"""Bounded, authenticated Task Coach ZIP store for private SurvivalOps LAN diagnostics.

The HTTPS handler owns authentication. This helper validates content and writes
only a small allowlisted subset into private task-scoped directories.
"""
from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
import threading
import time
import zipfile

MAX_ARCHIVE_BYTES = 30 * 1024 * 1024
MAX_UNCOMPRESSED_BYTES = 60 * 1024 * 1024
MAX_ENTRY_BYTES = 4 * 1024 * 1024
MAX_FILES = 96
MAX_RUNS_PER_DAY = 250
RETENTION_DAYS = 30
SESSION_RE = re.compile(r"[0-9]{8}-[0-9]{6}-[a-z0-9]{1,20}\Z")
IMAGE_RE = re.compile(r"(?:captures|proposals)/[a-zA-Z0-9_-]{1,115}\.png\Z")
FILES = frozenset({"run.txt", "summary.txt", "recognition-events.csv", "upload-manifest.json"})
LOCK = threading.RLock()
PNG_HEADER = bytes.fromhex("89504e470d0a1a0a")


class UploadRejected(ValueError):
    def __init__(self, message: str, status: int = 400):
        super().__init__(message)
        self.status = status


def _verify_archive(data: bytes) -> list[zipfile.ZipInfo]:
    if not 100 <= len(data) <= MAX_ARCHIVE_BYTES:
        raise UploadRejected("Evidence archive outside allowed size", 413)
    try:
        container = zipfile.ZipFile(io.BytesIO(data))
    except (OSError, zipfile.BadZipFile):
        raise UploadRejected("Invalid ZIP archive") from None
    with container:
        entries = container.infolist()
        names = [f.filename for f in entries]
        if not entries or len(entries) > MAX_FILES:
            raise UploadRejected("Unexpected number of evidence files", 413)
        if len(set(names)) != len(names):
            raise UploadRejected("Duplicate evidence filenames")
        if "summary.txt" not in names or "recognition-events.csv" not in names or "upload-manifest.json" not in names:
            raise UploadRejected("Required report files missing")
        total = 0
        for entry in entries:
            if entry.is_dir() or entry.filename not in FILES and not IMAGE_RE.fullmatch(entry.filename):
                raise UploadRejected("Unexpected evidence file path")
            if entry.file_size > MAX_ENTRY_BYTES:
                raise UploadRejected("Evidence file too large", 413)
            total += entry.file_size
            if total > MAX_UNCOMPRESSED_BYTES:
                raise UploadRejected("Evidence archive exceeds uncompressed limit", 413)
            if (entry.external_attr >> 16) & 0o170000 == 0o120000:
                raise UploadRejected("Symlink evidence not allowed")
            # ZIP encryption and unsupported compression must never be accepted.
            if entry.flag_bits & 1 or entry.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
                raise UploadRejected("Unsupported or encrypted ZIP")
        try:
            manifest = json.loads(container.read("upload-manifest.json"))
            if manifest.get("schema") != 1:
                raise ValueError("Invalid schema")
            if not isinstance(manifest.get("session_id"), str) or not SESSION_RE.fullmatch(manifest["session_id"]):
                raise ValueError("Invalid session")
            for entry in entries:
                if entry.filename.endswith(".png") and not container.read(entry, pwd=None).startswith(PNG_HEADER):
                    raise ValueError("Invalid PNG evidence")
        except (ValueError, KeyError, RuntimeError, zipfile.BadZipFile, UnicodeDecodeError):
            raise UploadRejected("Evidence archive failed validation") from None
    return entries


def _purge_old_runs(root: Path):
    cutoff = time.time() - RETENTION_DAYS * 86400
    if not root.is_dir():
        return
    for day in root.iterdir():
        if not day.is_dir() or not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", day.name):
            continue
        for run in day.iterdir():
            if run.is_dir() and SESSION_RE.fullmatch(run.name) and run.stat().st_mtime < cutoff:
                shutil.rmtree(run)
        if not any(day.iterdir()):
            day.rmdir()


def store_taskcoach_archive(root: Path, session_id: str, data: bytes) -> tuple[int, str]:
    """Accept a session archive into root/taskcoach/YYYY-MM-DD/<session_id>.

    Caller must validate bearer auth BEFORE invoking this method.
    """
    if not isinstance(session_id, str) or not SESSION_RE.fullmatch(session_id):
        raise UploadRejected("Invalid Task Coach session identifier")
    _verify_archive(data)
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        manifest = json.loads(archive.read("upload-manifest.json"))
    if manifest["session_id"] != session_id:
        raise UploadRejected("Session identifier does not match manifest")
    digest = hashlib.sha256(data).hexdigest()
    today = time.strftime("%Y-%m-%d", time.gmtime())
    store = root / "taskcoach"
    with LOCK:
        store.mkdir(mode=0o700, parents=True, exist_ok=True)
        _purge_old_runs(store)
        parent = store / today
        parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        target = parent / session_id
        if target.exists():
            receipt = target / "receipt.json"
            if receipt.is_file() and json.loads(receipt.read_text()).get("sha256") == digest:
                return 200, f"taskcoach/{today}/{session_id} (already uploaded)"
            raise UploadRejected("Session already exists with different evidence", 409)
        if sum(1 for p in parent.iterdir() if p.is_dir()) >= MAX_RUNS_PER_DAY:
            raise UploadRejected("Daily Task Coach upload limit reached", 429)
        temporary = Path(tempfile.mkdtemp(prefix=".receiving-", dir=parent))
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                for entry in archive.infolist():
                    output = temporary / entry.filename
                    output.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
                    with archive.open(entry) as source, output.open("xb") as dest:
                        copied = shutil.copyfileobj(source, dest, length=128 * 1024)
                    output.chmod(0o600)
            receipt = {
                "schema": 1,
                "session_id": session_id,
                "received_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                "sha256": digest,
                "bytes": len(data),
                "files": len(list(temporary.rglob("*"))) - len([p for p in temporary.rglob("*") if p.is_dir()]),
                "relative_path": f"taskcoach/{today}/{session_id}",
            }
            (temporary / "receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
            (temporary / "receipt.json").chmod(0o600)
            temporary.rename(target)
        except Exception:
            shutil.rmtree(temporary, ignore_errors=True)
            raise
    return 201, f"taskcoach/{today}/{session_id}"
