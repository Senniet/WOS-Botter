import csv
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
import audit

class AuditTest(unittest.TestCase):
 def test_all_tasks_are_listed_without_claiming_success(self):
  with tempfile.TemporaryDirectory() as tmp:
   result=audit.generate(Path(tmp),Path(tmp)/'absent.db')
   self.assertEqual(62,result['total_tasks'])
   self.assertEqual(0,result['coach_sessions'])
   self.assertTrue(all(not r['completion_proved'] for r in result['tasks']))
   self.assertTrue(all(r['assessment']=='needs_live_test' for r in result['tasks']))

 def test_successful_scheduler_and_zero_hit_safety_are_not_in_game_success(self):
  with tempfile.TemporaryDirectory() as tmp:
   root=Path(tmp);folder=root/'coach/2026-11-16/20261116-221100-abc1234';folder.mkdir(parents=True)
   (folder/'run.txt').write_text('task=TRAINING_TROOPS\nstarted=2026-11-16T22:11:00Z\n')
   (folder/'summary.txt').write_text('task=TRAINING_TROOPS scheduler_outcome=SUCCEEDED (not proof of in-game completion)\n')
   with (folder/'recognition-events.csv').open('w',newline='') as file:
    writer=csv.DictWriter(file,fieldnames=['time_utc','template','method','score','hit'])
    writer.writeheader()
    for i in range(10):
     writer.writerow(dict(time_utc=f'2026-11-16T22:11:{i:02d}Z',
       template='GAME_HOME_RECONNECT',method='mono',score='11.3',hit='false'))
    writer.writerow(dict(time_utc='2026-11-16T22:11:11Z',
      template='TRAINING_TRAIN_BUTTON',method='color',score='100',hit='true'))
   qa=root/'qa.db'
   with sqlite3.connect(qa) as db:
    db.execute('create table tasks(key text,status text)')
    db.execute('create table issues(task_key text,state text)')
    db.execute('insert into tasks values(?,?)',('TRAINING_TROOPS','broken'))
    db.execute('insert into issues values(?,?)',('TRAINING_TROOPS','open'))
   r=audit.generate(root/'coach',qa)
   row=next(x for x in r['tasks'] if x['task']=='TRAINING_TROOPS')
   self.assertEqual(1,row['coach_success_claims'])
   self.assertEqual(11,row['coach_checks'])
   self.assertEqual(1,row['coach_hits'])
   self.assertEqual('broken',row['qa_status'])
   self.assertEqual(1,row['open_issues'])
   self.assertFalse(row['completion_proved'])
   self.assertFalse(any(x['template']=='GAME_HOME_RECONNECT' for x in row['slow_or_repeated_templates']))

if __name__=='__main__':unittest.main()
