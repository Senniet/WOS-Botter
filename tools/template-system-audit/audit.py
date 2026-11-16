#!/usr/bin/env python3
"""Read-only inventory of every bundled visual template and available Task Coach evidence.

No game interaction, screenshots, credentials or account identifiers are exported.
Missing matches on a frame are observations, NOT evidence that a template is broken.
"""
from __future__ import annotations
import argparse
from collections import Counter, defaultdict
import csv
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[2]
ASSETS = Path('modules/vision/src/main/resources/templates')
MANIFEST = Path('modules/api/src/main/resources/config/templates.properties')
ENUM = Path('modules/api/src/main/java/dev/frostguard/api/configs/TemplatesEnum.java')
IMAGE_EXT = {'.png'}
PNG_SIG = b'\x89PNG\r\n\x1a\n'
REFERENCE = re.compile(r'\bTemplatesEnum\.([A-Z][A-Z0-9_]+)\b')
DECISIONS=Path('tools/template-system-audit/template-decisions.json')



def manifest_entries(content: str) -> dict[str, str]:
    entries = {}
    for raw in content.splitlines():
        row = raw.strip()
        if not row or row.startswith('#') or '=' not in row:
            continue
        key, value = (v.strip() for v in row.split('=', 1))
        if key in entries:
            raise ValueError('Duplicate manifest key: ' + key)
        entries[key] = value
    return entries


def enum_names(text: str) -> list[str]:
    body = re.search(r'public enum TemplatesEnum\s*\{(.*?);', text, re.S)
    if not body:
        raise ValueError('Template enum not found')
    return re.findall(r'\b([A-Z][A-Z0-9_]+)\s*(?=,|$)', body.group(1))


def png_details(data: bytes) -> tuple[int, int] | None:
    if len(data) < 33 or not data.startswith(PNG_SIG) or data[12:16] != b'IHDR':
        return None
    width, height = struct.unpack('>II', data[16:24])
    if width <= 0 or height <= 0 or width > 8000 or height > 8000:
        return None
    return width, height


def git_upstream(root: Path, ref: str) -> dict[str, str]:
    result = subprocess.run(['git', '-C', str(root), 'ls-tree', '-rz', ref,
                             '--', str(ASSETS)], capture_output=True, check=True)
    output = {}
    for entry in result.stdout.split(b'\0'):
        if not entry:
            continue
        info, path = entry.decode('utf-8').split('\t', 1)
        if path.endswith('.png'):
            output[path] = info.split()[2]
    return output


def git_hash(data: bytes) -> str:
    return hashlib.sha1(('blob ' + str(len(data))).encode() + b'\0' + data).hexdigest()


def production_uses(root: Path) -> dict[str, list[str]]:
    uses = defaultdict(set)
    for p in (root / 'modules').glob('*/src/main/java/**/*.java'):
        if p == root / ENUM or p.name == 'EmulatorRecognitionPolicy.java':
            # Policy declares rejected template IDs. These references are not
            # an executed OpenCV lookup and must not be counted as hot paths.
            continue
        for key in REFERENCE.findall(p.read_text(encoding='utf-8', errors='replace')):
            uses[key].add(p.stem)
    return {k: sorted(v) for k,v in uses.items()}


def coach_observations(evidence: Path) -> tuple[dict, int, int]:
    observed = defaultdict(lambda: {'checks': 0, 'hits': 0, 'sessions': set(),
                                    'tasks': set(), 'max_miss_score': 0.0})
    sessions = set()
    for source in sorted(evidence.glob('*/*/recognition-events.csv')) if evidence.is_dir() else []:
        run = source.parent / 'run.txt'
        task = next((line[5:] for line in run.read_text(errors='replace').splitlines()
                     if line.startswith('task=')), 'UNKNOWN') if run.exists() else 'UNKNOWN'
        sessions.add(source.parent.name)
        with source.open(newline='', encoding='utf-8-sig') as stream:
            for row in csv.DictReader(stream):
                key = row.get('template', '').strip()
                if not re.fullmatch(r'[A-Z][A-Z0-9_]+', key):
                    continue
                stat = observed[key]
                stat['checks'] += 1
                hit = str(row.get('hit', '')).lower() == 'true'
                stat['hits'] += int(hit)
                stat['sessions'].add(source.parent.name)
                stat['tasks'].add(task)
                if not hit:
                    try: stat['max_miss_score'] = max(stat['max_miss_score'], float(row.get('score',0)))
                    except ValueError: pass
    for stat in observed.values():
        stat['sessions'] = len(stat['sessions'])
        stat['tasks'] = sorted(stat['tasks'])
        stat['max_miss_score'] = round(stat['max_miss_score'],2)
    return dict(observed), len(sessions), sum(a['checks'] for a in observed.values())


def task_method_profiles(evidence: Path) -> dict:
    """Count methods per coached *task*, not per emulator or game account.

    These are recognition observations, not three emulators, and not proof
    that a template was visible. The profiles guide safe before/after runs.
    """
    out=defaultdict(lambda:{'sessions':set(),'observations':0,
                            'method_counts':defaultdict(int),
                            'templates':defaultdict(lambda:{'checks':0,'hits':0})})
    if not evidence.is_dir(): return {}
    for path in evidence.glob('*/*/recognition-events.csv'):
        run=path.parent/'run.txt'
        task=next((line[5:].strip() for line in run.read_text(errors='replace').splitlines()
                   if line.startswith('task=')), 'UNKNOWN') if run.exists() else 'UNKNOWN'
        if not re.fullmatch(r'[A-Z][A-Z0-9_]+',task):task='UNKNOWN'
        profile=out[task]
        profile['sessions'].add(path.parent.name)
        with path.open(newline='',encoding='utf-8-sig') as source:
            for item in csv.DictReader(source):
                name=item.get('template','')
                method=item.get('method','')
                if not re.fullmatch(r'[A-Z][A-Z0-9_]+',name):continue
                if method not in {'color','mono','multiscale','multi','multi-mono'}:continue
                profile['observations']+=1
                profile['method_counts'][method]+=1
                sample=profile['templates'][name]
                sample['checks']+=1
                sample['hits']+=item.get('hit','').lower()=='true'
    return {task:{'sessions':len(data['sessions']),
                  'observations':data['observations'],
                  'method_counts':dict(data['method_counts']),
                  'most_checked':[
                      dict(template=name,**stats)
                      for name,stats in sorted(data['templates'].items(),
                          key=lambda p:-p[1]['checks'])[:12]]}
            for task,data in sorted(out.items())}


def audit(root: Path, evidence: Path, upstream: str='upstream/main') -> dict:
    mapping = manifest_entries((root/MANIFEST).read_text(encoding='utf-8'))
    names = enum_names((root/ENUM).read_text(encoding='utf-8'))
    uses = production_uses(root)
    decisions=json.loads((root/DECISIONS).read_text(encoding='utf-8'))
    retired=decisions['retired']
    protected=decisions['protected']
    originals = git_upstream(root, upstream)
    sightings, session_count, checks = coach_observations(evidence)
    entries=[]
    by_path=defaultdict(list)
    by_sha=defaultdict(list)
    for key in names:
        ref = mapping.get(key, '/templates/' + key.lower() + '.png')
        by_path[ref].append(key)
        absolute = root / 'modules/vision/src/main/resources' / ref.lstrip('/')
        source_key = absolute.relative_to(root).as_posix()
        data = absolute.read_bytes() if absolute.is_file() else None
        dims = png_details(data) if data is not None else None
        digest = git_hash(data) if data is not None else ''
        if data and digest: by_sha[digest].append(key)
        baseline = ('new' if source_key not in originals else
                    'original_unchanged' if digest == originals[source_key] else 'modified')
        obs = sightings.get(key, {'checks':0, 'hits':0, 'sessions':0,'tasks':[],'max_miss_score':0})
        state = ('missing_asset' if data is None else 'invalid_png' if dims is None else
                 'observed_hit' if obs['hits'] else 'review_observed_misses' if obs['checks'] else
                 'not_observed')
        role = ('retired' if key in retired else
                'protected_guard' if key in protected else
                'active' if uses.get(key) else 'registered_unused')
        entries.append(dict(name=key, area=ref.split('/')[2] if ref.count('/') >= 3 else 'root',
                            resource=ref, status=state, upstream=baseline,
                            dimensions=list(dims) if dims else None,
                            size=len(data) if data else 0,
                            source_uses=uses.get(key,[]), runtime_role=role,
                            decision_note=retired.get(key,protected.get(key,'')),
                            observations=obs))
    unknown=set(retired)|set(protected)
    unknown.difference_update(names)
    if unknown:raise ValueError('Unknown decision template keys: '+','.join(sorted(unknown)))
    for key in retired:
        if uses.get(key):
            raise ValueError('Retired template has become active again: '+key)
    for e in entries:
        e['shared_with'] = [k for k in by_path[e['resource']] if k != e['name']]
    all_assets={p.relative_to(root).as_posix() for p in (root/ASSETS).rglob('*.png')}
    registered={('modules/vision/src/main/resources'+e['resource']) for e in entries}
    extras=sorted(all_assets - registered)
    mapped_names={e['name'] for e in entries}
    result={
        'schema':1,'generated_utc':datetime.now(timezone.utc).isoformat(timespec='seconds'),
        'comparison_ref':upstream,
        'summary':{
            'registered':len(entries), 'manifest_entries':len(mapping),
            'physical_png_assets':len(all_assets),
            'missing':sum(e['status']=='missing_asset' for e in entries),
            'invalid':sum(e['status']=='invalid_png' for e in entries),
            'registered_unchanged_original':sum(e['upstream']=='original_unchanged' and e['dimensions'] is not None for e in entries),
            'registered_modified':sum(e['upstream']=='modified' and e['dimensions'] is not None for e in entries),
            'registered_new':sum(e['upstream']=='new' and e['dimensions'] is not None for e in entries),
            'extra_png_files':len(extras),'unreferenced_manifest_keys':len(set(mapping)-set(names)),
            'shared_resource_alias_groups':sum(len(v)>1 for v in by_path.values()),
            'source_unused':sum(not e['source_uses'] for e in entries),
            'retired_from_runtime':sum(e['runtime_role']=='retired' for e in entries),
            'protected_safety_guards':sum(e['runtime_role']=='protected_guard' for e in entries),
            'active_source_referenced':sum(e['runtime_role']=='active' for e in entries),
            'registered_unused_review':sum(e['runtime_role']=='registered_unused' for e in entries),
            'coach_sessions':session_count,'coach_checks':checks,
            'coach_templates_seen':len(set(sightings)&mapped_names),
            'templates_with_positive_hits':sum(e['observations']['hits']>0 for e in entries),
            'templates_only_observed_misses':sum(e['observations']['checks']>0 and e['observations']['hits']==0 for e in entries),
        },
        'unmapped_manifest_keys':sorted(set(mapping)-set(names)),
        'extra_assets':extras,
        'shared_resource_paths':[{ 'resource':k, 'keys':v } for k,v in by_path.items() if len(v)>1],
        'duplicate_contents':[{ 'keys':v } for v in by_sha.values() if len(v)>1],
        'task_recognition_profiles':task_method_profiles(evidence),
        'templates':sorted(entries, key=lambda v:v['name'])
    }
    return result


def markdown(result: dict) -> str:
    s=result['summary']; lines=[
        '# SurvivalOps complete template baseline','',
        'Read-only audit. No screenshot, account data or credentials are included. A missing match is not proof of a broken template.', '',
        f"- Registered template constants: **{s['registered']}**; image files: **{s['physical_png_assets']}**.",
        f"- Missing PNG: **{s['missing']}**; invalid PNG: **{s['invalid']}**; extra unregistered PNGs: **{s['extra_png_files']}**.",
        f"- Upstream comparison: {s['registered_unchanged_original']} unchanged, {s['registered_modified']} modified, {s['registered_new']} new referenced images.",
        f"- Source-unused constants (direct Java enum references only): **{s['source_unused']}**. May be dynamically used.",
        f"- Confirmed retired from live runtime: **{s['retired_from_runtime']}**; safety guard exceptions **{s['protected_safety_guards']}**; remaining unreferenced under review **{s['registered_unused_review']}**.",
        f"- Real Task Coach sample: **{s['coach_sessions']} sessions**, **{s['coach_checks']} method observations**; {s['templates_with_positive_hits']} template keys with hits; {s['templates_only_observed_misses']} only misses. Most templates have NO runtime evidence.",
        '', '## Missing assets', '' ]
    for e in result['templates']:
        if e['status'] in ('missing_asset','invalid_png'):
            lines.append(f"- `{e['name']}` — `{e['resource']}` (used by: {', '.join(e['source_uses']) or 'no direct Java references'})")
    lines += ['', '## Deliberately retired active probes (image assets retained)', '']
    for e in result['templates']:
        if e['runtime_role']=='retired':
            lines.append(f"- `{e['name']}` — {e['decision_note']}")
    lines += ['', '## Observed without positive match (review, NOT proved broken)', '']
    seen=sorted((e for e in result['templates'] if e['status']=='review_observed_misses'),
                key=lambda e:-e['observations']['checks'])
    for e in seen:
        obs=e['observations']
        lines.append(f"- `{e['name']}` — {obs['checks']} observations in {obs['sessions']} sessions; peak non-match {obs['max_miss_score']}%; task(s): {', '.join(obs['tasks'])}")
    lines += ['', '## Per-task recognition method counts (not separate emulators)', '']
    for task,stats in result.get('task_recognition_profiles',{}).items():
        modes=', '.join(f'{method}={count}' for method,count in stats['method_counts'].items())
        lines.append(f"- `{task}`: {stats['sessions']} sessions, {stats['observations']} checks; {modes}")
    lines += ['', '## Next action', '',
              'Review only templates for which the expected UI was definitely visible, using cropped screen-specific evidence; write an actual before/after acceptance test. Do not globally lower thresholds or auto-promote Task Coach proposals.', '']
    return '\n'.join(lines)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--root',type=Path,default=ROOT,help='Local SurvivalOps source repository')
    parser.add_argument('--evidence',type=Path,default=Path('/nonexistent'))
    parser.add_argument('--output',type=Path,help='Private directory for report.json and report.md')
    parser.add_argument('--upstream',default='upstream/main')
    args=parser.parse_args()
    result=audit(args.root.resolve(),args.evidence,args.upstream)
    if args.output:
        args.output.mkdir(parents=True,exist_ok=True,mode=0o700)
        for name, body in (('report.json', json.dumps(result,ensure_ascii=False,indent=2)+'\n'),
                           ('report.md', markdown(result))):
            # The dashboard can read at any moment; never expose half a JSON file.
            pending = args.output / (name + '.tmp')
            pending.write_text(body, encoding='utf-8')
            pending.chmod(0o600)
            os.replace(pending, args.output / name)
    print(json.dumps(result['summary'],indent=2))

if __name__=='__main__':main()
