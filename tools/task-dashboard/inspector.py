"""Authenticated template, Task Coach, Java-flow and OCR inspection.

All image paths are resolved only from a known audit/template manifest or
existing run captures. Operator review markers never remove production files.
"""
from __future__ import annotations
from collections import Counter,defaultdict
import csv
from datetime import datetime,timezone
import json
import os
from pathlib import Path
import re

HOME=Path.home()
_SOURCE_REPO=Path(__file__).resolve().parents[2]
REPO=Path(os.environ.get('SURVIVALOPS_REPO',
    str(_SOURCE_REPO if (_SOURCE_REPO/'modules').is_dir() else HOME/'projects/WOS-Botter')))
# CI uses the same read-only audit output as the QA dashboard, without
# assuming that the test runner has the maintainer's private home directory.
AUDIT=Path(os.environ.get('SURVIVALOPS_TEMPLATE_AUDIT_REPORT',
    str(HOME/'.local/share/survivalops-template-audit/report.json')))
COACH=HOME/'survivalops-diagnostics/taskcoach'
INBOX=HOME/'survivalops-diagnostics/inbox'
DB=HOME/'.local/share/survivalops-qa/template-review.json'
CATALOG=REPO/'tools/task-dashboard/catalog.json'
NAME=re.compile(r'^[A-Z][A-Z0-9_]{1,79}$')
SESSION=re.compile(r'^20\d{6}-\d{6}-[a-z0-9]{5,12}$')
FRAME=re.compile(r'^\d{3}-[a-z0-9_-]{3,100}\.jpg$')


def inventory():
    if not AUDIT.is_file() or AUDIT.stat().st_size>3_000_000:return None
    data=json.loads(AUDIT.read_text())
    return {x['name']:x for x in data.get('templates',[]) if NAME.fullmatch(x.get('name',''))}


def review():
    try:
        if DB.is_file() and DB.stat().st_size<150_000:
            value=json.loads(DB.read_text())
            return {k:v for k,v in value.items() if NAME.fullmatch(k) and v in ('hidden','removal_review')}
    except (OSError,ValueError):pass
    return {}


def review_update(name,action):
    entries=inventory()
    if not entries or name not in entries or action not in ('hidden','removal_review','restore'):
        raise ValueError('unknown template or action')
    item=entries[name]
    if action=='removal_review' and item['runtime_role']=='protected_guard':
        raise ValueError('protected safety guard cannot be retired from this view')
    data=review()
    if action=='restore':data.pop(name,None)
    else:data[name]=action
    DB.parent.mkdir(parents=True,exist_ok=True)
    pending=DB.with_suffix('.tmp')
    pending.write_text(json.dumps(data,ensure_ascii=False,indent=2))
    pending.chmod(0o600);pending.replace(DB)
    return {'template':name,'review_state':data.get(name,'active_in_catalog'),
            'production_unchanged':True}


def template_png(name):
    if not NAME.fullmatch(name):return None
    entry=(inventory() or {}).get(name)
    if not entry or not entry.get('resource','').startswith('/templates/'):
        return None
    target=(REPO/'modules/vision/src/main/resources'/entry['resource'].lstrip('/')).resolve()
    base=(REPO/'modules/vision/src/main/resources/templates').resolve()
    if not target.is_relative_to(base) or not target.is_file() or target.stat().st_size>2_000_000:
        return None
    data=target.read_bytes()
    return data if data.startswith(b'\x89PNG\r\n\x1a\n') else None


def unregistered_assets():
    if not AUDIT.is_file():return []
    try:
        items=json.loads(AUDIT.read_text()).get('extra_assets',[])
        return [x for x in items if isinstance(x,str) and x.startswith('modules/vision/src/main/resources/templates/')][:160]
    except (ValueError,OSError):return []


def extra_png(resource):
    if resource not in unregistered_assets():return None
    base=(REPO/'modules/vision/src/main/resources/templates').resolve()
    image=(REPO/resource).resolve()
    if not image.is_relative_to(base) or not image.is_file() or image.stat().st_size>2_000_000:return None
    data=image.read_bytes()
    return data if data.startswith(b'\x89PNG\r\n\x1a\n') else None


def sessions(task=None,limit=60):
    if task and not NAME.fullmatch(task):return []
    rows=[]
    if not COACH.is_dir():return rows
    for run in COACH.glob('20??-??-??/*'):
        if not run.is_dir() or not SESSION.fullmatch(run.name):continue
        metadata=run/'run.txt';events=run/'recognition-events.csv'
        if not metadata.is_file() or not events.is_file():continue
        text=metadata.read_text(errors='replace')[:1000]
        found=re.search(r'^task=([A-Z][A-Z0-9_]+)$',text,re.M)
        if not found or task and found.group(1)!=task:continue
        rows.append({'id':run.name,'task':found.group(1),
                     'started':next((x.removeprefix('started=') for x in text.splitlines() if x.startswith('started=')),''),
                     'capture_count':len(list((run/'captures').glob('*.jpg'))) if (run/'captures').is_dir() else 0})
    return sorted(rows,key=lambda x:x['id'],reverse=True)[:limit]


def run_dir(run_id):
    if not SESSION.fullmatch(run_id):return None
    day=run_id[:8]
    candidate=COACH/f'{day[:4]}-{day[4:6]}-{day[6:]}'/run_id
    return candidate if candidate.is_dir() and (candidate/'run.txt').is_file() else None


def coach_image(run_id,name):
    root=run_dir(run_id)
    if root is None or not FRAME.fullmatch(name):return None
    path=root/'captures'/name
    if not path.is_file() or path.stat().st_size>3_000_000:return None
    data=path.read_bytes()
    return data if data.startswith(b'\xff\xd8') else None


def run_detail(run_id):
    root=run_dir(run_id)
    if root is None:return None
    path=root/'recognition-events.csv'
    if not path.is_file() or path.stat().st_size>5_000_000:return None
    records=[];totals=defaultdict(lambda:{'checks':0,'hits':0,'max_score':0,'methods':Counter()})
    with path.open(newline='',encoding='utf-8-sig',errors='replace') as handle:
        for row in csv.DictReader(handle):
            key=row.get('template','')
            if not NAME.fullmatch(key):continue
            hit=row.get('hit','').lower()=='true'
            try:score=float(row.get('score') or '0')
            except ValueError:score=0
            method=str(row.get('method') or 'unknown')[:32]
            total=totals[key];total['checks']+=1;total['hits']+=int(hit)
            total['max_score']=max(total['max_score'],score);total['methods'][method]+=1
            records.append({'time':row.get('time_utc','')[:35],'template':key,
                'method':method,'score':round(score,2),'hit':hit,
                'x':row.get('x',''),'y':row.get('y','')})
            if len(records)>=1500:break
    captures=[]
    for file in sorted((root/'captures').glob('*.jpg')) if (root/'captures').is_dir() else []:
        if FRAME.fullmatch(file.name):captures.append(file.name)
    summary=(root/'summary.txt').read_text(errors='replace')[:9000] if (root/'summary.txt').is_file() else ''
    sched=re.search(r'scheduler_outcome=([A-Z_]+)',summary)
    return {'id':run_id,'task':next((x['task'] for x in sessions() if x['id']==run_id),''),
        'scheduler_outcome':sched.group(1) if sched else 'UNKNOWN',
        'completion_proved':False,'records':records,'captures':captures[:100],
        'templates':[{'name':k,'checks':v['checks'],'hits':v['hits'],
            'max_score':round(v['max_score'],2),'methods':dict(v['methods'])}
            for k,v in sorted(totals.items(),key=lambda x:(-x[1]['checks'],x[0]))]}


def java_flow(task):
    if not NAME.fullmatch(task):return None
    tasks={row['key']:row for row in json.loads(CATALOG.read_text())}
    item=tasks.get(task)
    if not item:return None
    factory=item['factory']
    sources=[p for p in (REPO/'modules/tasks/src/main/java').rglob(factory+'.java')]
    if not sources:return {'task':task,'label':item['label'],'source_found':False,'steps':[]}
    path=sources[0];code=path.read_text(errors='replace')
    match=re.search(r'\b(?:protected|public)\s+void\s+execute\s*\(\s*\)\s*\{',code)
    steps=[]
    if match:
        begin=match.end();depth=1;end=begin
        while end<len(code) and depth:
            if code[end]=='{':depth+=1
            elif code[end]=='}':depth-=1
            end+=1
        body=code[begin:end-1]
        # Direct method calls in actual execution order. This is an extracted
        # source outline, not a guarantee of which conditional path ran.
        skip={'logInfo','logWarning','logDebug','reschedule','markWorkIncomplete','plusMinutes','now','stream','anyMatch','isEmpty'}
        for line in body.splitlines():
            stripped=line.strip()
            if stripped.startswith('//') or stripped.startswith('*'):continue
            for call in re.findall(r'(?<![.\w])([a-z][A-Za-z0-9]+)\s*\(',stripped):
                if call in skip or call in ('if','for','while','switch','return','catch'):continue
                if call not in [x['method'] for x in steps]:
                    steps.append({'method':call,'conditional':bool(re.search(r'\b(if|else|return|for)\b',stripped)),
                        'line':code[:begin].count('\n')+body[:body.find(line)].count('\n')+2})
    references=sorted(set(re.findall(r'\bTemplatesEnum\.([A-Z][A-Z0-9_]+)\b|\b([A-Z][A-Z0-9_]{3,})\b',code)))
    keys=sorted({a or b for a,b in references if a or b})
    listed=inventory() or {}
    keys=[key for key in keys if key in listed][:130]
    annotated=[]
    if task=='TRAINING_TROOPS':
        annotated=[
          {'label':'Configuratie en ingeschakelde typen','method':'loadConfiguration','condition':'Welke troepentypen en promoties zijn toegestaan?'},
          {'label':'Beschikbare wachtrijen bepalen','method':'buildQueuesListFlow','condition':'Geen geselecteerde wachtrij → stoppen'},
          {'label':'Elke wachtrij inspecteren','method':'inspectAllQueues','condition':'IDLE / TRAINING / COMPLETE / UPGRADING / UNKNOWN'},
          {'label':'Afgeronde troepen ophalen','method':'collectCompletedTraining','condition':'Alleen bij COMPLETE; claim niet bevestigd → nieuwe poging plannen en stoppen'},
          {'label':'Bestaande training en tijd controleren','method':'manageSoonReadyQueue','condition':'Bij bijna gereed wachten, geen nieuwe opdracht'},
          {'label':'Trainbare wachtrijen selecteren','method':'filterReadyQueuesFlow','condition':'Bij bezet of upgrade: veilig overslaan en opnieuw plannen'},
          {'label':'Ministerie en reserveringen controleren','method':'refreshMinistryAppointmentIfNeeded','condition':'Eventuele beperkingen en afspraak beschermen'},
          {'label':'Klaartijden vastleggen','method':'extractExistingCompletionTimesFlow','condition':'Gelezen via OCR waar nodig; mislukte OCR is niet automatisch een mislukte taak'},
          {'label':'Hoogst beschikbare troepen trainen','method':'trainAllReadyQueuesFlow','condition':'Selectie, Train-knop en voorwaarden per wachtrij'},
          {'label':'Terug naar Home en wachtrijen opnieuw lezen','method':'ensureHomeAfterTrainingPanel','condition':'Onbekend scherm niet als succes behandelen'},
          {'label':'Bevestigen dat IDLE → TRAINING veranderde','method':'hasConfirmedTrainingProgress','condition':'Geen overgang → INCOMPLETE; niet automatisch succesvol'},
          {'label':'Volgende controle plannen','method':'deferToEarliestCompletion','condition':'Tijd van de eerstvolgende afloop'}]
    return {'task':task,'label':item['label'],'factory':factory,'source':str(path.relative_to(REPO)),
            'annotated_stages':annotated,
            'source_found':True,'steps':steps[:75],
            'templates':keys,'description':'Methoden uit execute(); vertakkingen zijn conditioneel. Niet iedere stap wordt elke run uitgevoerd.'}


def ocr_health(limit_files=130):
    counts=Counter();samples=[]
    if INBOX.is_dir():
        paths=sorted(INBOX.glob('20??-??-??/*.json'),reverse=True)[:limit_files]
        for path in paths:
            try:
                if path.stat().st_size>170_000:continue
                raw=json.loads(path.read_text()).get('log_tail','')
            except (OSError,ValueError):continue
            if not isinstance(raw,str):continue
            for line in raw.splitlines():
                if 'OCR not accepted:' in line:
                    status=re.search(r'status=(\w+)',line)
                    typ=status.group(1) if status else 'UNKNOWN'
                    failure=re.search(r'transportFailure=([^\s]+)',line)
                    fail=failure.group(1) if failure else 'unknown'
                    category=('empty_text' if typ=='FAILED' and fail=='none' else
                              'capture_or_transport_error' if fail!='none' and
                              ('screencap' in line.lower() or 'capture' in line.lower() or 'adb' in line.lower()) else
                              'engine_exception' if fail!='none' else 'text_rejected')
                    counts[category]+=1
                    if len(samples)<12:samples.append({'category':category,'status':typ,'failure':fail,'source_day':path.parent.name})
                elif 'OCR trial ' in line and ' failed:' in line:
                    counts['trial_exceptions']+=1
                    if 'ADB screencap failed' in line:counts['adb_screencap_failures']+=1
    files=list((REPO/'tools/tesseract').glob('*.traineddata'))
    return {'counts':dict(counts),'samples':samples,'log_scope':'Recent uploaded diagnostics; log tails can overlap, counts are not unique OCR calls.',
            'bundled_languages':[p.stem for p in files],
            'windows_runtime_tested':False,'source':'ResilientOcrExecutor: FAILED=blank text unless exception; REJECTED=text not accepted; exception=engine/capture failure.'}
