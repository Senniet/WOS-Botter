#!/usr/bin/env python3
"""LAN-only HTTPS frontend for the localhost-only Java SurvivalOps engine.

This process never implements game automation: all routines, scheduling and
recognition remain owned by the original Java engine. ADB read-only captures
and optional manual controls are restricted to configured Serve-Emu endpoints.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import http.client
import json
import re
import sqlite3
from pathlib import Path
import ssl
import subprocess
import threading
import time
from urllib.parse import urlparse, parse_qs
from emulator_live import BOUNDARY, CLIENT_LIMIT, live_jpegs
from timeline import TimelineArchive, snapshot, start_monitor, valid_profile
import run_limit

ROOT = Path(__file__).resolve().parent
TLS_DIR = Path.home() / ".config/survivalops-web"
ENGINE_ADDR = ("127.0.0.1", 18765)
ADB = Path.home() / ".local/share/WsScrcpyWeb/dependencies/adb/adb"
MAX_BODY = 64 * 1024
MAX_FRAME = 10 * 1024 * 1024
QA_DB = Path.home() / ".local/share/survivalops-qa/dashboard.sqlite3"
DIAG_DIR = Path.home() / "survivalops-diagnostics/inbox"
DIAG_PATTERN = re.compile(r"\d{4}-\d{2}-\d{2}/[A-Za-z0-9_-]+\.json")
TIMELINE = TimelineArchive()


def engine(path: str, method="GET", body: bytes | None = None) -> tuple[int, bytes]:
    conn = http.client.HTTPConnection(*ENGINE_ADDR, timeout=12)
    try:
        conn.request(method, path, body=body,
                     headers={"Content-Type": "application/json"})
        response = conn.getresponse()
        raw = response.read(2 * 1024 * 1024 + 1)
        if len(raw) > 2 * 1024 * 1024:
            raise ValueError("Engine response too large")
        return response.status, raw
    finally:
        conn.close()


def emulator_endpoint() -> str:
    status, raw = engine("/api/emulator")
    if status != 200:
        raise RuntimeError("Serve-Emu is not configured")
    data = json.loads(raw)
    endpoints = data.get("endpoints", "")
    if not data.get("selected") or not endpoints:
        raise RuntimeError("Configure Serve-Emu in Settings first")
    # Only the first configured device is exposed in the single emulator view.
    endpoint = endpoints.split(",")[0].strip()
    if not endpoint or len(endpoint) > 260:
        raise RuntimeError("Invalid configured ADB endpoint")
    return endpoint


def adb(*args: str, timeout: int = 10, limit: int = MAX_FRAME) -> bytes:
    if not ADB.is_file():
        raise RuntimeError("Android platform tools are not available")
    command = [str(ADB), *args]
    completed = subprocess.run(command, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, timeout=timeout, check=False)
    if completed.returncode != 0:
        raise RuntimeError("ADB command failed or device unavailable")
    if len(completed.stdout) > limit:
        raise RuntimeError("ADB response too large")
    return completed.stdout


def qa_connection():
    conn = sqlite3.connect(f"file:{QA_DB}?mode=ro", uri=True, timeout=8)
    conn.row_factory = sqlite3.Row
    return conn


def qa_summary() -> dict:
    with qa_connection() as db:
        counts = {row["status"]: row["total"] for row in db.execute(
            "SELECT status,COUNT(*) AS total FROM tasks GROUP BY status")}
        issues = [dict(row) for row in db.execute(
            "SELECT i.id,i.task_key,t.label,i.title,i.severity,i.created_at "
            "FROM issues i LEFT JOIN tasks t ON t.key=i.task_key "
            "WHERE i.state='open' ORDER BY i.created_at DESC LIMIT 25")]
        diagnostics = [dict(row) for row in db.execute(
            "SELECT d.path,d.task_key,t.label AS task_label,d.activity,d.kind,"
            "d.severity,d.timestamp,(d.image_file!='') AS has_image "
            "FROM diagnostics d LEFT JOIN tasks t ON t.key=d.task_key "
            "ORDER BY d.timestamp DESC LIMIT 36")]
        total = db.execute("SELECT COUNT(*) FROM tasks").fetchone()[0]
        diag_total = db.execute("SELECT COUNT(*) FROM diagnostics").fetchone()[0]
    return {"total_tasks": total, "status_counts": counts,
            "open_issues": issues, "diagnostics": diagnostics,
            "diagnostic_total": diag_total}


def qa_indexed_record(path: str):
    if not DIAG_PATTERN.fullmatch(path) or len(path) > 180:
        return None
    with qa_connection() as db:
        row = db.execute("SELECT path,task_key,activity,kind,severity,timestamp,image_file "
                         "FROM diagnostics WHERE path=?", (path,)).fetchone()
        return dict(row) if row else None


def qa_details(path: str):
    row = qa_indexed_record(path)
    if row is None:
        return None
    filepath = DIAG_DIR / path
    if not filepath.is_file() or filepath.stat().st_size > 150000:
        return None
    data = json.loads(filepath.read_text(errors="replace"))
    tail = str(data.get("log_tail", ""))[-16000:]
    tail = re.sub(r"(?i)(\b(?:bearer|token|password|secret|api[_-]?key)\s*[:=]\s*)([^\s]+)",
                  r"\1[REDACTED]", tail)
    row["log_lines"] = tail.splitlines()[-65:]
    row["has_image"] = bool(row.pop("image_file"))
    return row


def qa_image(path: str):
    row = qa_indexed_record(path)
    if not row or not row["image_file"]:
        return None
    name = row["image_file"]
    if not re.fullmatch(r"[A-Za-z0-9_-]+\.png", name):
        return None
    filepath = DIAG_DIR / path.split("/", 1)[0] / name
    if not filepath.is_file() or filepath.stat().st_size > 3_000_000:
        return None
    payload = filepath.read_bytes()
    return payload if payload.startswith(b"\x89PNG\r\n\x1a\n") else None


def other_controller_recent(seconds: int = 60) -> bool:
    """Conservative interlock while the existing MSI/Task Coach uploads evidence.

    The web edition shares the same TrueNAS emulator with the old MSI. This is
    only an observer heuristic, not a replacement for an exclusive ADB lease.
    """
    now = datetime.now(timezone.utc)
    try:
        with qa_connection() as db:
            row = db.execute("SELECT MAX(timestamp) FROM diagnostics").fetchone()
        last = row[0] if row else None
        if last:
            seen = datetime.fromisoformat(last.replace("Z", "+00:00"))
            if seen.tzinfo is None: seen = seen.replace(tzinfo=timezone.utc)
            if (now-seen).total_seconds() < seconds:
                return True
    except (OSError, sqlite3.Error, ValueError):
        pass
    # The external QA index may lag the uploader. Check the current day's files.
    for day in (now.date(), (now-timedelta(days=1)).date()):
        folder = DIAG_DIR / day.isoformat()
        try:
            for item in folder.glob("*.json"):
                if time.time() - item.stat().st_mtime < seconds:
                    return True
        except OSError:
            continue
    return False


def manual_input_allowed(engine_state: dict) -> bool:
    """Only accept input when the scheduler cannot launch a competing task."""
    if engine_state.get("executing"):
        return False
    queues = engine_state.get("queues")
    if isinstance(queues, list) and any(isinstance(q, dict) and q.get("executing")
                                        for q in queues):
        return False
    if not engine_state.get("running"):
        return True
    return (isinstance(queues, list) and bool(queues)
            and all(isinstance(q, dict) and q.get("paused") is True
                    for q in queues))


class Handler(BaseHTTPRequestHandler):
    server_version = "SurvivalOpsWeb/1"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        # Do not log request bodies or screenshots.
        print(f"{self.address_string()} {fmt % args}", flush=True)

    def send(self, code: int, content: bytes, kind="application/json",
             extra: dict[str, str] | None = None):
        self.send_response(code)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(content)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Content-Security-Policy",
                         "default-src 'self'; img-src 'self' blob: data:; "
                         "style-src 'self'; script-src 'self'; connect-src 'self'; "
                         "frame-src https://192.168.1.20:8448; base-uri 'none'; "
                         "form-action 'self'; object-src 'none'")
        if extra:
            for k, v in extra.items():
                self.send_header(k, v)
        self.end_headers()
        try:
            self.wfile.write(content)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def json(self, code: int, obj: dict):
        self.send(code, json.dumps(obj).encode("utf-8"))

    def read_json(self) -> dict:
        try:
            length = int(self.headers.get("Content-Length", "-1"))
            if length < 0 or length > MAX_BODY:
                raise ValueError("Invalid request size")
            content = self.rfile.read(length)
            obj = json.loads(content)
            if not isinstance(obj, dict):
                raise ValueError("Object expected")
            return obj
        except (ValueError, json.JSONDecodeError):
            raise ValueError("Invalid JSON body")

    def is_safe_origin(self) -> bool:
        origin = self.headers.get("Origin", "")
        host = self.headers.get("Host", "")
        return (host in ("10.0.0.100:9098", "192.168.1.20:9098", "localhost:9098", "127.0.0.1:9098")
                and (not origin or origin == f"https://{host}"))

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        if path == "/health":
            self.json(200, {"web": True})
            return
        if path == "/":
            path = "/index.html"
        if path in ("/api/session", "/login", "/api/logout", "/api/change-password"):
            self.json(404, {"error": "Not found"})
            return
        if path not in ("/index.html", "/static/app.js", "/static/style.css",
                        "/static/operations.css", "/static/theme-overrides.css",
                        "/static/emulator-mobile.js", "/static/emulator-mobile.css") and not path.startswith("/api/"):
            self.json(404, {"error": "Not found"})
            return
        if path == "/index.html":
            self.send(200, (ROOT / "index.html").read_bytes(), "text/html; charset=utf-8")
        elif path.startswith("/static/"):
            filename = ROOT / path.lstrip("/")
            if not filename.is_file():
                self.json(404, {"error": "Not found"})
            else:
                kind = "text/javascript; charset=utf-8" if path.endswith(".js") else "text/css; charset=utf-8"
                self.send(200, filename.read_bytes(), kind)
        elif path == "/api/qa/summary":
            try:
                self.json(200, qa_summary())
            except (sqlite3.Error, OSError):
                self.json(503, {"error": "QA dashboard archive unavailable"})
        elif path in ("/api/qa/detail", "/api/qa/image"):
            relative = parse_qs(parsed.query).get("path", [""])[0]
            try:
                if path == "/api/qa/detail":
                    data = qa_details(relative)
                    self.json(200, data) if data else self.json(404, {"error": "Diagnostic not found"})
                else:
                    image = qa_image(relative)
                    self.send(200, image, "image/png") if image else self.json(404, {"error": "Image not found"})
            except (sqlite3.Error, OSError, ValueError):
                self.json(503, {"error": "Diagnostic unavailable"})
        elif path == "/api/emulator/frame":
            self.frame()
        elif path == "/api/emulator/live":
            self.live_stream()
        elif path == "/api/emulator/state":
            self.device_state()
        elif path == "/api/run-limit":
            self.json(200, run_limit.read())
        elif path == "/api/timeline":
            profile_id = parse_qs(parsed.query).get("profile", [""])[0]
            try:
                valid_profile(profile_id)
            except ValueError:
                self.json(400, {"error": "Invalid profile"})
                return
            try:
                status, raw = engine("/api/tasks?profile=" + profile_id)
                if status != 200:
                    self.json(503, {"error": "Task scheduler is unavailable"})
                    return
                state_status, state_raw = engine("/api/status")
                running = (state_status == 200 and bool(json.loads(state_raw).get("running")))
                self.json(200, snapshot(profile_id, json.loads(raw), TIMELINE,
                                        scheduler_running=running))
            except (OSError, ValueError, sqlite3.Error, RuntimeError):
                self.json(503, {"error": "Task timeline temporarily unavailable"})
        elif path == "/api/logs":
            self.logs()
        else:
            self.forward(path + ("?" + parsed.query if parsed.query else ""), "GET")

    def do_POST(self):
        parsed = urlparse(self.path)
        if not self.is_safe_origin():
            self.json(403, {"error": "Invalid origin"})
            return
        if parsed.path in ("/login", "/api/logout", "/api/change-password"):
            self.json(404, {"error": "Not found"})
            return
        if self.headers.get("X-SurvivalOps-Request") != "web":
            self.json(403, {"error": "Missing request token"})
            return
        if parsed.path == "/api/run-limit":
            try:
                self.json(200, run_limit.configure(self.read_json()))
            except ValueError as error:
                self.json(400, {"error": str(error)})
        elif parsed.path == "/api/emulator/input":
            self.emulator_input()
        elif parsed.path.startswith("/api/"):
            try:
                data = self.read_json()
                if parsed.path == "/api/engine" and data.get("action") == "start" \
                        and other_controller_recent():
                    self.json(409, {"error": "The MSI/Task Coach may already be controlling Serve-Emu. "
                                           "Only start the web bot once the other session has stopped."})
                    return
                body = json.dumps(data).encode("utf-8")
                self.forward(parsed.path, "POST", body)
            except ValueError as err:
                self.json(400, {"error": str(err)})
        else:
            self.json(404, {"error": "Not found"})

    def forward(self, path, method, body=None):
        try:
            status, raw = engine(path, method, body)
            self.send(status, raw)
        except (OSError, TimeoutError, RuntimeError) as error:
            self.json(503, {"error": "SurvivalOps engine offline"})

    def device_state(self):
        try:
            endpoint = emulator_endpoint()
            # Connecting is safe; no game input is generated.
            adb("connect", endpoint, timeout=7)
            state = adb("-s", endpoint, "get-state", timeout=6, limit=1024).decode().strip()
            self.json(200, {"connected": state == "device", "endpoint": endpoint,
                            "state": state, "externalControllerLikelyActive": other_controller_recent()})
        except (RuntimeError, subprocess.TimeoutExpired) as error:
            self.json(503, {"connected": False, "error": str(error)})

    def frame(self):
        try:
            endpoint = emulator_endpoint()
            png = adb("-s", endpoint, "exec-out", "screencap", "-p", timeout=12)
            if not png.startswith(b"\x89PNG\r\n\x1a\n"):
                raise RuntimeError("Invalid screenshot")
            self.send(200, png, "image/png")
        except (RuntimeError, subprocess.TimeoutExpired) as error:
            self.json(503, {"error": str(error)})

    def live_stream(self):
        """Send real *continuous* frames from the same scrcpy H.264 source.

        One GET holds a multipart stream open until navigation
        or disconnect. It does not poll ADB or refresh the <img> every N secs.
        Input is separately guarded through /api/emulator/input.
        """
        if not CLIENT_LIMIT.acquire(blocking=False):
            self.json(429, {"error": "Mobile live video is already in use"})
            return
        stop = threading.Event()
        try:
            self.send_response(200)
            self.send_header("Content-Type", "multipart/x-mixed-replace; boundary=" + BOUNDARY.decode())
            self.send_header("Cache-Control", "no-store, no-cache, must-revalidate")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("X-Frame-Options", "DENY")
            self.send_header("Referrer-Policy", "no-referrer")
            self.send_header("Connection", "close")
            self.end_headers()
            self.close_connection = True
            for jpeg in live_jpegs(stop):
                self.wfile.write(b"--" + BOUNDARY + b"\r\n")
                self.wfile.write(b"Content-Type: image/jpeg\r\n")
                self.wfile.write(b"Content-Length: " + str(len(jpeg)).encode() + b"\r\n\r\n")
                self.wfile.write(jpeg + b"\r\n")
                self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError, OSError, ValueError, RuntimeError) as error:
            # Browser navigated away, network changed, or Serve-Emu restarted.
            # Deliberately do not print cookie, input or frame data into logs.
            print("Mobile video session ended: " + type(error).__name__, flush=True)
        finally:
            stop.set()
            CLIENT_LIMIT.release()

    def emulator_input(self):
        try:
            data = self.read_json()
            status, raw = engine("/api/status")
            if status != 200:
                raise RuntimeError("Bot state unavailable")
            engine_state = json.loads(raw)
            # A paused scheduler cannot start a new routine; executing tasks
            # and any unpaused queue must still block ADB input.
            if not manual_input_allowed(engine_state):
                self.json(409, {"error": "Pause the bot and wait until the current routine finishes"})
                return
            if other_controller_recent():
                self.json(409, {"error": "Another SurvivalOps session is currently producing diagnostics. "
                                         "Manual ADB controls are temporarily blocked."})
                return
            endpoint = emulator_endpoint()
            action = data.get("action")
            if action == "tap":
                x = data.get("x")
                y = data.get("y")
                if type(x) is not int or type(y) is not int or not (0 <= x < 720 and 0 <= y < 1280):
                    raise ValueError("Coordinates must be within 720x1280")
                adb("-s", endpoint, "shell", "input", "tap", str(x), str(y), timeout=7)
            elif action == "swipe":
                fields = [data.get(key) for key in ("x", "y", "toX", "toY")]
                duration = data.get("duration", 400)
                if any(type(value) is not int for value in fields) or type(duration) is not int:
                    raise ValueError("Invalid swipe values")
                x, y, x2, y2 = fields
                if not (0 <= x < 720 and 0 <= x2 < 720 and 0 <= y < 1280
                        and 0 <= y2 < 1280 and 100 <= duration <= 2000):
                    raise ValueError("Swipe out of range")
                adb("-s", endpoint, "shell", "input", "swipe", str(x), str(y),
                    str(x2), str(y2), str(duration), timeout=8)
            elif action == "back":
                adb("-s", endpoint, "shell", "input", "keyevent", "4", timeout=7)
            else:
                raise ValueError("Unsupported manual control")
            self.json(200, {"ok": True})
        except ValueError as error:
            self.json(400, {"error": str(error)})
        except (RuntimeError, subprocess.TimeoutExpired):
            self.json(503, {"error": "ADB input failed"})

    def logs(self):
        log_file = (Path.home() / ".survivalops/workspaces/stable/web/logs/frostguard.log")
        if not log_file.is_file():
            self.json(200, {"lines": [], "note": "No runtime log yet"})
            return
        # Read bounded tail, never accept a filesystem path from a browser.
        with log_file.open("rb") as f:
            f.seek(0, 2)
            length = f.tell()
            f.seek(max(0, length - 120000))
            lines = f.read(120000).decode("utf-8", errors="replace").splitlines()[-160:]
        self.json(200, {"lines": lines})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="10.0.0.100")
    parser.add_argument("--port", type=int, default=9098)
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    private = Path.home() / "survivalops-diagnostics/private"
    certificate = TLS_DIR / "server-cert.pem"
    private_key = TLS_DIR / "server-key.pem"
    context.load_cert_chain(certificate if certificate.exists() else private / "server-cert.pem",
                            private_key if private_key.exists() else private / "server-key.pem")
    server.socket = context.wrap_socket(server.socket, server_side=True)
    print(f"SurvivalOps Web HTTPS ready on https://{args.host}:{args.port}", flush=True)
    start_monitor(engine, TIMELINE)
    run_limit.start_monitor(engine, adb, emulator_endpoint)
    server.serve_forever()


if __name__ == "__main__":
    main()
