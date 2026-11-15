#!/usr/bin/env python3
"""Read-only task coverage inventory. Never sends emulator input or logs account IDs."""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import json
import pathlib
import re
import struct
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
ENUM_PATH = ROOT / 'modules/api/src/main/java/dev/frostguard/api/configs/TpDailyTaskEnum.java'
REGISTRY_PATH = ROOT / 'modules/tasks/src/main/java/dev/frostguard/tasks/TaskRegistrations.java'
SOURCE_DIR = ROOT / 'modules/tasks/src/main/java'
TEST_DIR = ROOT / 'modules/tasks/src/test/java'
ENUM_RE = re.compile(r'^\s*([A-Z][A-Z0-9_]+)\s*\(\s*(\d+)\s*,\s*"([^"]+)"\s*,\s*([^,]+),\s*RoutineCategory\.([A-Z_]+)\s*\)', re.M)
REGISTRY_RE = re.compile(r'\bcase\s+((?:[A-Z0-9_]+\s*,\s*)*[A-Z0-9_]+)\s*->\s*new\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(')
PRIVILEGED_CATEGORIES = {'EVENT', 'LIFECYCLE', 'CUSTOM'}


def catalogue():
    entries = [dict(key=k, code=int(code), label=label, config=config.strip(), category=cat)
               for k, code, label, config, cat in ENUM_RE.findall(ENUM_PATH.read_text())]
    if not entries or len(set(entry['key'] for entry in entries)) != len(entries):
        raise RuntimeError('Missing or duplicate enum entries in the task catalogue')
    factory = {}
    for keys, cls in REGISTRY_RE.findall(REGISTRY_PATH.read_text()):
        for key in (s.strip() for s in keys.split(',')):
            if key in factory:
                raise RuntimeError('Duplicate registration for ' + key)
            factory[key] = cls
    tests = [(p, p.read_text(errors='replace')) for p in TEST_DIR.rglob('*Test.java')]
    sources = {p.stem: p for p in SOURCE_DIR.rglob('*.java')}
    for entry in entries:
        key = entry['key']
        cls = factory.get(key)
        entry['factory'] = cls
        source = sources.get(cls) if cls else None
        entry['source'] = str(source.relative_to(ROOT)) if source else None
        code = source.read_text(errors='replace') if source else ''
        stem = (cls or '').removesuffix('Routine')
        related = [p for p, body in tests if stem and (stem in p.stem or cls in body or key in body)]
        frame_tests = [p for p in related if any(marker in p.stem for marker in ('FrameTest', 'ImageTest', 'RecognitionTest'))]
        entry['tests'] = len(related)
        entry['test_classes'] = [p.stem for p in related]
        entry['frame_tests'] = len(frame_tests)
        entry['ocr_calls'] = len(re.findall(r'\breadText\s*\(|\battemptRecognition\s*\(', code))
        entry['template_calls'] = len(re.findall(r'\blocatePattern\s*\(|\blocateAction\s*\(', code))
        entry['direct_taps'] = len(re.findall(r'\btapInside\s*\(|\btapRandomPoint\s*\(', code))
        entry['diagnostic_capture'] = ('captureInterfaceFailure(' in code or 'TaskDiagnosticSnapshots.' in code)
        entry['live_incidents'] = 0
    return entries


def incident_counts(entries, directory, window_hours=24):
    if not directory.exists():
        return
    threshold = dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=window_hours)
    incidents = []
    for file in directory.glob('*/*.json'):
        try:
            data = json.loads(file.read_text())
            stamp = dt.datetime.fromisoformat(data.get('timestamp', '').replace('Z', '+00:00'))
            if stamp < threshold:
                continue
            incidents.append(data)
        except (ValueError, KeyError, OSError):
            continue
    def slug(text):
        return re.sub(r'[^a-z0-9]', '', text.lower())
    for entry in entries:
        class_slug = slug((entry['factory'] or '').removesuffix('Routine'))
        enum_slug = slug(entry['key'])
        entry['live_incidents'] = sum(1 for d in incidents
            if any(alias and (alias in slug(d.get('activity', '')) or slug(d.get('activity', '')) in alias)
                   for alias in (class_slug, enum_slug))
            and len(slug(d.get('activity', ''))) >= 5)


def inspect_screen(url, cache_path):
    # The endpoint provides a screenshot only. This function never calls tap,
    # swipe, key, navigation or any other mutating API.
    try:
        with urllib.request.urlopen(url, timeout=12) as response:
            if response.headers.get_content_type() != 'image/png':
                return 'not available (not a PNG response)'
            frame = response.read(8_000_000)
        if frame[:8] != b'\x89PNG\r\n\x1a\n' or len(frame) < 24:
            return 'not available (invalid PNG)'
        width, height = struct.unpack('>II', frame[16:24])
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        cache_path.write_bytes(frame)
        return f'{width}x{height}, PNG, captured read-only (private local cache)'
    except (OSError, ValueError) as exc:
        return 'not available (' + type(exc).__name__ + ')'


def report(entries, screen, output):
    grouped = collections.defaultdict(list)
    for task in entries:
        grouped[task['category']].append(task)
    missing = [e for e in entries if e['key'] != 'CUSTOM_TASK' and not e['factory']]
    missing_src = [e for e in entries if e['factory'] and not e['source']]
    no_tests = [e for e in entries if e['factory'] and not e['tests']]
    frame_covered = sum(e['frame_tests'] > 0 for e in entries)
    lines = [
        '# SurvivalOps task inventory and coverage baseline', '',
        'Generated from `TpDailyTaskEnum`, `TaskRegistrations`, source tests and local diagnostics.',
        '**Evidence levels must not be conflated:** a source test is not a live task completion.', '',
        f'- Registered catalogue: **{len(entries)}** task types across **{len(grouped)}** categories.',
        f'- Factory omissions (excluding custom task): **{len(missing)}**; missing source: **{len(missing_src)}**.',
        f'- Source-test candidates found: **{len(entries)-len(no_tests)-len(missing)}**; without candidate tests: **{len(no_tests)}**.',
        f'- Frame/recognition test candidates found: **{frame_covered}** task types.',
        f'- Current serve-emu screenshot: **{screen}**.',
        '', 'The test numbers below are static reference heuristics, not proof of successful UI actions.',
        'Incidents are counts of relevant diagnostic snapshots in the last 24 hours, not all failures.',
        'A safe end-to-end pass requires before/after screen evidence, verified clicks, actual claimed result,',
        'bounded timeout, correct cooldown/retry schedule and no unintended spending.', '',
        '## Execution policy', '',
        '1. Verify active ADB serial, physical 720x1280 screenshot, configured UI distance and account ownership.',
        '2. Gate each task by its verified screen state and availability; do not use stale coordinates or blind Back.',
        '3. Check relevant button with two independent confirmations when ambiguous, then perform one action.',
        '4. Classify observed result as confirmed, unavailable, blocked by game conditions, or unknown.',
        '5. For unavailable features, report the actual gate and do not repeatedly attempt a tap.',
        '6. Never test purchases, premium currency, special events, alliance membership changes or FC upgrades without an explicit safe budget.',
        '7. Retain redacted before/after frames with expected and observed state; recheck scheduling after failures.', '',
        '## Catalogue', ''
    ]
    for category in sorted(grouped):
        tasks = grouped[category]
        lines += [f'### {category.replace("_", " ").title()} ({len(tasks)})', '',
                  '| Task | Implementation | Tests* | Frame tests* | Live incidents | Gate |',
                  '|---|---|---:|---:|---:|---|']
        for item in tasks:
            if item['key'] == 'CUSTOM_TASK':
                gate = 'User-defined workflow; no built-in factory'
            elif item['factory'] is None or item['source'] is None:
                gate = 'Missing registration/source'
            elif category in PRIVILEGED_CATEGORIES:
                gate = 'Restricted / active-state dependent'
            else:
                gate = 'Live E2E not yet proven'
            lines.append(f'| {item["label"]} (`{item["key"]}`) | '
                         f'`{item["factory"] or "—"}` | {item["tests"]} | {item["frame_tests"]} | '
                         f'{item["live_incidents"]} | {gate} |')
        lines += ['', '']
    lines += ['## Prioritisation', '',
             '- P0: live recurring incidents involving UI ownership, modal recognition, stamina/storehouse, home recovery.',
             '- P1: all daily routines, training, gathering, mail, Intel, arena and survivors; require real-account replay evidence.',
             '- P2: alliance, beasts, pets, research, exploration and labyrinth with explicit cooldown and action budgets.',
             '- P3: conditional/limited-time events and account/lifecycle workflows; run only when their screen safely exists.', '',
             'The programme is **not completed** by this inventory: it only establishes complete scope and auditable coverage gaps.', '']
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text('\n'.join(lines))
    return missing, missing_src, no_tests, frame_covered


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=pathlib.Path, default=ROOT / 'docs/qa/survivalops-task-inventory.md')
    p.add_argument('--diagnostics', type=pathlib.Path, default=pathlib.Path.home() / 'survivalops-diagnostics/inbox')
    p.add_argument('--screenshot-url', default='http://192.168.1.20:3300/api/screenshot')
    p.add_argument('--private-screen-cache', type=pathlib.Path, default=pathlib.Path.home() / '.cache/survivalops-audit/latest-readonly.png')
    p.add_argument('--no-live', action='store_true')
    p.add_argument('--strict', action='store_true', help='Fail when a built-in registered task has no factory or source')
    args = p.parse_args()
    entries = catalogue()
    incident_counts(entries, args.diagnostics)
    screen = 'disabled' if args.no_live else inspect_screen(args.screenshot_url, args.private_screen_cache)
    missing, missing_src, no_tests, frame = report(entries, screen, args.output)
    print(json.dumps(dict(tasks=len(entries), categories=len(set(e['category'] for e in entries)),
          factory_missing=[e['key'] for e in missing], source_missing=[e['key'] for e in missing_src],
          no_test_candidates=len(no_tests), frame_test_candidates=frame, screen=screen,
          output=str(args.output)), indent=2))
    if args.strict and (missing or missing_src):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
