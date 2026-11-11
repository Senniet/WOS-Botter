#!/usr/bin/env python3
"""Report UI templates whose image files are absent. Exit nonzero in strict mode."""
import argparse
import json
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
    parser.add_argument("--baseline", type=Path,
                        help="fail if any new missing asset appears beyond an existing baseline")
    args = parser.parse_args()
    total, missing = audit()
    print(f"Template audit: {total} references, {len(missing)} missing assets")
    for name, resource in missing:
        print(f"MISSING {name}: {resource}")
    if args.baseline:
        allowed = set(json.loads(args.baseline.read_text(encoding="utf-8")))
        current = {name for name, _ in missing}
        introduced = current - allowed
        resolved = allowed - current
        for name in sorted(introduced):
            print(f"NEW MISSING {name}")
        for name in sorted(resolved):
            print(f"RESOLVED {name}")
        if introduced:
            raise SystemExit(1)
    if args.strict and missing:
        raise SystemExit(1)
