#!/usr/bin/env python3
"""LAN-only shared SurvivalOps QA board. Python stdlib, SQLite, authenticated HTTPS."""
from __future__ import annotations

import base64
from collections import Counter, defaultdict
from datetime import datetime, timezone, timedelta
import hashlib
import hmac
from http import HTTPStatus
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import secrets
import sqlite3
import ssl
import threading
import time
from urllib.parse import urlsplit, parse_qs
from qa_protocol import Protocol
import autoqa_bridge
import inspector

HERE = Path(__file__).resolve().parent
TEMPLATE_AUDIT_PATH = Path.home()/'.local/share/survivalops-template-audit/report.json'


def read_template_audit(path=None):
    """Return only offline aggregated template metadata, never screenshot files."""
    target = path or TEMPLATE_AUDIT_PATH
    try:
        if not target.is_file() or target.stat().st_size > 2_000_000:
            return None
        data = json.loads(target.read_text(encoding='utf-8'))
        if (not isinstance(data,dict) or data.get('schema')!=1 or
                not isinstance(data.get('summary'),dict) or
                not isinstance(data.get('templates'),list) or
                len(data['templates'])<500):
            return None
        return data
    except (OSError, ValueError, UnicodeError):
        return None
STATUS_VALUES = {"not_tested", "testing", "working", "broken", "blocked", "verified"}
ISSUE_SEVERITIES = {"low", "medium", "high", "critical"}
MAX_BODY = 12000
KEY_PATTERN = re.compile(r"^[A-Z][A-Z0-9_]{1,79}$")
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
STATES = {
    "not_tested": "Nog niet getest", "testing": "In onderzoek",
    "working": "Lijkt te werken", "broken": "Werkt niet",
    "blocked": "Geblokkeerd", "verified": "Werkt — afgevinkt"
}


def iso_now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def connect(dbfile):
    db = sqlite3.connect(dbfile, timeout=10)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA foreign_keys=ON")
    db.execute("PRAGMA busy_timeout=10000")
    return db


def init_db(dbfile):
    dbfile.parent.mkdir(parents=True, exist_ok=True)
    with connect(dbfile) as db:
        db.executescript("""
            PRAGMA journal_mode=WAL;
            CREATE TABLE IF NOT EXISTS tasks(
              key TEXT PRIMARY KEY, code INTEGER NOT NULL, label TEXT NOT NULL,
              category TEXT NOT NULL, factory TEXT NOT NULL DEFAULT '',
              status TEXT NOT NULL DEFAULT 'not_tested',
              notes TEXT NOT NULL DEFAULT '', verified_by TEXT NOT NULL DEFAULT '',
              verified_at TEXT NOT NULL DEFAULT '', updated_at TEXT NOT NULL DEFAULT '',
              version INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS issues(
              id INTEGER PRIMARY KEY AUTOINCREMENT, task_key TEXT NOT NULL REFERENCES tasks(key),
              title TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '',
              severity TEXT NOT NULL DEFAULT 'medium', state TEXT NOT NULL DEFAULT 'open',
              created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
              created_by TEXT NOT NULL, closed_by TEXT NOT NULL DEFAULT ''
            );
            CREATE TABLE IF NOT EXISTS events(
              id INTEGER PRIMARY KEY AUTOINCREMENT, task_key TEXT NOT NULL REFERENCES tasks(key),
              kind TEXT NOT NULL, detail TEXT NOT NULL, actor TEXT NOT NULL,
              occurred_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS diagnostics(
              path TEXT PRIMARY KEY, task_key TEXT REFERENCES tasks(key),
              activity TEXT NOT NULL, kind TEXT NOT NULL, severity TEXT NOT NULL,
              timestamp TEXT NOT NULL, image_file TEXT NOT NULL DEFAULT ''
            );
            CREATE INDEX IF NOT EXISTS idx_diag_task ON diagnostics(task_key,timestamp);
            CREATE INDEX IF NOT EXISTS idx_issues_task ON issues(task_key,state);
            CREATE INDEX IF NOT EXISTS idx_events_task ON events(task_key,id);
        """)
    os.chmod(dbfile, 0o600)


def upsert_catalog(db, path):
    data = json.loads(Path(path).read_text())
    if not isinstance(data, list) or len(data) < 50:
        raise ValueError("Missing or incomplete task catalogue")
    with db:
        for t in data:
            if not KEY_PATTERN.fullmatch(t["key"]):
                raise ValueError("Invalid task key")
            db.execute("""
                INSERT INTO tasks(key,code,label,category,factory) VALUES(?,?,?,?,?)
                ON CONFLICT(key) DO UPDATE SET code=excluded.code,
                  label=excluded.label, category=excluded.category,
                  factory=excluded.factory
            """, (t["key"], t["code"], t["label"], t["category"], t.get("factory") or ""))


def scrub(text, maxlen=280):
    return re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "", str(text or "")).strip()[:maxlen]


def alias(s):
    return re.sub(r"[^a-z0-9]", "", str(s or "").lower())


def associate_activity(activity, tasks):
    value = alias(activity)
    if len(value) < 5 or value in {"recognition", "navigation", "stamina", "taskqueue", "general"}:
        return None
    exact = {}
    for t in tasks:
        key = t["key"]
        names = [alias(key), alias(t["factory"]), alias(t["factory"].removesuffix("Routine")),
                 alias(t["label"])]
        for name in names:
            if len(name) >= 5:
                exact[name] = key
    special = {"storehousechest": "STOREHOUSE_CHEST", "hero": "HERO_RECRUITMENT",
               "new-survivors": "CITY_SURVIVORS", "training": "TRAINING_TROOPS",
               "intelligence": "INTEL", "mail": "MAIL_REWARDS", "daily": "DAILY_MISSIONS",
               "tundratruck": "EVENT_TUNDRA_TRUCK", "gather": "GATHER_RESOURCES",
               "petadventure": "PET_ADVENTURE"}
    if value in exact:
        return exact[value]
    return {alias(k): v for k, v in special.items()}.get(value)


def diagnostic_severity(kind):
    s = kind.lower()
    if any(x in s for x in ("error", "failed", "unconfirmed", "unknown", "timeout", "crash")):
        return "error"
    if any(x in s for x in ("missing", "not-found", "unrecognized", "not-in", "uncertain")):
        return "warning"
    return "notice"


class Board:
    def __init__(self, dbpath, catalog_path, diagnostics_dir, auth_path):
        self.dbpath = Path(dbpath)
        self.diagnostics_dir = Path(diagnostics_dir)
        self.auth_path = Path(auth_path)
        self._lock = threading.Lock()
        self._last_sync = 0.
        self._failures = defaultdict(list)
        self.auth = json.loads(self.auth_path.read_text())
        self.secret = base64.urlsafe_b64decode(self.auth["session_key"])
        init_db(self.dbpath)
        self.protocol=Protocol(self.dbpath)
        self.protocol.install()
        with connect(self.dbpath) as db:
            upsert_catalog(db, catalog_path)
        self.sync_diagnostics(force=True)

    def password_ok(self, password):
        if not isinstance(password, str) or len(password) > 200:
            return False
        salt = bytes.fromhex(self.auth["salt"])
        actual = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 260000)
        return hmac.compare_digest(actual.hex(), self.auth["password_hash"])

    def check_limit(self, ip):
        with self._lock:
            self._failures[ip] = [t for t in self._failures[ip] if time.time()-t < 300]
            return len(self._failures[ip]) < 7

    def failed_login(self, ip):
        with self._lock:
            self._failures[ip].append(time.time())

    def make_cookie(self):
        stamp = str(int(time.time()))
        payload = base64.urlsafe_b64encode((stamp+":"+secrets.token_urlsafe(18)).encode()).decode().rstrip("=")
        sig = hmac.new(self.secret, payload.encode(), hashlib.sha256).hexdigest()
        return payload+"."+sig

    def valid_cookie(self, raw):
        try:
            payload, signature = raw.rsplit(".", 1)
            expected = hmac.new(self.secret, payload.encode(), hashlib.sha256).hexdigest()
            if not hmac.compare_digest(signature, expected):
                return False
            blob = base64.urlsafe_b64decode(payload + "="*((-len(payload))%4)).decode()
            epoch = int(blob.split(":", 1)[0])
            return 0 <= time.time()-epoch < 7*86400
        except (ValueError, IndexError, UnicodeError):
            return False

    def sync_diagnostics(self, force=False):
        now = time.monotonic()
        with self._lock:
            if not force and now-self._last_sync < 5:
                return 0
            self._last_sync = now
        if not self.diagnostics_dir.is_dir():
            return 0
        cutoff = (datetime.now(timezone.utc)-timedelta(days=14)).date().isoformat()
        inserted = 0
        with connect(self.dbpath) as db:
            tasks = [dict(x) for x in db.execute("SELECT key,label,factory FROM tasks")]
            seen = {r[0] for r in db.execute("SELECT path FROM diagnostics")}
            for day in sorted(self.diagnostics_dir.iterdir()):
                if not day.is_dir() or not DATE_RE.fullmatch(day.name) or day.name < cutoff:
                    continue
                for file in day.glob("*.json"):
                    rel = day.name+"/"+file.name
                    if rel in seen:
                        continue
                    try:
                        if file.stat().st_size > 150000:
                            continue
                        obj = json.loads(file.read_text(errors="replace"))
                        activity = scrub(obj.get("activity"), 80)
                        kind = scrub(obj.get("kind"), 80)
                        if not activity or not kind:
                            continue
                        img = scrub(obj.get("image_file"), 100)
                        if "/" in img or "\\" in img or not img.endswith(".png"):
                            img = ""
                        stamp = scrub(obj.get("timestamp"), 48) or day.name+"T00:00:00Z"
                        key = associate_activity(activity, tasks)
                        db.execute("""INSERT OR IGNORE INTO diagnostics
                         (path,task_key,activity,kind,severity,timestamp,image_file)
                         VALUES(?,?,?,?,?,?,?)""",
                         (rel,key,activity,kind,diagnostic_severity(kind),stamp,img))
                        inserted += 1
                    except (OSError, ValueError, TypeError, sqlite3.Error):
                        continue
        return inserted

    def summary(self):
        self.sync_diagnostics()
        with connect(self.dbpath) as db:
            tasks = [dict(t) for t in db.execute("SELECT * FROM tasks ORDER BY category,label")]
            issues = {r["task_key"]: dict(r) for r in db.execute("""
                SELECT task_key,COUNT(*) AS total FROM issues WHERE state='open' GROUP BY task_key
            """)}
            diags = {r["task_key"]: dict(r) for r in db.execute("""
                SELECT task_key,COUNT(*) AS total,MAX(timestamp) AS last_at
                FROM diagnostics WHERE task_key IS NOT NULL GROUP BY task_key
            """)}
            counts = Counter(x["status"] for x in tasks)
            unmapped = db.execute("SELECT COUNT(*) FROM diagnostics WHERE task_key IS NULL").fetchone()[0]
            for t in tasks:
                t["open_issues"] = issues.get(t["key"],{}).get("total",0)
                t["diagnostics"] = diags.get(t["key"],{}).get("total",0)
                t["last_diagnostic_at"] = diags.get(t["key"],{}).get("last_at","")
        return {"tasks":tasks,"counts":dict(counts),"unmapped_diagnostics":unmapped,
                "total":len(tasks),"updated_at":iso_now()}

    def system_diagnostics(self):
        self.sync_diagnostics()
        with connect(self.dbpath) as db:
            rows=[dict(x) for x in db.execute("""
                SELECT path,activity,kind,severity,timestamp,(image_file!='') AS has_image
                FROM diagnostics WHERE task_key IS NULL ORDER BY timestamp DESC LIMIT 75
            """)]
            counts={r["severity"]:r["total"] for r in db.execute("""
                SELECT severity,COUNT(*) AS total FROM diagnostics
                WHERE task_key IS NULL GROUP BY severity
            """)}
        return {"diagnostics":rows,"counts":counts}

    def live_diagnostics(self, task="all", severity="all", query="", offset=0, limit=40, hours=24):
        """Bounded, authenticated live feed. Never expose raw logs in list payloads."""
        self.sync_diagnostics()
        if task != "all" and task != "system" and not KEY_PATTERN.fullmatch(task):
            raise ValueError("Invalid task filter")
        if severity not in {"all", "error", "warning", "notice"}:
            raise ValueError("Invalid severity")
        if not 0 <= offset <= 3000 or not 1 <= limit <= 60 or len(query) > 100 or hours not in (0,24,72,168,336):
            raise ValueError("Invalid pagination or query")
        filters=[]
        args=[]
        if task == "system":
            filters.append("d.task_key IS NULL")
        elif task != "all":
            filters.append("d.task_key=?")
            args.append(task)
        if severity != "all":
            filters.append("d.severity=?")
            args.append(severity)
        if hours:
            filters.append("d.timestamp>=?")
            args.append((datetime.now(timezone.utc)-timedelta(hours=hours)).strftime("%Y-%m-%dT%H:%M:%S"))
        if query:
            filters.append("(instr(lower(d.activity),lower(?))>0 OR instr(lower(d.kind),lower(?))>0 OR instr(lower(t.label),lower(?))>0 OR instr(lower(d.task_key),lower(?))>0)")
            args.extend([query]*4)
        where=" WHERE "+" AND ".join(filters) if filters else ""
        with connect(self.dbpath) as db:
            count=db.execute("SELECT COUNT(*) FROM diagnostics d LEFT JOIN tasks t ON t.key=d.task_key"+where,args).fetchone()[0]
            rows=[dict(x) for x in db.execute("""
                SELECT d.path,d.task_key,t.label AS task_label,d.activity,d.kind,d.severity,
                  d.timestamp,(d.image_file!='') AS has_image
                FROM diagnostics d LEFT JOIN tasks t ON t.key=d.task_key
            """+where+" ORDER BY d.timestamp DESC, d.path DESC LIMIT ? OFFSET ?",args+[limit,offset])]
            latest=db.execute("SELECT MAX(timestamp) FROM diagnostics").fetchone()[0]
            global_count=db.execute("SELECT COUNT(*) FROM diagnostics").fetchone()[0]
        try:
            last=datetime.fromisoformat(latest.replace("Z","+00:00"))
            age=max(0,int((datetime.now(timezone.utc)-last).total_seconds()))
        except (ValueError, AttributeError):age=None
        return {"items":rows,"total":count,"global_total":global_count,
                "latest_at":latest,"seconds_since_latest":age,"poll_seconds":8,
                "server_time":iso_now(),"offset":offset,"limit":limit}

    def diagnostic_detail(self, relative):
        """Metadata and redacted log tail for a known indexed diagnostic only."""
        if len(relative)>180 or not re.fullmatch(r"\d{4}-\d{2}-\d{2}/[A-Za-z0-9_-]+\.json",relative):
            return None
        with connect(self.dbpath) as db:
            row=db.execute("""SELECT d.path,d.task_key,t.label AS task_label,
                   d.activity,d.kind,d.severity,d.timestamp,d.image_file
                   FROM diagnostics d LEFT JOIN tasks t ON t.key=d.task_key WHERE d.path=?""",
                   (relative,)).fetchone()
        if row is None:return None
        file=self.diagnostics_dir/relative
        try:
            if not file.is_file() or file.stat().st_size>150000:return None
            obj=json.loads(file.read_text(errors="replace"))
        except (OSError,ValueError):return None
        raw=obj.get("log_tail","")
        if not isinstance(raw,str):raw=""
        # Redact access material even from otherwise trusted local diagnostic uploads.
        raw=raw[-20000:]
        raw=re.sub(r"(?i)(\b(?:bearer|token|password|secret|api[_-]?key)\s*[:=]\s*)([\w./+~=-]{8,})",
                   r"\1[REDACTED]",raw)
        raw=re.sub(r"(?i)(Authorization\s*:\s*Bearer\s+)[^\s]+",r"\1[REDACTED]",raw)
        lines=[]
        for line in raw.splitlines()[-75:]:
            m=re.search(r"\b(ERROR|WARN|INFO|DEBUG|TRACE)\b",line)
            lines.append({"level":m.group(1) if m else "LOG","text":scrub(line,1100)})
        d=dict(row)
        d["has_image"]=bool(d.pop("image_file"))
        d["platform"]=scrub(obj.get("platform",""),80)
        d["log_lines"]=lines
        d["debug_available"]=bool(lines)
        return d

    def detail(self, key):
        self.sync_diagnostics()
        with connect(self.dbpath) as db:
            row = db.execute("SELECT * FROM tasks WHERE key=?", (key,)).fetchone()
            if row is None: return None
            return {"task":dict(row),
                    "issues":[dict(x) for x in db.execute("SELECT * FROM issues WHERE task_key=? ORDER BY state,id DESC", (key,))],
                    "diagnostics":[dict(x) for x in db.execute("""
                       SELECT path,activity,kind,severity,timestamp,(image_file!='') AS has_image
                       FROM diagnostics WHERE task_key=? ORDER BY timestamp DESC LIMIT 30
                    """,(key,))],
                    "events":[dict(x) for x in db.execute("""
                        SELECT * FROM events WHERE task_key=? ORDER BY id DESC LIMIT 30
                    """,(key,))],
                    "qa":self.protocol.snapshot(key)}

    def edit_task(self, key, payload, actor):
        allowed = {"status", "notes", "version"}
        if set(payload)-allowed or "version" not in payload or type(payload["version"]) is not int:
            return "invalid", None
        if "status" in payload and payload["status"] not in STATUS_VALUES:
            return "invalid", None
        if "notes" in payload and (not isinstance(payload["notes"], str) or len(payload["notes"]) > 5000):
            return "invalid", None
        with connect(self.dbpath) as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT * FROM tasks WHERE key=?", (key,)).fetchone()
            if row is None: return "missing", None
            if row["version"] != payload["version"]: return "conflict", dict(row)
            state = payload.get("status", row["status"])
            if state == "verified" and row["status"] != "verified":
                return "needs_joint_qa",dict(row)
            notes = scrub(payload.get("notes", row["notes"]), 5000)
            verified_by = (actor if state == "verified" and row["status"] != "verified"
                           else (row["verified_by"] if state == "verified" else ""))
            verified_at = (iso_now() if state == "verified" and row["status"] != "verified"
                           else (row["verified_at"] if state == "verified" else ""))
            if state == row["status"] and notes == row["notes"]:
                return "ok", dict(row)
            now = iso_now()
            db.execute("""UPDATE tasks SET status=?,notes=?,verified_by=?,verified_at=?,
                       updated_at=?,version=version+1 WHERE key=?""",
                       (state,notes,verified_by,verified_at,now,key))
            changed = {"status": [row["status"],state]} if state != row["status"] else {}
            if notes != row["notes"]:
                changed["notes_changed"] = True
            db.execute("""INSERT INTO events(task_key,kind,detail,actor,occurred_at)
                       VALUES(?,?,?,?,?)""",
                       (key,"task_update",json.dumps(changed,ensure_ascii=False),actor,now))
            return "ok", dict(db.execute("SELECT * FROM tasks WHERE key=?", (key,)).fetchone())

    def add_issue(self, key, payload, actor):
        if set(payload)-{"title","detail","severity"}:
            return None
        title = payload.get("title")
        detail = payload.get("detail", "")
        severity = payload.get("severity", "medium")
        if not isinstance(title,str) or not 3<=len(title.strip())<=160 or not isinstance(detail,str) or len(detail)>3000 or severity not in ISSUE_SEVERITIES:
            return None
        with connect(self.dbpath) as db:
            if not db.execute("SELECT 1 FROM tasks WHERE key=?", (key,)).fetchone():
                return None
            now = iso_now()
            cur = db.execute("""INSERT INTO issues(task_key,title,detail,severity,state,created_at,updated_at,created_by)
               VALUES(?,?,?,?,'open',?,?,?)""",(key,scrub(title,160),scrub(detail,3000),severity,now,now,actor))
            db.execute("INSERT INTO events(task_key,kind,detail,actor,occurred_at) VALUES(?,?,?,?,?)",
                       (key,"issue_created",str(cur.lastrowid)+" "+scrub(title,160),actor,now))
            # A newly discovered regression invalidates an in-flight joint
            # approval and a previously VERIFIED status, but preserves history.
            current=self.protocol.latest_row(db,key)
            if current is not None and not current['completed_at']:
                db.execute("""UPDATE qa_rounds SET operator_signed_at='',operator_note='',
                            reviewer_signed_at='',reviewer_note='' WHERE id=?""",(current['id'],))
                db.execute("""UPDATE qa_checks SET result='fail',note=?,actor=?,updated_at=?
                            WHERE round_id=? AND step='postcondition'""",
                           ('New open issue #'+str(cur.lastrowid)+': '+scrub(title,160),'System',now,current['id']))
            current_status=db.execute('SELECT status FROM tasks WHERE key=?',(key,)).fetchone()[0]
            if current_status in ('verified','working') or (current is not None and not current['completed_at']):
                db.execute("""UPDATE tasks SET status='broken',verified_by='',verified_at='',
                            updated_at=?,version=version+1 WHERE key=?""",(now,key))
            return cur.lastrowid

    def edit_issue(self, issue_id, payload, actor):
        if set(payload) != {"state"} or payload["state"] not in {"open","closed"}:
            return False
        with connect(self.dbpath) as db:
            row = db.execute("SELECT * FROM issues WHERE id=?", (issue_id,)).fetchone()
            if not row:return False
            state=payload["state"]
            now=iso_now()
            if row["state"] != state:
                db.execute("UPDATE issues SET state=?,updated_at=?,closed_by=? WHERE id=?",
                           (state,now, actor if state=="closed" else "",issue_id))
                db.execute("INSERT INTO events(task_key,kind,detail,actor,occurred_at) VALUES(?,?,?,?,?)",
                           (row["task_key"],"issue_"+state,str(issue_id),actor,now))
        return True

    def diagnostic_image(self, relative):
        with connect(self.dbpath) as db:
            row = db.execute("SELECT image_file FROM diagnostics WHERE path=?", (relative,)).fetchone()
            if row is None or not row["image_file"]:return None
            day = relative.split("/",1)[0]
            if not DATE_RE.fullmatch(day):return None
            path = self.diagnostics_dir/day/row["image_file"]
            try:
                if not path.is_file() or path.stat().st_size>3_000_000:return None
                result=path.read_bytes()
                if result[:8]!=b"\x89PNG\r\n\x1a\n":return None
                return result
            except OSError:
                return None


class Handler(BaseHTTPRequestHandler):
    server_version = "SurvivalOpsQA/1"
    def log_message(self, fmt, *args):
        # Keep raw query and login attempts out of logs.
        if self.path.split("?",1)[0] in ("/api/login","/api/session"):
            return
        super().log_message(fmt,*args)

    @property
    def board(self):
        return self.server.board

    def reply(self, status, payload, headers=None):
        data = json.dumps(payload,ensure_ascii=False,separators=(",",":")).encode()
        self.raw_reply(status,data,"application/json; charset=utf-8",headers)

    def raw_reply(self,status,data,mimetype,headers=None):
        self.send_response(status)
        self.send_header("Content-Type",mimetype)
        self.send_header("Content-Length",str(len(data)))
        self.send_header("Cache-Control","no-store")
        self.send_header("X-Content-Type-Options","nosniff")
        self.send_header("X-Frame-Options","DENY")
        self.send_header("Referrer-Policy","no-referrer")
        self.send_header("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'")
        for k,v in (headers or {}).items():self.send_header(k,v)
        self.end_headers()
        try:self.wfile.write(data)
        except (BrokenPipeError,ConnectionResetError): pass

    def authenticated(self):
        cookie = SimpleCookie()
        try:cookie.load(self.headers.get("Cookie",""))
        except Exception:return False
        return "qa_session" in cookie and self.board.valid_cookie(cookie["qa_session"].value)

    def require_auth(self):
        if not self.authenticated():
            self.reply(401,{"error":"login_required"})
            return False
        return True

    def require_same_origin(self):
        # Browser forms, and especially cookie-authenticated API writes, cannot
        # be initiated by other LAN websites (CSRF and DNS-rebinding hardening).
        origin=self.headers.get("Origin")
        host=self.headers.get("Host","")
        if host not in {"10.0.0.100:9097","localhost:9097","127.0.0.1:9097"}:
            self.reply(403,{"error":"invalid_host"})
            return False
        if origin and origin not in {"https://10.0.0.100:9097","https://localhost:9097","https://127.0.0.1:9097"}:
            self.reply(403,{"error":"invalid_origin"})
            return False
        return True

    def json_body(self):
        try: size=int(self.headers.get("Content-Length","-1"))
        except ValueError:raise ValueError("Invalid content length")
        if size<2 or size>MAX_BODY or "application/json" not in self.headers.get("Content-Type",""):
            raise ValueError("Invalid JSON content type or size")
        payload=json.loads(self.rfile.read(size))
        if not isinstance(payload,dict):raise ValueError("Object required")
        return payload

    def do_GET(self):
        path=urlsplit(self.path).path
        if path=="/healthz":
            return self.reply(200,{"ok":True,"service":"SurvivalOps QA"})
        if path in ("/","/index.html","/app.js","/live.js","/style.css","/live.css","/templates.js","/templates.css","/qa.css","/autoqa.js","/autoqa.css","/inspector.js","/inspector.css"):
            name = "index.html" if path=="/" else path.lstrip("/")
            target=HERE/"static"/name
            if name not in {"index.html","app.js","live.js","style.css","live.css","templates.js","templates.css","qa.css","autoqa.js","autoqa.css","inspector.js","inspector.css"} or not target.is_file():
                return self.reply(404,{"error":"not_found"})
            mime= {"index.html":"text/html; charset=utf-8","app.js":"text/javascript; charset=utf-8","live.js":"text/javascript; charset=utf-8",
                   "style.css":"text/css; charset=utf-8","live.css":"text/css; charset=utf-8","templates.js":"text/javascript; charset=utf-8","templates.css":"text/css; charset=utf-8","qa.css":"text/css; charset=utf-8","autoqa.js":"text/javascript; charset=utf-8","autoqa.css":"text/css; charset=utf-8","inspector.js":"text/javascript; charset=utf-8","inspector.css":"text/css; charset=utf-8"}[name]
            return self.raw_reply(200,target.read_bytes(),mime)
        if not self.require_auth():return
        if path=="/api/session":return self.reply(200,{"authenticated":True})
        if path=="/api/autoqa":return self.reply(200,autoqa_bridge.status())
        if path=="/api/autoqa/audit":
            report=autoqa_bridge.audit()
            return self.reply(200,report) if report else self.reply(503,{"error":"audit_not_ready"})
        if path=="/api/autoqa/detail":
            query=parse_qs(urlsplit(self.path).query)
            result=autoqa_bridge.detail(query.get("id",[""])[0])
            return self.reply(200,result) if result else self.reply(404,{"error":"run_not_found"})
        if path=="/api/autoqa/image":
            query=parse_qs(urlsplit(self.path).query)
            img=autoqa_bridge.image(query.get("id",[""])[0],query.get("name",[""])[0])
            return self.raw_reply(200,img,"image/png") if img else self.reply(404,{"error":"screenshot_not_found"})
        if path=="/api/inspector/sessions":
            query=parse_qs(urlsplit(self.path).query)
            return self.reply(200,{"sessions":inspector.sessions(query.get('task',[None])[0])})
        if path=="/api/inspector/run":
            query=parse_qs(urlsplit(self.path).query)
            result=inspector.run_detail(query.get('id',[''])[0])
            return self.reply(200,result) if result else self.reply(404,{"error":"run_not_found"})
        if path=="/api/inspector/run-image":
            query=parse_qs(urlsplit(self.path).query)
            image=inspector.coach_image(query.get('id',[''])[0],query.get('file',[''])[0])
            return self.raw_reply(200,image,'image/jpeg') if image else self.reply(404,{"error":"capture_not_found"})
        if path=="/api/inspector/extras":return self.reply(200,{"files":inspector.unregistered_assets()})
        if path=="/api/inspector/extra-image":
            query=parse_qs(urlsplit(self.path).query)
            img=inspector.extra_png(query.get('path',[''])[0])
            return self.raw_reply(200,img,'image/png') if img else self.reply(404,{"error":"asset_not_found"})
        if path=="/api/inspector/template-image":
            query=parse_qs(urlsplit(self.path).query)
            image=inspector.template_png(query.get('name',[''])[0])
            return self.raw_reply(200,image,'image/png') if image else self.reply(404,{"error":"template_not_found"})
        if path=="/api/inspector/review":
            return self.reply(200,{"choices":inspector.review(),"runtime_unchanged":True})
        if path=="/api/inspector/flow":
            query=parse_qs(urlsplit(self.path).query)
            result=inspector.java_flow(query.get('task',[''])[0])
            return self.reply(200,result) if result else self.reply(404,{"error":"unknown_task"})
        if path=="/api/inspector/ocr":return self.reply(200,inspector.ocr_health())
        if path=="/api/template-audit":
            audit=read_template_audit()
            return self.reply(200,audit) if audit else self.reply(503,{"error":"audit_not_ready"})
        if path=="/api/tasks":
            return self.reply(200,self.board.summary())
        if path=="/api/overview":
            return self.reply(200,self.board.summary())
        if path=="/api/system":
            return self.reply(200,self.board.system_diagnostics())
        if path=="/api/diagnostics":
            query=parse_qs(urlsplit(self.path).query)
            try:
                def param(name,default):return query.get(name,[default])[0]
                result=self.board.live_diagnostics(
                    task=param("task","all"),severity=param("severity","all"),
                    query=param("q",""),offset=int(param("offset","0")),
                    limit=int(param("limit","40")), hours=int(param("hours","24")))
            except (ValueError,TypeError):return self.reply(400,{"error":"invalid_filters"})
            return self.reply(200,result)
        if path=="/api/diagnostics/detail":
            query=parse_qs(urlsplit(self.path).query)
            result=self.board.diagnostic_detail(query.get("path",[""])[0])
            return self.reply(200,result) if result else self.reply(404,{"error":"diagnostic_missing"})
        if path.startswith("/api/tasks/") and path.endswith("/detail"):
            key=path.split("/")[3]
            if not KEY_PATTERN.fullmatch(key):return self.reply(400,{"error":"invalid_key"})
            result=self.board.detail(key)
            return self.reply(200,result) if result else self.reply(404,{"error":"missing_task"})
        if path.startswith("/api/diagnostic/image"):
            query=parse_qs(urlsplit(self.path).query)
            relative=query.get("path",[""])[0]
            img=self.board.diagnostic_image(relative)
            return self.raw_reply(200,img,"image/png") if img else self.reply(404,{"error":"image_missing"})
        return self.reply(404,{"error":"not_found"})

    def do_POST(self):
        if not self.require_same_origin():return
        path=urlsplit(self.path).path
        try:payload=self.json_body()
        except (ValueError,UnicodeError) as exc:
            return self.reply(400,{"error":"bad_request"})
        if path=="/api/login":
            ip=self.client_address[0]
            if not self.board.check_limit(ip):
                return self.reply(429,{"error":"too_many_attempts"})
            if set(payload)!={"password"} or not self.board.password_ok(payload.get("password")):
                self.board.failed_login(ip)
                return self.reply(401,{"error":"invalid_password"})
            return self.reply(200,{"ok":True},{
                "Set-Cookie":"qa_session="+self.board.make_cookie()+"; Path=/; Max-Age=604800; HttpOnly; Secure; SameSite=Strict"})
        if not self.require_auth():return
        if path=="/api/logout":
            return self.reply(200,{"ok":True},{"Set-Cookie":"qa_session=; Max-Age=0; Path=/; HttpOnly; Secure; SameSite=Strict"})
        if path=="/api/sync":
            return self.reply(200,{"imported":self.board.sync_diagnostics(force=True)})
        if path=="/api/inspector/review":
            if set(payload)!={'name','action'} or not isinstance(payload['name'],str) or not isinstance(payload['action'],str):
                return self.reply(400,{"error":"name_action_required"})
            try:return self.reply(200,inspector.review_update(payload['name'],payload['action']))
            except ValueError as exc:return self.reply(400,{"error":str(exc)[:100]})
        if path=="/api/autoqa/pause":
            if set(payload)!={'paused'} or type(payload.get('paused')) is not bool:
                return self.reply(400,{'error':'boolean_paused_required'})
            return self.reply(200,autoqa_bridge.set_paused(payload['paused']))
        if path=="/api/autoqa/enqueue":
            if not set(payload).issubset({'task','mode'}) or 'task' not in payload or not isinstance(payload.get("task"),str):
                return self.reply(400,{"error":"task_required"})
            try:return self.reply(202,autoqa_bridge.enqueue(payload['task'],payload.get('mode','observe')))
            except (ValueError,RuntimeError) as exc:
                return self.reply(400,{"error":str(exc)[:140]})
        match=re.fullmatch(r"/api/tasks/([A-Z][A-Z0-9_]{1,79})/qa/(start|attach|check|operator-note|operator-sign)",path)
        if match:
            key,action=match.groups()
            if action=='start':state,data=self.board.protocol.begin(key,payload.get('build'),'Operator')
            elif action=='attach':state,data=self.board.protocol.attach(key,payload.get('coach_run'),'Operator')
            elif action=='check':state,data=self.board.protocol.check(key,payload.get('step'),payload.get('result'),payload.get('note'),'Operator')
            elif action=='operator-note':state,data=self.board.protocol.save_operator_note(key,payload.get('note'),'Operator')
            else:state,data=self.board.protocol.sign(key,'operator',payload.get('note'))
            return self.reply(200,{"result":state,"qa":data}) if state=='ok' else self.reply(400,{"error":state})
        if path.startswith("/api/tasks/") and path.endswith("/issues"):
            key=path.split("/")[3]
            if not KEY_PATTERN.fullmatch(key):return self.reply(400,{"error":"invalid_key"})
            issue=self.board.add_issue(key,payload,"Operator")
            return self.reply(201,{"id":issue}) if issue else self.reply(400,{"error":"invalid_issue"})
        return self.reply(404,{"error":"not_found"})

    def do_PATCH(self):
        if not self.require_same_origin() or not self.require_auth():return
        path=urlsplit(self.path).path
        try:payload=self.json_body()
        except (ValueError,UnicodeError):return self.reply(400,{"error":"bad_request"})
        if path.startswith("/api/tasks/") and not path.endswith("/detail"):
            key=path.split("/")[3]
            if not KEY_PATTERN.fullmatch(key):return self.reply(400,{"error":"invalid_key"})
            state, result=self.board.edit_task(key,payload,"Operator")
            status={"ok":200,"conflict":409,"missing":404,"invalid":400,"needs_joint_qa":400}[state]
            return self.reply(status,{"result":state,"task":result})
        if re.fullmatch(r"/api/issues/\d{1,12}",path):
            num=int(path.rsplit("/",1)[1])
            if self.board.edit_issue(num,payload,"Operator"):
                return self.reply(200,{"ok":True})
            return self.reply(400,{"error":"invalid_issue_or_state"})
        return self.reply(404,{"error":"not_found"})


def run_server():
    import argparse
    p=argparse.ArgumentParser()
    p.add_argument("--host",default="10.0.0.100")
    p.add_argument("--port",type=int,default=9097)
    p.add_argument("--db",type=Path,default=Path.home()/".local/share/survivalops-qa/dashboard.sqlite3")
    p.add_argument("--catalog",type=Path,default=HERE/"catalog.json")
    p.add_argument("--diagnostics",type=Path,default=Path.home()/"survivalops-diagnostics/inbox")
    p.add_argument("--auth",type=Path,default=Path.home()/".config/survivalops-qa-dashboard/auth.json")
    p.add_argument("--cert",type=Path,default=Path.home()/"survivalops-diagnostics/private/server-cert.pem")
    p.add_argument("--key",type=Path,default=Path.home()/"survivalops-diagnostics/private/server-key.pem")
    args=p.parse_args()
    board=Board(args.db,args.catalog,args.diagnostics,args.auth)
    httpd=ThreadingHTTPServer((args.host,args.port),Handler)
    httpd.board=board
    ctx=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.minimum_version=ssl.TLSVersion.TLSv1_2
    ctx.load_cert_chain(args.cert,args.key)
    httpd.socket=ctx.wrap_socket(httpd.socket,server_side=True)
    print(f"SurvivalOps QA HTTPS on {args.host}:{args.port}; tasks={board.summary()['total']}",flush=True)
    httpd.serve_forever()

if __name__=="__main__":
    run_server()
