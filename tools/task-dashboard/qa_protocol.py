"""Durable two-party, evidence-gated QA rounds for supported game tasks.

A screenshot/template hit is not proof of a successful action. All seven stages
must be completed for the SAME test round and the operator and reviewer sign
off separately. A new round or a changed result invalidates prior signoffs.
"""
from __future__ import annotations
from contextlib import contextmanager
from datetime import datetime, timezone, timedelta
from pathlib import Path
import json
import re
import sqlite3

STAGES = (
    ('preflight', 'Emulator, resolutie, DPI en game-instellingen gecontroleerd'),
    ('navigation', 'Juiste scherm geopend; blokkades herkend'),
    ('recognition', 'Juiste knop herkend op het juiste scherm'),
    ('interaction', 'Verwachte actie uitgevoerd, zonder ongewenste klik'),
    ('postcondition', 'Resultaat in Whiteout Survival onafhankelijk bevestigd'),
    ('return_schedule', 'Terug naar juiste scherm en vervolgplanning gecontroleerd'),
    ('regression', 'Reparatie opnieuw getest met screenshot- of coderegressietest'),
)
STAGE_IDS = {x for x,_ in STAGES}
RESULTS = {'pending','pass','fail','blocked'}
RUN_PATTERN = re.compile(r'^20\d{6}-\d{6}-[a-z0-9]{4,32}$')
BUILD_PATTERN = re.compile(r'^[A-Za-z0-9][A-Za-z0-9.+_-]{1,40}$')


def timestamp():
    return datetime.now(timezone.utc).isoformat(timespec='seconds').replace('+00:00','Z')


class Protocol:
    def __init__(self, dbpath, evidence_root=None):
        self.dbpath = Path(dbpath)
        self.evidence_root = Path(evidence_root) if evidence_root else Path.home()/'survivalops-diagnostics/taskcoach'

    @contextmanager
    def connection(self):
        db=sqlite3.connect(self.dbpath,timeout=10)
        try:
            db.row_factory=sqlite3.Row
            db.execute('PRAGMA foreign_keys=ON')
            db.execute('PRAGMA busy_timeout=10000')
            with db:
                yield db
        finally:
            db.close()

    def install(self):
        with self.connection() as db:
            db.executescript('''
                CREATE TABLE IF NOT EXISTS qa_rounds(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  task_key TEXT NOT NULL REFERENCES tasks(key),
                  build TEXT NOT NULL, coach_run TEXT NOT NULL DEFAULT '',
                  started_at TEXT NOT NULL, started_by TEXT NOT NULL,
                  operator_signed_at TEXT NOT NULL DEFAULT '', operator_note TEXT NOT NULL DEFAULT '',
                  reviewer_signed_at TEXT NOT NULL DEFAULT '', reviewer_note TEXT NOT NULL DEFAULT '',
                  superseded_at TEXT NOT NULL DEFAULT '', completed_at TEXT NOT NULL DEFAULT '');
                CREATE TABLE IF NOT EXISTS qa_checks(
                  round_id INTEGER NOT NULL REFERENCES qa_rounds(id),
                  step TEXT NOT NULL, result TEXT NOT NULL DEFAULT 'pending',
                  note TEXT NOT NULL DEFAULT '', actor TEXT NOT NULL DEFAULT '',
                  updated_at TEXT NOT NULL DEFAULT '', PRIMARY KEY(round_id,step));
                CREATE INDEX IF NOT EXISTS idx_qa_rounds_task ON qa_rounds(task_key,id);
            ''')

    @staticmethod
    def latest_row(db, key):
        return db.execute('SELECT * FROM qa_rounds WHERE task_key=? ORDER BY id DESC LIMIT 1',(key,)).fetchone()

    def snapshot(self,key):
        with self.connection() as db:
            row=self.latest_row(db,key)
            if row is None:return None
            return {'round':dict(row),'checks':[dict(r) for r in db.execute(
                'SELECT step,result,note,actor,updated_at FROM qa_checks WHERE round_id=? ORDER BY rowid',(row['id'],))],
                'steps':[{'id':x,'label':label} for x,label in STAGES]}

    @staticmethod
    def event(db,key,kind,detail,actor,at):
        db.execute('INSERT INTO events(task_key,kind,detail,actor,occurred_at) VALUES(?,?,?,?,?)',
                   (key,kind,detail,actor,at))

    @staticmethod
    def state(db,key,state,at):
        db.execute('''UPDATE tasks SET status=?, verified_by='', verified_at='',
                      updated_at=?,version=version+1 WHERE key=?''',(state,at,key))

    def begin(self,key,build,actor):
        if not isinstance(build,str) or not BUILD_PATTERN.fullmatch(build):return 'invalid',None
        with self.connection() as db:
            db.execute('BEGIN IMMEDIATE')
            if not db.execute('SELECT 1 FROM tasks WHERE key=?',(key,)).fetchone():return 'missing',None
            at=timestamp()
            db.execute('UPDATE qa_rounds SET superseded_at=? WHERE task_key=? AND superseded_at=\'\' AND completed_at=\'\'',(at,key))
            cur=db.execute('INSERT INTO qa_rounds(task_key,build,started_at,started_by) VALUES(?,?,?,?)',
                           (key,build,at,actor))
            for stage,_ in STAGES:
                db.execute('INSERT INTO qa_checks(round_id,step) VALUES(?,?)',(cur.lastrowid,stage))
            self.state(db,key,'testing',at)
            self.event(db,key,'qa_round_started',f'round={cur.lastrowid} build={build}',actor,at)
        return 'ok',self.snapshot(key)

    def validate_run(self,key,run_id,started_at):
        if not isinstance(run_id,str) or not RUN_PATTERN.fullmatch(run_id):return False
        try:
            day=datetime.strptime(run_id[:15],'%Y%m%d-%H%M%S').replace(tzinfo=timezone.utc)
            started=datetime.fromisoformat(started_at.replace('Z','+00:00'))
        except ValueError:return False
        if day < started-timedelta(minutes=5):return False
        folder=self.evidence_root/day.strftime('%Y-%m-%d')/run_id
        manifest=folder/'run.txt'
        if not manifest.is_file() or not (folder/'recognition-events.csv').is_file():return False
        if manifest.stat().st_size>10000:return False
        return f'task={key}' in manifest.read_text(encoding='utf-8',errors='replace').splitlines()

    def attach(self,key,run_id,actor):
        with self.connection() as db:
            db.execute('BEGIN IMMEDIATE')
            row=self.latest_row(db,key)
            if row is None:return 'no_round',None
            if row['completed_at']:return 'finalized',None
            if not self.validate_run(key,run_id,row['started_at']):return 'invalid_evidence',None
            at=timestamp()
            db.execute('''UPDATE qa_rounds SET coach_run=?,operator_signed_at='',operator_note='',
                        reviewer_signed_at='',reviewer_note='' WHERE id=?''',(run_id,row['id']))
            self.state(db,key,'testing',at)
            self.event(db,key,'qa_evidence_attached',f'round={row["id"]} coach={run_id}',actor,at)
        return 'ok',self.snapshot(key)

    def check(self,key,step,result,note,actor):
        if step not in STAGE_IDS or result not in RESULTS or not isinstance(note,str) or len(note)>1200:
            return 'invalid',None
        if result in {'pass','fail','blocked'} and len(note.strip())<8:
            return 'needs_note',None
        with self.connection() as db:
            db.execute('BEGIN IMMEDIATE')
            row=self.latest_row(db,key)
            if row is None:return 'no_round',None
            if row['completed_at']:return 'finalized',None
            at=timestamp()
            db.execute('''UPDATE qa_checks SET result=?,note=?,actor=?,updated_at=? WHERE round_id=? AND step=?''',
                       (result,note.strip(),actor,at,row['id'],step))
            db.execute('''UPDATE qa_rounds SET operator_signed_at='',operator_note='',
                        reviewer_signed_at='',reviewer_note='' WHERE id=?''',(row['id'],))
            status={'fail':'broken','blocked':'blocked'}.get(result,'testing')
            self.state(db,key,status,at)
            self.event(db,key,'qa_check',f'round={row["id"]} step={step} result={result}',actor,at)
        return 'ok',self.snapshot(key)

    def sign(self,key,role,note):
        if role not in {'operator','reviewer'} or not isinstance(note,str) or not 12<=len(note.strip())<=1200:
            return 'needs_evidence_note',None
        with self.connection() as db:
            db.execute('BEGIN IMMEDIATE')
            row=self.latest_row(db,key)
            if row is None:return 'no_round',None
            if row['completed_at']:return 'finalized',None
            if not row['coach_run'] or not self.validate_run(key,row['coach_run'],row['started_at']):
                return 'missing_coach_evidence',None
            checks=[r['result'] for r in db.execute('SELECT result FROM qa_checks WHERE round_id=?',(row['id'],))]
            if len(checks)!=len(STAGES) or not all(c=='pass' for c in checks):return 'incomplete_checks',None
            if db.execute('SELECT 1 FROM issues WHERE task_key=? AND state=\'open\' LIMIT 1',(key,)).fetchone():
                return 'open_issues',None
            at=timestamp()
            if role=='operator':
                db.execute('UPDATE qa_rounds SET operator_signed_at=?,operator_note=? WHERE id=?',
                           (at,note.strip(),row['id']))
            else:
                db.execute('UPDATE qa_rounds SET reviewer_signed_at=?,reviewer_note=? WHERE id=?',
                           (at,note.strip(),row['id']))
            now=db.execute('SELECT * FROM qa_rounds WHERE id=?',(row['id'],)).fetchone()
            both=bool(now['operator_signed_at'] and now['reviewer_signed_at'])
            if both:
                db.execute('UPDATE qa_rounds SET completed_at=? WHERE id=?',(at,row['id']))
                db.execute('''UPDATE tasks SET status='verified',verified_by='Operator + ChatGPT',
                          verified_at=?,updated_at=?,version=version+1 WHERE key=?''',(at,at,key))
            else:
                self.state(db,key,'working',at)
            self.event(db,key,'qa_both_verified' if both else 'qa_'+role+'_signed',
                       f'round={row["id"]} coach={row["coach_run"]}',role,at)
        return 'ok',self.snapshot(key)

    def allows_verification(self,key):
        """Manual single-party checkboxes cannot newly set VERIFIED."""
        return False
