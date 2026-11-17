#!/usr/bin/env python3
"""Private, LAN-bound SurvivalOps HTTPS diagnostics inbox. Stdlib only."""
import hashlib
import base64
import binascii
import hmac
import json
import os
from pathlib import Path
import re
import ssl
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent
INBOX = ROOT / "inbox"
TOKEN_FILE = ROOT / "private" / "upload-token"
MAX_BYTES = 5 * 1024 * 1024
MAX_PER_DAY = 150
RETENTION_DAYS = 14
INBOX.mkdir(parents=True, exist_ok=True)
TOKEN = TOKEN_FILE.read_text().strip().encode()

def purge():
    threshold = time.time() - RETENTION_DAYS * 86400
    for day in INBOX.iterdir():
        if not day.is_dir() or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", day.name):
            continue
        for item in day.iterdir():
            if item.is_file() and item.stat().st_mtime < threshold:
                item.unlink(missing_ok=True)
        if not any(day.iterdir()):
            day.rmdir()

class Handler(BaseHTTPRequestHandler):
    server_version = "SurvivalOpsLAN/1"
    def log_message(self, fmt, *args):
        print("%s %s" % (self.address_string(), fmt % args), flush=True)

    def reply(self, status, message):
        body = json.dumps({"ok": status < 300, "message": message}).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def authorized(self):
        raw = self.headers.get("Authorization", "")
        return raw.startswith("Bearer ") and hmac.compare_digest(raw[7:].encode(), TOKEN)

    def do_GET(self):
        if urlsplit(self.path).path != "/health":
            return self.reply(404, "not found")
        if not self.authorized():
            return self.reply(401, "unauthorized")
        self.reply(200, "LAN diagnostics inbox ready")

    def do_POST(self):
        if urlsplit(self.path).path != "/api/diagnostics":
            return self.reply(404, "not found")
        if not self.authorized():
            return self.reply(401, "unauthorized")
        try:
            size = int(self.headers.get("Content-Length", "-1"))
        except ValueError:
            return self.reply(400, "invalid content length")
        if size < 10 or size > MAX_BYTES:
            return self.reply(413, "payload too large or missing")
        if self.headers.get("Content-Type", "") != "application/json":
            return self.reply(415, "JSON required")
        purge()
        day = INBOX / time.strftime("%Y-%m-%d", time.gmtime())
        day.mkdir(mode=0o700, parents=True, exist_ok=True)
        if sum(1 for p in day.iterdir() if p.suffix == ".json") >= MAX_PER_DAY:
            return self.reply(429, "daily quota exceeded")
        try:
            obj = json.loads(self.rfile.read(size))
            if not isinstance(obj, dict) or obj.get("schema") != 1:
                return self.reply(400, "invalid diagnostic schema")
            if not isinstance(obj.get("activity"), str) or len(obj["activity"]) > 80:
                return self.reply(400, "invalid activity")
            if not isinstance(obj.get("kind"), str) or len(obj["kind"]) > 80:
                return self.reply(400, "invalid kind")
            if len(json.dumps(obj)) > MAX_BYTES:
                return self.reply(413, "payload too large")
            screenshot = None
            if "image_png_base64" in obj:
                try:
                    screenshot = base64.b64decode(obj.pop("image_png_base64"), validate=True)
                except (ValueError, binascii.Error):
                    return self.reply(400, "invalid image encoding")
                if len(screenshot) > 2_000_000 or screenshot[:8] != bytes.fromhex("89504e470d0a1a0a"):
                    return self.reply(400, "invalid screenshot PNG")
            filename = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime()) + "-" + uuid.uuid4().hex[:12] + ".json"
            target = day / filename
            if screenshot is not None:
                image_path = day / (filename[:-5] + ".png")
                with image_path.open("xb") as output:
                    output.write(screenshot)
                image_path.chmod(0o600)
                obj["image_file"] = image_path.name
            obj.pop("image_name", None)
            with target.open("x", encoding="utf-8") as out:
                json.dump(obj, out, separators=(",", ":"))
            target.chmod(0o600)
            self.reply(201, filename)
        except (ValueError, TypeError, UnicodeError) as ex:
            self.reply(400, "malformed diagnostic")

class Server(ThreadingHTTPServer):
    daemon_threads = True

if __name__ == "__main__":
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(ROOT / "private/server-cert.pem", ROOT / "private/server-key.pem")
    server = Server(("10.0.0.100", 18761), Handler)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    print("SurvivalOps diagnostics listening on 10.0.0.100:18761", flush=True)
    server.serve_forever(poll_interval=0.5)
