"""Serve-Emu's existing H.264 WebSocket -> continuously streamed MJPEG.

The source is the SAME scrcpy WebSocket consumed by native desktop Serve-Emu;
no ADB screencap polling, no new emulator or second scrcpy server. The small
FFmpeg process decodes one continuous H.264 feed and encodes live JPEG frames
for browsers with native multipart/x-mixed-replace <img> support.

This module has no third-party Python dependencies. Video and input remain
inside the same-origin SurvivalOps LAN HTTPS gateway.
"""
from __future__ import annotations

import base64
import hashlib
import os
import socket
import ssl
import struct
import subprocess
import threading
from contextlib import closing
from http import HTTPStatus

# Serve-Emu's self-signed device-manager TLS certificate, pinned as SHA-256.
# This is local trusted equipment, never a publicly routable stream endpoint.
CERT_SHA256 = "9d7cfc757f89b4e46e674e0c43a26d942796a0eea40e3087f70c9a2ae267a55f"
SOURCE_HOST = "192.168.1.20"
SOURCE_PORT = 8448
BOUNDARY = b"liveframe"
MAX_VIDEO_FRAME = 3 * 1024 * 1024
CLIENT_LIMIT = threading.BoundedSemaphore(2)


def _read_exact(stream, n: int) -> bytes:
    if not (0 <= n <= MAX_VIDEO_FRAME):
        raise ValueError("Video WebSocket message too large")
    data = stream.read(n)
    if len(data) != n:
        raise ConnectionError("Serve-Emu WebSocket ended")
    return data


def _open_ws():
    ssl_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    ssl_context.check_hostname = False
    ssl_context.verify_mode = ssl.CERT_NONE
    sock = socket.create_connection((SOURCE_HOST, SOURCE_PORT), timeout=8)
    try:
        tls = ssl_context.wrap_socket(sock, server_hostname=SOURCE_HOST)
        if hashlib.sha256(tls.getpeercert(binary_form=True)).hexdigest() != CERT_SHA256:
            tls.close()
            raise ssl.SSLError("Serve-Emu TLS certificate changed; refusing live connection")
        tls.settimeout(9)
        key = base64.b64encode(os.urandom(16)).decode("ascii")
        request = (f"GET /ws?frame-meta=1 HTTP/1.1\r\nHost: {SOURCE_HOST}:{SOURCE_PORT}\r\n"
                   f"Upgrade: websocket\r\nConnection: Upgrade\r\n"
                   f"Sec-WebSocket-Version: 13\r\nSec-WebSocket-Key: {key}\r\n\r\n")
        tls.sendall(request.encode("ascii"))
        stream = tls.makefile("rb")
        status = stream.readline(2048)
        headers = {}
        for _ in range(70):
            line = stream.readline(2048)
            if line == b"\r\n":
                break
            if not line:
                raise ConnectionError("Truncated WebSocket handshake")
            if b":" in line:
                name, value = line.decode("latin-1").split(":", 1)
                headers[name.lower()] = value.strip()
        else:
            raise ValueError("Excessive WebSocket headers")
        expected = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest()).decode()
        if b" 101 " not in status or headers.get("sec-websocket-accept") != expected:
            raise ConnectionError(f"Serve-Emu rejected WebSocket upgrade: {status[:100]!r}")
        return tls, stream
    except Exception:
        sock.close()
        raise


def _next_ws_video(stream):
    """Read one RFC6455 frame, skipping control JSON and stripping SEMU v2 metadata."""
    while True:
        b0, b1 = _read_exact(stream, 2)
        opcode = b0 & 15
        length = b1 & 127
        if length == 126:
            length = struct.unpack(">H", _read_exact(stream, 2))[0]
        elif length == 127:
            length = struct.unpack(">Q", _read_exact(stream, 8))[0]
        mask = _read_exact(stream, 4) if b1 & 128 else None
        payload = _read_exact(stream, length)
        if mask:
            payload = bytes(v ^ mask[i % 4] for i, v in enumerate(payload))
        if opcode == 8:
            raise ConnectionError("Serve-Emu closed H.264 stream")
        if opcode != 2:
            continue
        if payload.startswith(b"SEMU") and len(payload) > 24:
            payload = payload[24:] if payload[4] == 2 else payload[16:]
        # Annex-B start codes ensure we are not feeding a JSON/control frame.
        if payload.startswith((b"\x00\x00\x00\x01", b"\x00\x00\x01")):
            return payload


def live_jpegs(stop: threading.Event):
    """Yield actual decoded frames while video is arriving, no polling at all."""
    tls, stream = _open_ws()
    ffmpeg = subprocess.Popen([
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin",
        "-threads", "2", "-probesize", "2048", "-analyzeduration", "0",
        "-f", "h264", "-r", "40", "-i", "pipe:0", "-an",
        "-vf", "fps=18,scale=720:1280:flags=fast_bilinear",
        "-q:v", "8", "-f", "image2pipe", "-c:v", "mjpeg", "pipe:1"
    ], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, bufsize=0)

    def pump_h264():
        try:
            while not stop.is_set() and ffmpeg.poll() is None:
                packet = _next_ws_video(stream)
                ffmpeg.stdin.write(packet)
        except (OSError, ConnectionError, ValueError, ssl.SSLError):
            pass
        finally:
            try: ffmpeg.stdin.close()
            except OSError: pass

    reader = threading.Thread(target=pump_h264, daemon=True, name="serve-emu-h264-feed")
    reader.start()
    try:
        data = bytearray()
        while not stop.is_set() and ffmpeg.poll() is None:
            chunk = ffmpeg.stdout.read(65536)
            if not chunk:
                break
            data.extend(chunk)
            # JPEG frames have an unambiguous SOI + EOI sequence. Do not wait
            # for another HTTP request, a timer, or an ADB screenshot.
            while True:
                start = data.find(b"\xff\xd8")
                if start < 0:
                    data.clear()
                    break
                if start > 0:
                    del data[:start]
                end = data.find(b"\xff\xd9", 2)
                if end < 0:
                    break
                image = bytes(data[:end + 2])
                del data[:end + 2]
                yield image
            if len(data) > MAX_VIDEO_FRAME:
                raise ValueError("MJPEG decoder buffer exceeded frame limit")
    finally:
        stop.set()
        try: tls.shutdown(socket.SHUT_RDWR)
        except OSError: pass
        tls.close()
        stream.close()
        if ffmpeg.poll() is None:
            ffmpeg.kill()
        try: ffmpeg.wait(timeout=2)
        except subprocess.TimeoutExpired: pass
        reader.join(timeout=1)
