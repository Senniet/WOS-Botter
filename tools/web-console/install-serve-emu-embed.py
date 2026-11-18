#!/usr/bin/env python3
"""Install a CSS-only compact player page alongside the native Serve-Emu UI.

Does not modify the original /index.html or its React application. The page
uses the original bundle /assets, original /ws stream, and original input code.
It must be reinstalled if the Serve-Emu container is recreated.
"""
from pathlib import Path
import subprocess
import tempfile
import sys

CONTAINER = "ix-serve-emu-serve-emu-1"
DEST = "/opt/serve-emu/node_modules/serve-emu/dist/ui"
CSS = Path("/home/truenas_admin/survivalops-web-proxy/serve-emu-embed.css")

def docker(*args):
    return subprocess.run(["docker", *args], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=15).stdout

def main():
    try:
        index = docker("exec", CONTAINER, "cat", DEST + "/index.html").decode()
        css = CSS.read_text(encoding="utf-8")
        expected = '<div id="root"></div>'
        if expected not in index or "</head>" not in index or "assets/index-" not in index:
            raise RuntimeError("Unexpected original Serve-Emu HTML; refused to modify")
        marker = "<!-- SURVIVALOPS-INTERACTIVE-EMBED v1 -->"
        injected = index.replace("<title>serve-emu</title>",
            "<title>SurvivalOps · Live Emulator</title>").replace(
            "</head>", marker + "\n<style>\n" + css + "\n</style>\n</head>", 1)
        current = b""
        try:
            current = docker("exec", CONTAINER, "cat", DEST + "/embed.html")
        except subprocess.CalledProcessError:
            pass
        if current == injected.encode():
            print("Embed is already up-to-date")
            return 0
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".html", delete=False) as target:
            target.write(injected)
            tmp = Path(target.name)
        try:
            docker("cp", str(tmp), f"{CONTAINER}:{DEST}/embed.html")
        finally:
            tmp.unlink(missing_ok=True)
        installed = docker("exec", CONTAINER, "cat", DEST + "/embed.html")
        if installed != injected.encode():
            raise RuntimeError("Installation verification failed")
        print("Installed compact interactive player, original Serve-Emu UI unchanged")
        return 0
    except Exception as exc:
        print(f"Compact player deployment unavailable: {exc}", file=sys.stderr)
        return 1

if __name__ == "__main__":
    sys.exit(main())
