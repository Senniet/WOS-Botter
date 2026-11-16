#!/usr/bin/env python3
"""SurvivalOps Serve-EMU QA controller, Python stdlib + system ImageMagick.

One worker, one pinned AVD, no automatic spending, no filesystem code execution,
no automatic QA signoff. Default workflows are OBSERVE ONLY. A per-task visual
workflow can later be enabled explicitly after both pre/postcondition tests.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import fcntl
import hashlib
import io
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile
import time
import recognition
import proposals
import daily

HOME=Path.home()
DATA=Path(os.environ.get('SURVIVALOPS_AUTOQA_DATA',HOME/'.local/share/survivalops-autoqa'))
_repo_root=Path(__file__).resolve().parents[2]
REPO=Path(os.environ.get('SURVIVALOPS_REPO',
    str(_repo_root if (_repo_root/'modules').is_dir() else HOME/'projects/WOS-Botter')))
CATALOG=REPO/'tools/task-dashboard/catalog.json'
HOST='truenas_admin@192.168.1.20'
SSH_KEY=HOME/'.ssh/androidwoslab_deploy'
CONTAINER='ix-serve-emu-serve-emu-1'
SERIAL=re.compile(r'^emulator-[0-9]{4,5}$')
TASK=re.compile(r'^[A-Z][A-Z0-9_]{1,79}$')
ID=re.compile(r'^[0-9a-f]{16}$')
DEFAULT_TIMEOUT=18
MAX_PNG=6_000_000


def stamp():return datetime.now(timezone.utc).isoformat(timespec='seconds').replace('+00:00','Z')


def db():
    DATA.mkdir(parents=True,exist_ok=True)
    c=sqlite3.connect(DATA/'runs.sqlite3',timeout=10)
    c.row_factory=sqlite3.Row
    c.execute('pragma busy_timeout=10000')
    c.executescript('''
      create table if not exists runs(
        id text primary key,task text not null,mode text not null,
        state text not null,created_at text not null,started_at text,
        ended_at text,reason text not null default '',device text not null,
        checks integer not null default 0,actions integer not null default 0,
        duration_ms integer not null default 0
      );
      create index if not exists runs_recent on runs(created_at desc);
    ''')
    return c


def configuration():
    path=DATA/'config.json'
    if not path.is_file():return {'bound':None,'mode':'observe','allow_unreviewed_actions':False}
    return json.loads(path.read_text())


def paused()->bool:
    return bool(configuration().get('paused',False))


def require_active()->None:
    if paused():raise RuntimeError('Controller paused by operator; refusing next game action')


def pause(value:bool)->dict:
    # A single stop also applies to an already executing workflow because
    # every actual game tap is guarded immediately before ADB invocation.
    config=configuration();config['paused']=bool(value)
    save_config(config)
    return {'paused':bool(value),'game_actions':0}


def save_config(config):
    DATA.mkdir(parents=True,exist_ok=True)
    dest=DATA/'config.json'
    tmp=DATA/'config.tmp'
    tmp.write_text(json.dumps(config,indent=2))
    os.chmod(tmp,0o600)
    tmp.replace(dest)


def ssh(args: list[str],timeout=DEFAULT_TIMEOUT)->bytes:
    if any(not isinstance(x,str) for x in args):raise ValueError('Invalid SSH argument')
    # Reuse the SSH transport for successive ADB screenshots. A new SSH/TLS
    # handshake on every recognition frame was wasting time. UNIX socket is
    # restricted to the service's own user; no connection to other servers.
    mux_dir=HOME/'.cache/survivalops-autoqa-ssh'
    mux_dir.mkdir(mode=0o700,parents=True,exist_ok=True)
    os.chmod(mux_dir,0o700)
    cmd=['/usr/bin/ssh','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes',
         '-o','ConnectTimeout=5','-o','ControlMaster=auto',
         '-o','ControlPersist=50s','-o','ControlPath='+str(mux_dir/'mux-%r@%h-%p'),
         '-i',str(SSH_KEY),HOST,*args]
    result=subprocess.run(cmd,capture_output=True,timeout=timeout,check=False)
    if result.returncode:
        raise RuntimeError('Remote command error: '+result.stderr.decode('utf-8','replace')[-250:])
    return result.stdout


def adb(*args: str,serial: str|None=None,timeout=DEFAULT_TIMEOUT)->bytes:
    if serial is not None and not SERIAL.fullmatch(serial):raise ValueError('Unapproved serial')
    command=['docker','exec',CONTAINER,'adb']
    if serial:command+=['-s',serial]
    # Do not accept shell snippets; each ADB argument is supplied separately.
    return ssh(command+list(args),timeout=timeout)


def devices()->list[dict]:
    output=adb('devices','-l').decode('utf-8','replace')
    result=[]
    for line in output.splitlines()[1:]:
        parts=line.split()
        if len(parts)<2 or not SERIAL.fullmatch(parts[0]) or parts[1]!='device':continue
        serial=parts[0]
        try:
            size=adb('shell','wm','size',serial=serial).decode().strip()
            dpi=adb('shell','wm','density',serial=serial).decode().strip()
            game=bool(adb('shell','pidof','com.gof.global',serial=serial).strip())
            api=adb('shell','getprop','ro.build.version.sdk',serial=serial).decode().strip()
            model=adb('shell','getprop','ro.product.model',serial=serial).decode().strip()
            fingerprint=adb('shell','getprop','ro.build.fingerprint',serial=serial).decode().strip()
            result.append({'serial':serial,'size':size,'density':dpi,'game':game,
                           'api':api,'model':model,'fingerprint_hash':hashlib.sha256(fingerprint.encode()).hexdigest()[:16],
                           'ready':('720x1280' in size and '320' in dpi and game and api=='36')})
        except Exception as exc:
            result.append({'serial':serial,'ready':False,'error':str(exc)[:120]})
    return result


def bind(serial:str,force=False)->dict:
    if not SERIAL.fullmatch(serial):raise ValueError('Invalid serial')
    found=next((d for d in devices() if d['serial']==serial),None)
    if not found or not found.get('ready'):raise RuntimeError('Emulator must be running Android 16, game loaded and 720x1280@320dpi')
    cfg=configuration()
    if cfg.get('bound') and cfg['bound']['serial']!=serial and not force:
        raise RuntimeError('Another emulator is bound; explicit --force needed')
    cfg.update(bound={k:found[k] for k in ('serial','fingerprint_hash','size','density','api')},
               mode='observe',allow_unreviewed_actions=False)
    save_config(cfg)
    return cfg


def bound_device()->dict:
    pinned=configuration().get('bound')
    if not pinned:raise RuntimeError('No emulator bound; use discover then bind')
    actual=next((d for d in devices() if d['serial']==pinned['serial']),None)
    if not actual or not actual.get('ready'):raise RuntimeError('Pinned emulator no longer healthy; refusing emulator failover')
    for field in ('fingerprint_hash','size','density','api'):
        if actual[field]!=pinned[field]:raise RuntimeError('Pinned emulator changed '+field+'; refusing any action')
    return actual


def tasks()->list[dict]:
    return [t for t in json.loads(CATALOG.read_text()) if TASK.fullmatch(t.get('key',''))]


def scenario(task:str)->dict:
    row=next((t for t in tasks() if t['key']==task),None)
    if not row:raise ValueError('Unknown task')
    # Every task has a dedicated test scenario and identity; default is
    # read-only screen evidence. Real interactions require separately reviewed
    # visual pre/postconditions and explicit enabling, never inferred clicks.
    expected={
        'INITIALIZE':['city','world'],
        'DO_EXPLORATION':['city','world','exploration'],
        'EXPLORATION_CHEST':['exploration'],
        'DAILY_MISSIONS':['daily_missions'],
    }
    return {'task':task,'label':row['label'],'factory':row['factory'],
            'mode':'observe','steps':['verify_pinned_emulator','capture_before',
                  'recognize_current_screen','capture_after','write_evidence'],
            'expected_screens':expected.get(task,[]),
            'reviewed_action_steps':(
                ['verify_city_world_twice','optional_known_research_back',
                 'tap_exploration_entry_once','verify_exploration_title_and_explore_button']
                 if task=='DO_EXPLORATION' else
                ['verify_daily_title_and_free_claim_twice','tap_claim_all_once',
                 'verify_reward_banner_and_dismiss_hint_twice','dismiss_reward_once',
                 'verify_daily_title_and_claim_button_gone']
                 if task=='DAILY_MISSIONS' else []),
            'requires_review_for_actions':True}


def png(serial:str)->bytes:
    data=adb('exec-out','screencap','-p',serial=serial,timeout=30)
    if not data.startswith(b'\x89PNG\r\n\x1a\n') or len(data)>MAX_PNG:
        raise RuntimeError('Invalid or oversized PNG from emulator')
    return data


def snapshot(run_id:str,name:str,device:str)->dict:
    if not ID.fullmatch(run_id) or not re.fullmatch('[a-z0-9-]{2,35}',name):raise ValueError('Invalid snapshot name')
    d=DATA/'evidence'/run_id
    d.mkdir(parents=True,exist_ok=True)
    content=png(device)
    path=d/(name+'.png')
    path.write_bytes(content)
    os.chmod(path,0o600)
    return {'name':name,'sha256':hashlib.sha256(content).hexdigest(),
            'bytes':len(content),'file':path.name}


def new_run(task:str,mode='observe')->str:
    if mode not in ('observe','navigate','claim_free'):
        raise ValueError('Invalid test mode')
    plan=scenario(task)
    if (mode=='navigate' and task!='DO_EXPLORATION' or
        mode=='claim_free' and task!='DAILY_MISSIONS'):
        raise ValueError('Active test is not reviewed for this task; observation only')
    pinned=configuration().get('bound')
    if not pinned:raise ValueError('Bind a test emulator first')
    if paused():raise RuntimeError('Controller is paused; new runs not accepted')
    run_id=os.urandom(8).hex()
    with db() as c:
        pending=c.execute("select count(*) from runs where state in ('queued','running')").fetchone()[0]
        if pending>=80:raise RuntimeError('Queue full')
        c.execute('insert into runs(id,task,mode,state,created_at,device) values(?,?,?,?,?,?)',
                  (run_id,task,mode,'queued',stamp(),pinned['serial']))
    return run_id


def heartbeat(healthy:bool,serial:str,reason:str=''):
    DATA.mkdir(parents=True,exist_ok=True)
    data={'healthy':bool(healthy),'device':serial,'checked_at':stamp(),
          'unix_time':time.time(),'detail':reason[:160]}
    tmp=DATA/'health.tmp';tmp.write_text(json.dumps(data))
    os.chmod(tmp,0o600);tmp.replace(DATA/'health.json')


def get_heartbeat(pinned):
    path=DATA/'health.json'
    try:
        obj=json.loads(path.read_text())
        age=max(0,int(time.time()-float(obj['unix_time'])))
        obj['seconds_old']=age
        obj['connected']=bool(obj.get('healthy') and age<=40*60
            and pinned and obj.get('device')==pinned.get('serial'))
        return obj
    except (OSError,ValueError,TypeError,KeyError):
        return {'healthy':False,'connected':False,'seconds_old':None,'checked_at':None}


def status(limit=40)->dict:
    cfg=configuration()
    with db() as c:
        rows=[dict(r) for r in c.execute('select * from runs order by created_at desc,id desc limit ?', (max(1,min(limit,200)),))]
        summary=dict(c.execute('select state,count(*) from runs group by state').fetchall())
    health=get_heartbeat(cfg.get('bound'))
    return {'connected':health['connected'],'device':cfg.get('bound'),
            'health':health,'paused':bool(cfg.get('paused',False)),
            'allow_actions':{'DO_EXPLORATION':['navigate_only'],
                              'DAILY_MISSIONS':['free_claim_only']},
            'mode':'reviewed_navigation_or_observe','summary':summary,'runs':rows}


def details(run_id:str)->dict|None:
    if not ID.fullmatch(run_id):return None
    with db() as c:
        row=c.execute('select * from runs where id=?',(run_id,)).fetchone()
    if not row:return None
    path=DATA/'evidence'/run_id/'result.json'
    return {'run':dict(row),'result':json.loads(path.read_text()) if path.is_file() else None}


def next_queued()->dict|None:
    with db() as c:
        c.execute('begin immediate')
        row=c.execute("select * from runs where state='queued' order by created_at,id limit 1").fetchone()
        if not row:return None
        c.execute("update runs set state='running',started_at=? where id=?",(stamp(),row['id']))
        return dict(row)


def record_board_event(report:dict)->None:
    # Add a trace to the existing QA task timeline, but never alter the
    # operator's seven steps, status, notes or joint verification signatures.
    path=HOME/'.local/share/survivalops-qa/dashboard.sqlite3'
    if not path.is_file():return
    try:
        with sqlite3.connect(path,timeout=5) as c:
            if not c.execute('select 1 from tasks where key=?',(report['task'],)).fetchone():return
            detail=f"AutoQA {report['id']}: {report['outcome']}, "                   f"{report['checks']} checks, {report['actions']} actions, {report['duration_ms']}ms; "                   f"evidence in AutoQA dashboard"
            c.execute('insert into events(task_key,kind,detail,actor,occurred_at) values(?,?,?,?,?)',
                      (report['task'],'autoqa_observation',detail,'AutoQA Controller',stamp()))
    except sqlite3.Error:pass


def execute(row:dict)->dict:
    start=time.monotonic()
    rid=row['id']
    events=[]
    report={'schema':1,'id':rid,'task':row['task'],'mode':row['mode'],
            'device':row['device'],'started_at':stamp(),'screens':[],
            'events':events,'checks':0,'actions':0,'outcome':'incomplete'}
    try:
        scenario(row['task'])
        actual=bound_device()
        if actual['serial']!=row['device']:raise RuntimeError('Queued run refers to a different emulator; cancelled')
        heartbeat(True,actual['serial'],'Android 16 emulator, display and WOS process verified')
        events.append({'time':stamp(),'type':'preflight','detail':'Android 16, 720x1280@320, WOS running, pinned serial verified'})
        plan=scenario(row['task'])
        states=[]
        def evidence(name):
            capture_started=time.monotonic()
            image=snapshot(rid,name,actual['serial'])
            image['capture_ms']=round((time.monotonic()-capture_started)*1000)
            path=DATA/'evidence'/rid/image['file']
            started=time.monotonic()
            detection=recognition.analyze_file(path)
            image['recognition']=detection
            image['recognition_ms']=round((time.monotonic()-started)*1000)
            states.append(detection['state'])
            report['screens'].append(image)
            events.append({'time':stamp(),'type':'recognition',
                'detail':name+': '+detection['state']+' / score '+str(detection['score'])})
            report['checks']+=len(recognition.STATES)
            return image,path,detection
        first,first_path,initial=evidence('before')
        if row['mode']=='claim_free':
            if row['task']!='DAILY_MISSIONS':
                raise RuntimeError('No reviewed free Claim All workflow')
            first_state=daily.state(first_path)
            if not first_state['ready_to_claim']:
                raise RuntimeError('Daily title and free Claim All not jointly verified; no tap')
            time.sleep(0.15)
            _,check_path,_=evidence('preflight2')
            second_state=daily.state(check_path)
            if not daily.claim_is_stable(first_state,second_state):
                raise RuntimeError('Claim All button did not remain stable for two frames; no tap')
            bound_device()
            require_active()
            x,y=second_state['claim_point']
            adb('shell','input','tap',str(x),str(y),serial=actual['serial'],timeout=15)
            report['actions']+=1
            events.append({'time':stamp(),'type':'tap','detail':'Verified free Daily Missions Claim All'})
            reward_state=None
            for index in range(8):
                time.sleep(0.35)
                _,path,_=evidence('reward-'+str(index))
                state=daily.state(path)
                if state['reward_visible']:
                    reward_state=state
                    break
            if reward_state is None:
                raise RuntimeError('Claim All tapped, but no confirmed Rewards banner+hint; stopping, not marking success')
            time.sleep(0.15)
            _,second_reward_path,_=evidence('reward-confirm')
            if not daily.reward_is_stable(reward_state,daily.state(second_reward_path)):
                raise RuntimeError('Rewards dialog changed; refusing dismiss tap')
            bound_device()
            require_active()
            # This area is explicitly labelled 'Tap anywhere to exit' and
            # verified on two frames. Never tap reward chests or gem buttons.
            adb('shell','input','tap','365','1163',serial=actual['serial'],timeout=15)
            report['actions']+=1
            events.append({'time':stamp(),'type':'tap','detail':'Dismiss confirmed free Rewards via Tap anywhere to exit'})
            done=False
            for index in range(8):
                time.sleep(0.35)
                image,path,_=evidence('after' if index==7 else 'post-'+str(index))
                if daily.state(path)['post_claim']:
                    if image['name']!='after':
                        new_path=path.with_name('after.png');path.replace(new_path)
                        image['name']='after';image['file']='after.png'
                    done=True
                    break
            if done:
                report.update(outcome='free_claim_verified',
                              reason='Free Claim All collected and Rewards dismissed; optional milestone chest not tested')
            else:
                report.update(outcome='blocked',reason='No verified Daily screen with Claim All gone after reward dismissal')
        elif row['mode']=='navigate':
            # Only the one reviewed, free City/World→Exploration shortcut.
            # No spending, no automatic recovery, no guessing button pixels.
            if row['task']!='DO_EXPLORATION':raise RuntimeError('No reviewed navigation workflow for this task')
            if initial['state'] not in ('city','world') or initial['score']<0.87:
                # Only a known, non-purchase screen can be recovered. The
                # existing Java Task Coach verified both Tech Research title
                # and top-left Back on actual 2026 frames.
                known=recognition.known_research_back(first_path)
                if not known['ready']:
                    raise RuntimeError('No positively recognized City/World root or reviewed Tech Research exit; no tap')
                time.sleep(0.13)
                before_back,preback_path,_=evidence('research-preflight')
                confirmation=recognition.known_research_back(preback_path)
                if not confirmation['ready']:
                    raise RuntimeError('Tech Research Back changed; refusing unverified click')
                bound_device()
                require_active()
                adb('shell','input','tap','41','32',serial=actual['serial'],timeout=15)
                report['actions']+=1
                events.append({'time':stamp(),'type':'tap','detail':'Verified Tech Research Back x41 y32 (no spending)'})
                resolved=False
                for count in range(6):
                    time.sleep(0.35)
                    _,_,fresh=evidence('research-exit-'+str(count))
                    if fresh['state'] in ('city','world') and fresh['score']>=0.87:
                        initial=fresh
                        resolved=True
                        break
                if not resolved:
                    raise RuntimeError('Tech Research closed but no verified City/World root; stopping safely')
            time.sleep(0.15)
            second,_,verified=evidence('preflight2')
            if verified['state']!=initial['state'] or verified['score']<0.87:
                raise RuntimeError('Root screen changed between confirmations; no tap')
            # Re-evaluate pinned identity immediately before the single action.
            bound_device()
            require_active()
            adb('shell','input','tap','70','1220',serial=actual['serial'],timeout=15)
            report['actions']+=1
            events.append({'time':stamp(),'type':'tap','detail':'Verified Exploration shortcut x70 y1220'})
            found=False
            for index in range(8):
                time.sleep(0.35)
                image,path,det=evidence('after' if index==7 else 'transition-'+str(index))
                ready=recognition.exploration_ready(path)
                events.append({'time':stamp(),'type':'entry_check',
                   'detail':'Exploration title %.3f, Explore %.3f'%(ready['title']['score'],ready['explore_button']['score'])})
                if ready['ready']:
                    if image['name']!='after':
                        # The proof already exists; retain it, no new tap.
                        new_path=path.with_name('after.png')
                        path.replace(new_path)
                        image['name']='after'
                        image['file']='after.png'
                    report['outcome']='navigation_verified'
                    report['reason']='Exploration header and Explore CTA independently recognized after single tap'
                    found=True
                    break
            if not found:
                report.update(outcome='blocked',reason='Exploration did not become visually ready; no extra clicks')
        else:
            time.sleep(0.5)
            evidence('after')
            report.update(outcome='observed',
                          reason='Visual state observations only; action workflows not enabled')
        report.update(expected_screens=plan['expected_screens'],states=states)
        if report['outcome']=='observed' and plan['expected_screens'] and not any(x in plan['expected_screens'] for x in states):
            events.append({'time':stamp(),'type':'review_needed',
                           'detail':'Current screen outside task expected states; no clicks attempted'})
        if (report['outcome']=='observed' and len(states)>=2
                and states[0]=='unknown' and states[-1]=='unknown'):
            candidate=proposals.propose_unknown(DATA/'evidence'/rid,
                DATA/'evidence'/rid/'before.png',DATA/'evidence'/rid/'after.png')
            if candidate:
                report.setdefault('proposals',[]).append(candidate)
                events.append({'time':stamp(),'type':'template_proposal',
                        'detail':'Stable unknown UI cropped for review; never activated'})
    except Exception as exc:
        if 'actual' not in locals():
            heartbeat(False,row['device'],'Emulator preflight failed; '+str(exc)[:90])
        report.update(outcome='blocked',reason=str(exc)[:300])
        events.append({'time':stamp(),'type':'error','detail':report['reason']})
    finally:
        report['finished_at']=stamp()
        report['duration_ms']=round((time.monotonic()-start)*1000)
        folder=DATA/'evidence'/rid;folder.mkdir(parents=True,exist_ok=True)
        resultfile=folder/'result.json';resultfile.write_text(json.dumps(report,indent=2))
        os.chmod(resultfile,0o600)
        with db() as c:
            c.execute('update runs set state=?,ended_at=?,reason=?,checks=?,actions=?,duration_ms=? where id=?',
                      (report['outcome'],report['finished_at'],report['reason'],report['checks'],report['actions'],report['duration_ms'],rid))
        record_board_event(report)
    return report


def reconcile_action_counters()->dict:
    """Fix stale counters from actual persisted tap events; never infer taps.
    Never alter original screenshots, recognition evidence or task signoffs.
    """
    fixed=0
    for folder in (DATA/'evidence').glob('*'):
        if not folder.is_dir() or not ID.fullmatch(folder.name):continue
        file=folder/'result.json'
        if not file.is_file():continue
        try:
            report=json.loads(file.read_text())
            count=sum(e.get('type')=='tap' for e in report.get('events',[]))
            if count==report.get('actions',0):continue
            report['actions']=count
            report.setdefault('events',[]).append({'time':stamp(),'type':'metrics_corrected',
                'detail':'Action count reconciled against explicit stored tap events'})
            file.write_text(json.dumps(report,indent=2))
            with db() as c:
                c.execute('update runs set actions=? where id=?',(count,folder.name))
            fixed+=1
        except (OSError,ValueError,sqlite3.Error):continue
    return {'corrected_runs':fixed,'game_actions':0}


def recheck_evidence()->dict:
    """Re-analyze stored screenshots after a matcher fix, without game input.
    Historical evidence bytes and run outcomes are preserved; stale scores
    must not remain visible to an operator as 100% false positives.
    """
    updated=0
    for folder in sorted((DATA/'evidence').glob('*')):
        if not folder.is_dir() or not ID.fullmatch(folder.name):continue
        path=folder/'result.json'
        if not path.is_file():continue
        try:
            report=json.loads(path.read_text())
            changed=False
            for shot in report.get('screens',[]):
                picture=folder/shot.get('file','')
                if picture.is_file() and picture.name.endswith('.png'):
                    result=recognition.analyze_file(picture)
                    if result!=shot.get('recognition'):
                        shot['recognition']=result
                        changed=True
            if changed:
                states=[shot['recognition']['state'] for shot in report['screens']]
                report['states']=states
                report.setdefault('events',[]).append({'time':stamp(),
                    'type':'recognition_corrected',
                    'detail':'Re-scored saved screenshots after rejecting flat-colour false positives; no game actions'})
                if (report.get('outcome')=='observed' and len(states)>=2
                        and states[0]=='unknown' and states[-1]=='unknown'):
                    first=folder/'before.png';last=folder/'after.png'
                    candidate=proposals.propose_unknown(folder,first,last)
                    if candidate:report['proposals']=[candidate]
                path.write_text(json.dumps(report,indent=2))
                updated+=1
        except (ValueError,OSError,KeyError):continue
    return {'rechecked':updated,'game_actions':0}


def worker(once=False):
    DATA.mkdir(parents=True,exist_ok=True)
    lock=open(DATA/'worker.lock','w')
    fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
    # A previously interrupted worker cannot leave a stale running entry.
    with db() as c:
        c.execute("update runs set state='interrupted',reason='Worker restarted; no automatic retry',ended_at=? where state='running'",(stamp(),))
    while True:
        if paused():
            if once:break
            time.sleep(2)
            continue
        row=next_queued()
        if row:execute(row)
        elif once:break
        else:time.sleep(2)
        if once:break


def main():
    p=argparse.ArgumentParser(description=__doc__)
    sub=p.add_subparsers(dest='cmd',required=True)
    sub.add_parser('discover')
    b=sub.add_parser('bind');b.add_argument('serial');b.add_argument('--force',action='store_true')
    q=sub.add_parser('enqueue');q.add_argument('task');q.add_argument('--mode',choices=('observe','navigate','claim_free'),default='observe')
    sub.add_parser('status')
    d=sub.add_parser('detail');d.add_argument('id')
    w=sub.add_parser('worker');w.add_argument('--once',action='store_true')
    sub.add_parser('workflows')
    sub.add_parser('recheck-evidence')
    sub.add_parser('reconcile-counters')
    action=sub.add_parser('pause');action.add_argument('state',choices=('on','off'))
    a=p.parse_args()
    if a.cmd=='discover':result=devices()
    elif a.cmd=='bind':result=bind(a.serial,a.force)
    elif a.cmd=='enqueue':result={'id':new_run(a.task,a.mode)}
    elif a.cmd=='status':result=status()
    elif a.cmd=='detail':result=details(a.id)
    elif a.cmd=='workflows':result=[scenario(t['key']) for t in tasks()]
    elif a.cmd=='recheck-evidence':result=recheck_evidence()
    elif a.cmd=='reconcile-counters':result=reconcile_action_counters()
    elif a.cmd=='pause':result=pause(a.state=='on')
    elif a.cmd=='worker':worker(a.once);result={'ok':True}
    print(json.dumps(result,indent=2,ensure_ascii=False))

if __name__=='__main__':main()
