#!/usr/bin/env python3
"""Report UI templates whose image files are absent. Exit nonzero in strict mode."""
import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "modules/api/src/main/resources/config/templates.properties"
ASSETS = ROOT / "modules/vision/src/main/resources"

def audit():
    missing = []
    total = 0
    for raw in MANIFEST.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, resource = (part.strip() for part in line.split("=", 1))
        if not resource.startswith("/templates/"):
            continue
        total += 1
        if not (ASSETS / resource.lstrip("/")).is_file():
            missing.append((name, resource))
    return total, missing

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true", help="fail if any template is missing")
    args = parser.parse_args()
    total, missing = audit()
    print(f"Template audit: {total} references, {len(missing)} missing assets")
    for name, resource in missing:
        print(f"MISSING {name}: {resource}")
    if args.strict and missing:
        raise SystemExit(1)
