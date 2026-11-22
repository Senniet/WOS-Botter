"""Read-only task timeline snapshots with a bounded, durable observation history.

The Java scheduler remains the only authority for whether/when a task executes.
Each distinct lastAttempt timestamp is stored so later schedules do not erase
the previous run. The observer never triggers or changes a task.
"""
from __future__ import annotations

from datetime import datetime, timedelta
import json
import os
from pathlib import Path
import re
import sqlite3
import threading
from urllib.parse import quote

DB_PATH = Path.home() / ".local/share/survivalops-web/task-timeline.sqlite3"
PROFILE_PATTERN = re.compile(r"[0-9]{1,12}\Z")
FINAL = {"SUCCEEDED", "FAILED", "DEFERRED", "SKIPPED", "CANCELLED"}
OBSERVED = FINAL | {"RUNNING", "UNKNOWN"}


def valid_profile(profile_id: str) -> str:
    if not isinstance(profile_id, str) or not PROFILE_PATTERN.fullmatch(profile_id):
        raise ValueError("Invalid profile")
    return profile_id


def outcome_for(task: dict, scheduler_running: bool = True) -> str:
    if scheduler_running and task.get("executing"):
        return "RUNNING"
    outcome = str(task.get("outcome") or "").upper()
    if outcome in FINAL:
        return outcome
    # Java's initial schedule record can set lastAttempt == lastSuccess even
    # though the task has never run. Never infer completion from timestamps.
    return "UNKNOWN"


class TimelineArchive:
    def __init__(self, db_path: Path = DB_PATH):
        self.path = Path(db_path)
        self.lock = threading.RLock()
        self.path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        with self._connect() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS observations (
                profile_id TEXT NOT NULL,
                task_id TEXT NOT NULL,
                attempt_at TEXT NOT NULL,
                task_name TEXT NOT NULL,
                outcome TEXT NOT NULL,
                observed_at TEXT NOT NULL,
                PRIMARY KEY (profile_id, task_id, attempt_at)
            )""")
            db.execute("CREATE INDEX IF NOT EXISTS observations_latest "
                       "ON observations(profile_id, attempt_at DESC)")

    def _connect(self):
        db = sqlite3.connect(self.path, timeout=5)
        os.chmod(self.path, 0o600)
        return db

    def observe(self, profile_id: str, tasks: list[dict], scheduler_running: bool = True):
        valid_profile(profile_id)
        now = datetime.now().isoformat(timespec="seconds")
        with self.lock, self._connect() as db:
            for task in tasks:
                stamp = task.get("lastAttempt")
                task_id = task.get("id")
                if not isinstance(stamp, str) or not isinstance(task_id, str):
                    continue
                if not re.fullmatch(r"[A-Z0-9_]{2,90}", task_id):
                    continue
                try:
                    parsed = datetime.fromisoformat(stamp)
                except ValueError:
                    continue
                if parsed.year < 2020 or parsed > datetime.now() + timedelta(days=1):
                    continue
                new_status = outcome_for(task, scheduler_running)
                old = db.execute(
                    "SELECT outcome FROM observations "
                    "WHERE profile_id=? AND task_id=? AND attempt_at=?",
                    (profile_id, task_id, stamp)
                ).fetchone()
                if not old and str(task.get("outcome") or "").upper() == "NOT_RUN":
                    # Scheduling a new task is not an execution event.
                    # Retain already observed RUNNING->UNKNOWN transitions below.
                    continue
                if old and old[0] in FINAL:
                    # An engine restart can reset a completed task to NOT_RUN.
                    # Do not overwrite its recorded outcome with a weaker state.
                    continue
                name = str(task.get("name") or task_id)[:120]
                db.execute(
                    "INSERT INTO observations (profile_id,task_id,attempt_at,task_name,outcome,observed_at) "
                    "VALUES (?,?,?,?,?,?) "
                    "ON CONFLICT(profile_id,task_id,attempt_at) DO UPDATE SET "
                    "task_name=excluded.task_name, outcome=excluded.outcome, observed_at=excluded.observed_at",
                    (profile_id, task_id, stamp, name, new_status, now),
                )
            # Bound the on-disk archive without collapsing repeat executions.
            db.execute("DELETE FROM observations WHERE profile_id=? AND attempt_at < ?",
                       (profile_id, (datetime.now() - timedelta(days=30)).isoformat()))
            db.execute("""DELETE FROM observations WHERE profile_id=? AND rowid NOT IN (
                SELECT rowid FROM observations WHERE profile_id=?
                ORDER BY attempt_at DESC, task_id LIMIT 1500
            )""", (profile_id, profile_id))

    def recent(self, profile_id: str, limit: int = 100) -> list[dict]:
        valid_profile(profile_id)
        with self.lock, self._connect() as db:
            db.row_factory = sqlite3.Row
            rows = db.execute(
                "SELECT task_id,task_name,attempt_at,outcome FROM observations "
                "WHERE profile_id=? ORDER BY attempt_at DESC,task_id LIMIT ?",
                (profile_id, min(max(limit, 1), 200))
            ).fetchall()
            return [dict(row) for row in rows]


def snapshot(profile_id: str, tasks: list[dict], archive: TimelineArchive,
             scheduler_running: bool = True) -> dict:
    valid_profile(profile_id)
    archive.observe(profile_id, tasks, scheduler_running)
    running = []
    upcoming = []
    overdue = []
    unscheduled = []
    now = datetime.now()
    for task in tasks:
        if not task.get("enabled"):
            continue
        row = {
            "task_id": str(task.get("id", "")),
            "task_name": str(task.get("name", "")),
            "next": task.get("next") or "",
            "queued": bool(task.get("queued")),
            "last_attempt": (task.get("lastAttempt") or "")
                if str(task.get("outcome") or "").upper() != "NOT_RUN" else "",
        }
        if scheduler_running and task.get("executing"):
            running.append(row)
        elif row["next"]:
            try:
                due = datetime.fromisoformat(row["next"])
                # Java scheduler timestamps are server-local (usually naive).
                current = now.astimezone(due.tzinfo) if due.tzinfo else now
                if due <= current:
                    overdue.append(row)
                else:
                    upcoming.append(row)
            except ValueError:
                unscheduled.append(row)
        else:
            unscheduled.append(row)
    upcoming = [row for row in upcoming if row["queued"]]
    overdue = [row for row in overdue if row["queued"]]
    upcoming.sort(key=lambda t: (t["next"], t["task_name"]))
    overdue.sort(key=lambda t: (t["next"], t["task_name"]))
    # An old RUNNING observation is not a second active task. Show only
    # completed attempts in history, keeping current execution in running.
    active = {r["task_id"] for r in running}
    recent = [r for r in archive.recent(profile_id)
              if r["outcome"] in FINAL and r["task_id"] not in active]
    return {"asOf": datetime.now().isoformat(timespec="seconds"),
            "running": running, "upcoming": upcoming, "overdue": overdue,
            "unscheduled": unscheduled, "recent": recent}


def start_monitor(engine, archive: TimelineArchive, interval: int = 5) -> threading.Event:
    """Sample scheduler state while the browser is closed too.

    Each sample is read-only; missing engine responses do not become run events.
    """
    stop = threading.Event()

    def poll():
        while not stop.is_set():
            try:
                status_code, status_raw = engine("/api/status")
                if status_code != 200:
                    stop.wait(interval)
                    continue
                scheduler_running = bool(json.loads(status_raw).get("running"))
                code, raw = engine("/api/profiles")
                if code == 200:
                    for profile in json.loads(raw):
                        profile_id = str(profile.get("id", ""))
                        if not PROFILE_PATTERN.fullmatch(profile_id):
                            continue
                        code, raw = engine("/api/tasks?profile=" + quote(profile_id))
                        if code == 200:
                            archive.observe(profile_id, json.loads(raw), scheduler_running)
            except (OSError, ValueError, TypeError, sqlite3.Error, RuntimeError):
                # A temporary Java shutdown is not an execution outcome.
                pass
            stop.wait(interval)

    thread = threading.Thread(target=poll, name="survivalops-task-timeline", daemon=True)
    thread.start()
    return stop
