#!/usr/bin/env python3
"""Read-only cross-task triage from latest Task Coach and shared QA records.

A successful scheduler outcome or pattern hit is not in-game completion.
This report never changes task status, evidence, templates or game state.
"""
from __future__ import annotations
import argparse
from collections import Counter,defaultdict
import csv
from datetime import datetime,timezone
import json
import os
from pathlib import Path
import re
import sqlite3

_source_root=Path(__file__).resolve().parents[2]
ROOT=Path(os.environ.get('SURVIVALOPS_REPO',
    str(_source_root if (_source_root/'modules').is_dir() else Path.home()/'projects/WOS-Botter')))
CATALOG=ROOT/'tools/task-dashboard/catalog.json'
DEFAULT_COACH=Path.home()/'survivalops-diagnostics/taskcoach'
DEFAULT_QA=Path.home()/'.local/share/survivalops-qa/dashboard.sqlite3'
DEFAULT_REPORT=Path.home()/'.local/share/survivalops-autoqa/task-audit.json'
PROTECTED_GUARDS={'GAME_HOME_RECONNECT','GAME_START_WELCOME_BACK_TITLE',
 'GAME_START_MANDATORY_UPDATE_TITLE','GAME_QUIT_CONFIRMATION_PROMPT',
 'SHARED_REWARDS_BANNER_CURRENT','SERVE_EMU_OFFER_RESPLENDENT_TITLE_2026'}
SESSION_RE=re.compile(r'^20\d{6}-\d{6}-[a-z0-9]{5,12}$')
TASK_RE=re.compile(r'^[A-Z][A-Z0-9_]{1,79}$')


def _parse_kv(path):
    if not path.is_file() or path.stat().st_size>300_000:return {}
    result={}
    for line in path.read_text(errors='replace').splitlines():
        if '=' in line:
            key,value=line.split('=',1)
            result[key.strip()]=value.strip()
    return result


def _safe_int(value):
    try:return int(value)
    except (ValueError,TypeError):return 0


def coach_runs(coach_root:Path):
    for day in sorted(coach_root.glob('20??-??-??')) if coach_root.is_dir() else []:
        if not day.is_dir():continue
        for session in sorted(day.iterdir()):
            if not session.is_dir() or not SESSION_RE.fullmatch(session.name):continue
            info=_parse_kv(session/'run.txt')
            task=info.get('task','')
            if not TASK_RE.fullmatch(task):continue
            csv_path=session/'recognition-events.csv'
            if not csv_path.is_file() or csv_path.stat().st_size>4_000_000:continue
            methods=Counter();per_template=defaultdict(lambda:{'checks':0,'hits':0,'max_miss':0.0})
            stamps=[];checks=0;hits=0
            with csv_path.open(newline='',encoding='utf-8-sig',errors='replace') as handle:
                for row in csv.DictReader(handle):
                    template=row.get('template','')
                    if not TASK_RE.fullmatch(template):continue
                    hit=str(row.get('hit','')).lower()=='true'
                    method=(row.get('method') or 'unknown')[:32]
                    methods[method]+=1
                    stats=per_template[template];stats['checks']+=1;stats['hits']+=int(hit)
                    if not hit:
                        try:stats['max_miss']=max(stats['max_miss'],float(row.get('score') or 0))
                        except ValueError:pass
                    checks+=1;hits+=int(hit)
                    stamp=(row.get('time_utc') or '').strip()
                    if stamp:stamps.append(stamp)
            summary=(session/'summary.txt').read_text(errors='replace') if (session/'summary.txt').is_file() else ''
            outcome=re.search(r'scheduler_outcome=([A-Z_]+)',summary)
            gaps=[]
            try:
                sequence=[datetime.fromisoformat(t.replace('Z','+00:00')) for t in stamps]
                gaps=[(b-a).total_seconds() for a,b in zip(sequence,sequence[1:]) if b>=a]
            except ValueError:pass
            yield {'session':session.name,'task':task,'started_at':info.get('started',''),
                   'scheduler_outcome':outcome.group(1) if outcome else 'UNKNOWN',
                   'checks':checks,'hits':hits,'methods':dict(methods),
                   'largest_observation_gap_s':round(max(gaps,default=0),2),
                   'templates':per_template}


def generate(coach_root=DEFAULT_COACH,qa_db=DEFAULT_QA,catalog=CATALOG)->dict:
    items=json.loads(Path(catalog).read_text())
    table={t['key']:{'task':t['key'],'label':t['label'],'factory':t.get('factory'),
            'qa_status':'not_tested','open_issues':0,'priority':0,
            'coach_sessions':0,'coach_checks':0,'coach_hits':0,'coach_success_claims':0,
            'largest_gap_s':0,'recent_coach':None,
            'last_scheduler_outcome':None,'slow_or_repeated_templates':[],
            '_templates':defaultdict(lambda:{'checks':0,'hits':0,'max_miss':0.0})}
            for t in items if TASK_RE.fullmatch(t.get('key',''))}
    if Path(qa_db).is_file():
        with sqlite3.connect(f'file:{qa_db}?mode=ro',uri=True) as db:
            for key,status in db.execute('select key,status from tasks'):
                if key in table:table[key]['qa_status']=status
            for key,n in db.execute('select task_key,count(*) from issues where state="open" group by task_key'):
                if key in table:table[key]['open_issues']=n
    runs=0
    for evidence in coach_runs(Path(coach_root)):
        name=evidence['task']
        if name not in table:continue
        runs+=1
        row=table[name]
        row['coach_sessions']+=1;row['coach_checks']+=evidence['checks'];row['coach_hits']+=evidence['hits']
        if evidence['scheduler_outcome']=='SUCCEEDED':row['coach_success_claims']+=1
        row['largest_gap_s']=max(row['largest_gap_s'],evidence['largest_observation_gap_s'])
        if row['recent_coach'] is None or evidence['session']>row['recent_coach']:
            row['recent_coach']=evidence['session'];row['last_scheduler_outcome']=evidence['scheduler_outcome']
        for name,stats in evidence['templates'].items():
            total=row['_templates'][name];total['checks']+=stats['checks'];total['hits']+=stats['hits']
            total['max_miss']=max(total['max_miss'],stats['max_miss'])
    for row in table.values():
        stats=row.pop('_templates')
        # Prioritize expensive misses for *review*, never auto-disable optional
        # rewards/guard buttons solely because no positive hit was observed.
        repeated=sorted(((n,v) for n,v in stats.items() if v['checks']>=4 and n not in PROTECTED_GUARDS),
                        key=lambda v:(-(v[1]['checks']-v[1]['hits']),-v[1]['checks'],v[0]))
        row['slow_or_repeated_templates']=[
            {'template':name,'checks':x['checks'],'hits':x['hits'],
             'misses':x['checks']-x['hits'],'max_miss_score':round(x['max_miss'],2),
             'protected_guard':name in PROTECTED_GUARDS}
             for name,x in repeated[:8]]
        row['priority']=({ 'broken':60,'blocked':45,'testing':30,'not_tested':20,'working':10,'verified':0}
                         .get(row['qa_status'],10)+row['open_issues']*20+
                         min(row['coach_checks']//25,20))
        row['assessment']=('needs_live_test' if not row['coach_sessions'] else
                           'needs_in_game_postcondition_verification')
        row['completion_proved']=False
    ordered=sorted(table.values(),key=lambda x:(-x['priority'],x['task']))
    return {'schema':1,'generated_at':datetime.now(timezone.utc).isoformat(),
            'total_tasks':len(ordered),'coach_sessions':runs,'tasks_with_evidence':sum(x['coach_sessions']>0 for x in ordered),
            'total_checks':sum(x['coach_checks'] for x in ordered),
            'note':'Scheduler SUCCEEDED and high template scores are not proof of in-game completion or QA verification.',
            'tasks':ordered}


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--coach-root',type=Path,default=DEFAULT_COACH)
    parser.add_argument('--qa-db',type=Path,default=DEFAULT_QA)
    parser.add_argument('--out',type=Path,default=DEFAULT_REPORT)
    args=parser.parse_args()
    data=generate(args.coach_root,args.qa_db)
    args.out.parent.mkdir(parents=True,exist_ok=True)
    temp=args.out.with_suffix('.tmp')
    temp.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')
    os.chmod(temp,0o600)
    temp.replace(args.out)
    print('Audited',data['total_tasks'],'tasks; Coach runs',data['coach_sessions'],
          'tasks with evidence',data['tasks_with_evidence'],'checks',data['total_checks'])

if __name__=='__main__':main()
