#!/usr/bin/env python3
import base64
from http.client import HTTPConnection
import hashlib
from datetime import datetime,timezone
from http.server import ThreadingHTTPServer
import json
from pathlib import Path
from unittest.mock import patch
import tempfile
import threading
import unittest
from server import Board, Handler, read_template_audit
from qa_protocol import STAGES

ROOT=Path(__file__).resolve().parent
class BoardTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.diags=self.root/"inbox";self.diags.mkdir()
        self.authfile=self.root/"auth.json";salt=b"\x08"*16
        self.password="test-private-password"
        self.authfile.write_text(json.dumps({"salt":salt.hex(),
          "password_hash":hashlib.pbkdf2_hmac("sha256",self.password.encode(),salt,260000).hex(),
          "session_key":base64.urlsafe_b64encode(b"\x09"*32).decode()}))
        self.dbfile=self.root/"db.sqlite3"
        self.board=Board(self.dbfile,ROOT/"catalog.json",self.diags,self.authfile)
        self.board.protocol.evidence_root=self.root/'coach'
    def tearDown(self):self.temp.cleanup()

    def coach_receipt(self,task='STOREHOUSE_CHEST'):
        self._test_run_counter=getattr(self,'_test_run_counter',0)+1
        run_id=datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')+'-qa12345'+str(self._test_run_counter)
        folder=self.board.protocol.evidence_root/datetime.now(timezone.utc).strftime('%Y-%m-%d')/run_id
        folder.mkdir(parents=True)
        (folder/'run.txt').write_text('task='+task+'\nscheduler_outcome=SUCCEEDED\n')
        (folder/'recognition-events.csv').write_text('time_utc,template,method,threshold,score,hit\n')
        return run_id

    def test_alliance_help_is_independent_unverified_qa_task(self):
        detail=self.board.detail('ALLIANCE_HELP_REQUEST')
        self.assertEqual('Alliance Help Request',detail['task']['label'])
        self.assertEqual('AllianceHelpRoutine',detail['task']['factory'])
        self.assertEqual('ALLIANCE',detail['task']['category'])
        self.assertNotEqual('verified',detail['task']['status'])
        state,qa=self.board.protocol.begin('ALLIANCE_HELP_REQUEST','1.1.69','Operator')
        self.assertEqual('ok',state)
        self.assertEqual(7,len(qa['checks']))
        self.assertEqual('missing_coach_evidence',self.board.protocol.sign(
            'ALLIANCE_HELP_REQUEST','operator','No game reward is yet proved.')[0])

    def test_62_tasks_and_restarts_preserve_joint_verified(self):
        self.assertEqual(62,self.board.summary()["total"])
        row=self.board.detail("STOREHOUSE_CHEST")["task"]
        state,new=self.board.edit_task('STOREHOUSE_CHEST',{'version':row['version'],'status':'verified'},'Operator')
        self.assertEqual('needs_joint_qa',state)
        self.assertEqual('ok',self.board.protocol.begin('STOREHOUSE_CHEST','1.1.66','Operator')[0])
        run_id=self.coach_receipt()
        self.assertEqual('ok',self.board.protocol.attach('STOREHOUSE_CHEST',run_id,'ChatGPT')[0])
        for step,_ in STAGES:
            self.assertEqual('ok',self.board.protocol.check('STOREHOUSE_CHEST',step,'pass','Live test with evidence for '+step,'Operator')[0])
        self.assertEqual('ok',self.board.protocol.sign('STOREHOUSE_CHEST','operator','I observed the intended action and correct game result.')[0])
        self.assertEqual('working',self.board.detail('STOREHOUSE_CHEST')['task']['status'])
        self.assertEqual('ok',self.board.protocol.sign('STOREHOUSE_CHEST','reviewer','Coach capture, logs and postcondition independently reviewed.')[0])
        after=Board(self.dbfile,ROOT/"catalog.json",self.diags,self.authfile).detail('STOREHOUSE_CHEST')
        self.assertEqual('verified',after['task']['status'])
        self.assertEqual('Operator + ChatGPT',after['task']['verified_by'])
        self.assertTrue(after['qa']['round']['completed_at'])

    def test_joint_qa_needs_real_matching_new_evidence_and_all_passed(self):
        task='TRAINING_TROOPS'
        self.assertEqual('ok',self.board.protocol.begin(task,'1.1.66','Operator')[0])
        self.assertEqual('invalid_evidence',self.board.protocol.attach(task,'fake-claim','Operator')[0])
        self.assertEqual('missing_coach_evidence',self.board.protocol.sign(task,'operator','too short')[0])
        self.assertEqual('missing_coach_evidence',self.board.protocol.sign(task,'operator','I saw full game result on screen.')[0])
        bad=self.coach_receipt('STOREHOUSE_CHEST')
        self.assertEqual('invalid_evidence',self.board.protocol.attach(task,bad,'Operator')[0])
        good=self.coach_receipt(task)
        self.assertEqual('ok',self.board.protocol.attach(task,good,'Operator')[0])
        self.assertEqual('incomplete_checks',self.board.protocol.sign(task,'operator','Game actions were observed and verified.')[0])
        self.assertEqual('ok',self.board.protocol.check(task,'interaction','fail','Train button not clicked','Operator')[0])
        self.assertEqual('broken',self.board.detail(task)['task']['status'])
        for step,_ in STAGES:
            self.assertEqual('ok',self.board.protocol.check(task,step,'pass','Successful fresh screenshot check','ChatGPT')[0])
        self.assertEqual('ok',self.board.protocol.sign(task,'operator','Observed production run and confirmed reward.')[0])
        self.assertEqual('ok',self.board.protocol.check(task,'regression','blocked','Test needs new capture','ChatGPT')[0])
        self.assertEqual('blocked',self.board.detail(task)['task']['status'])
        self.assertEqual('incomplete_checks',self.board.protocol.sign(task,'reviewer','Reviewed production logs and screenshots.')[0])
        self.assertFalse(self.board.detail(task)['qa']['round']['operator_signed_at'])

    def test_saved_step_evidence_replaces_duplicate_operator_note(self):
        task='STOREHOUSE_CHEST'
        self.assertEqual('ok',self.board.protocol.begin(task,'1.1.66','Operator')[0])
        self.assertEqual('ok',self.board.protocol.attach(task,self.coach_receipt(task),'Operator')[0])
        for step,_ in STAGES:
            self.assertEqual('ok',self.board.protocol.check(task,step,'pass','Tested on current emulator at '+step,'Operator')[0])
        # No second free-text form is needed when the seven stage notes exist.
        self.assertEqual('ok',self.board.protocol.sign(task,'operator','')[0])
        note=self.board.detail(task)['qa']['round']['operator_note']
        self.assertIn('postcondition',note)
        self.assertIn('interaction',note)
        self.assertEqual('working',self.board.detail(task)['task']['status'])
        # Reviewer still needs an actual independent review note.
        self.assertEqual('needs_evidence_note',self.board.protocol.sign(task,'reviewer','')[0])
        self.assertEqual('ok',self.board.protocol.sign(task,'reviewer','Independent logs and saved frame checked.')[0])
        self.assertEqual('verified',self.board.detail(task)['task']['status'])

    def test_failed_and_pending_results_can_be_saved_without_signing(self):
        key='TRAINING_TROOPS'
        self.assertEqual('ok',self.board.protocol.begin(key,'1.1.66','Operator')[0])
        # A failed action remains failed even if another independent step passes.
        self.assertEqual('ok',self.board.protocol.check(key,'interaction','fail',
            'Training button does not respond','Operator')[0])
        self.assertEqual('broken',self.board.detail(key)['task']['status'])
        self.assertEqual('ok',self.board.protocol.check(key,'preflight','pass',
            'Screen dimensions and DPI match','Operator')[0])
        self.assertEqual('broken',self.board.detail(key)['task']['status'])
        self.assertEqual('ok',self.board.protocol.check(key,'regression','pending',
            'Regression run not yet executed','Operator')[0])
        self.assertEqual('broken',self.board.detail(key)['task']['status'])
        self.assertEqual('ok',self.board.protocol.save_operator_note(key,
            'Training fails at unlock popup after selecting highest tier.','Operator')[0])
        self.assertEqual('broken',self.board.detail(key)['task']['status'])
        restored=Board(self.dbfile,ROOT/'catalog.json',self.diags,self.authfile).detail(key)
        self.assertEqual('Training fails at unlock popup after selecting highest tier.',
            restored['qa']['round']['operator_note'])
        self.assertEqual('fail',next(c['result'] for c in restored['qa']['checks']
             if c['step']=='interaction'))
        self.assertEqual('pending',next(c['result'] for c in restored['qa']['checks']
             if c['step']=='regression'))
        self.assertFalse(restored['qa']['round']['completed_at'])
        self.assertEqual('missing_coach_evidence',self.board.protocol.sign(key,
            'operator','Some notes.')[0])

    def test_operator_notes_persist_without_extra_signoff(self):
        key='ARENA'
        self.board.protocol.begin(key,'1.1.66','Operator')
        self.assertEqual('invalid',self.board.protocol.save_operator_note(key,
            'x'*1201,'Operator')[0])
        self.assertEqual('ok',self.board.protocol.save_operator_note(key,
            'New observation about a visible but inactive button.','Operator')[0])
        self.assertEqual('ok',self.board.protocol.save_operator_note(key,
            'New observation about a visible but inactive button.','Operator')[0])
        state=self.board.detail(key)
        self.assertEqual('testing',state['task']['status'])
        self.assertEqual('New observation about a visible but inactive button.',
            state['qa']['round']['operator_note'])
        self.assertFalse(state['qa']['round']['operator_signed_at'])

    def test_incomplete_checks_take_precedence_over_optional_operator_note(self):
        task='TRAINING_TROOPS'
        self.board.protocol.begin(task,'1.1.66','Operator')
        self.assertEqual('missing_coach_evidence',self.board.protocol.sign(task,'operator','')[0])
        self.board.protocol.attach(task,self.coach_receipt(task),'Operator')
        self.assertEqual('incomplete_checks',self.board.protocol.sign(task,'operator','')[0])
        self.board.protocol.check(task,'postcondition','fail','Train never started','Operator')
        self.assertEqual('incomplete_checks',self.board.protocol.sign(task,'operator','')[0])

    def test_open_issue_invalidates_earlier_joint_approval(self):
        task='TRAINING_TROOPS'
        self.board.protocol.begin(task,'1.1.66','Operator')
        self.board.protocol.attach(task,self.coach_receipt(task),'ChatGPT')
        for step,_ in STAGES:self.board.protocol.check(task,step,'pass','Verified after fresh action','Operator')
        self.assertEqual('ok',self.board.protocol.sign(task,'reviewer','Logs and code regression replayed safely.')[0])
        issue=self.board.add_issue(task,{'title':'Unexpected new popup','detail':'Blocked in game'},'Operator')
        self.assertIsNotNone(issue)
        self.assertEqual('broken',self.board.detail(task)['task']['status'])
        snapshot=self.board.detail(task)['qa']
        self.assertEqual('',snapshot['round']['reviewer_signed_at'])
        self.assertEqual('fail',next(x['result'] for x in snapshot['checks'] if x['step']=='postcondition'))
        self.board.edit_issue(issue,{'state':'closed'},'ChatGPT')
        self.assertEqual('incomplete_checks',self.board.protocol.sign(task,'operator','New run and behavior verified.')[0])

    def test_open_issue_blocks_joint_signoff(self):
        task='STOREHOUSE_CHEST'
        self.board.protocol.begin(task,'1.1.66','Operator')
        self.board.protocol.attach(task,self.coach_receipt(task),'ChatGPT')
        for step,_ in STAGES:self.board.protocol.check(task,step,'pass','Confirmed specific game event','Operator')
        issue=self.board.add_issue(task,{'title':'Still unresolved chest','detail':'Claim status unknown'},'Operator')
        self.assertEqual('incomplete_checks',self.board.protocol.sign(task,'reviewer','Logs and game status show complete.')[0])
        self.board.edit_issue(issue,{'state':'closed'},'ChatGPT')
        self.board.protocol.check(task,'postcondition','pass','Retested after issue was repaired','ChatGPT')
        self.assertEqual('ok',self.board.protocol.sign(task,'reviewer','Logs and game status show complete.')[0])
        self.assertEqual('working',self.board.detail(task)['task']['status'])
        self.board.protocol.begin(task,'1.1.67','Operator')
        self.assertEqual('testing',self.board.detail(task)['task']['status'])
        self.assertFalse(self.board.detail(task)['qa']['round']['reviewer_signed_at'])

    def test_concurrent_edits_are_not_overwritten(self):
        row=self.board.detail("ARENA")["task"]
        self.assertEqual("ok",self.board.edit_task("ARENA",{"version":row["version"],"notes":"A"},"Operator")[0])
        self.assertEqual("conflict",self.board.edit_task("ARENA",{"version":row["version"],"notes":"B"},"ChatGPT")[0])
        self.assertEqual("invalid",self.board.edit_task("ARENA",{"version":1,"status":"COMPLETED!"},"ChatGPT")[0])
        self.assertEqual("A",self.board.detail("ARENA")["task"]["notes"])

    def test_issue_lifecycle_and_audit_history(self):
        num=self.board.add_issue("INTEL",{"title":"Rescue: View ontbreekt","detail":"knop niet herkend","severity":"high"},"Operator")
        self.assertIsInstance(num,int)
        self.assertTrue(self.board.edit_issue(num,{"state":"closed"},"ChatGPT"))
        info=self.board.detail("INTEL")
        self.assertEqual("closed",info["issues"][0]["state"])
        self.assertEqual("ChatGPT",info["issues"][0]["closed_by"])
        self.assertIn("issue_closed",[e["kind"] for e in info["events"]])

    def test_imported_diagnostics_never_mark_task_complete(self):
        day=self.diags/"2026-11-15";day.mkdir()
        (day/"frame.json").write_text(json.dumps({"schema":1,"activity":"storehousechest",
            "kind":"stamina-collection-unconfirmed","timestamp":"2026-11-15T01:59:00Z",
            "image_file":"frame.png","log_tail":"private data should never appear"}))
        (day/"frame.png").write_bytes(b"\x89PNG\r\n\x1a\n"+b"\0"*20)
        self.assertEqual(1,self.board.sync_diagnostics(force=True))
        info=self.board.detail("STOREHOUSE_CHEST")
        self.assertEqual("not_tested",info["task"]["status"])
        self.assertEqual("error",info["diagnostics"][0]["severity"])
        self.assertNotIn("log_tail",str(info))
        self.assertTrue(self.board.diagnostic_image("2026-11-15/frame.json"))
        self.assertIsNone(self.board.diagnostic_image("../../etc/passwd"))
        self.assertEqual(0,self.board.sync_diagnostics(force=True))

    def test_live_feed_filters_pagination_and_redacted_debug(self):
        from server import iso_now
        day=self.diags/iso_now()[:10]
        day.mkdir()
        for n,(activity,kind) in enumerate((("storehousechest","stamina-collection-unconfirmed"),
                                            ("intel","task-error-homenotfoundexception"),
                                            ("recognition","template-scan-not-found"))):
            frame=day/("event-"+str(n)+".json")
            frame.write_text(json.dumps({
                "schema":1,"activity":activity,"kind":kind,
                "timestamp":iso_now(),"platform":"Windows 11",
                "log_tail":"2026-11-15T01:00:00+01:00 WARN - OCR failed\nAuthorization: Bearer mySecretToken123456\nDEBUG - candidate score=47"
            }))
        self.assertEqual(3,self.board.sync_diagnostics(force=True))
        all_items=self.board.live_diagnostics(hours=0,limit=2)
        self.assertEqual(3,all_items["total"])
        self.assertEqual(2,len(all_items["items"]))
        self.assertEqual(1,len(self.board.live_diagnostics(hours=0,offset=2)["items"]))
        self.assertEqual(1,self.board.live_diagnostics(hours=0,task="STOREHOUSE_CHEST")["total"])
        self.assertEqual(1,self.board.live_diagnostics(hours=0,task="system")["total"])
        self.assertEqual(1,self.board.live_diagnostics(hours=0,severity="warning")["total"])
        self.assertEqual(1,self.board.live_diagnostics(hours=0,query="storehouse")["total"])
        self.assertEqual(0,self.board.live_diagnostics(hours=0,query="%_")["total"])
        self.assertIsNotNone(all_items["seconds_since_latest"])
        detail=self.board.diagnostic_detail(day.name+"/event-0.json")
        self.assertEqual(3,len(detail["log_lines"]))
        self.assertEqual("WARN",detail["log_lines"][0]["level"])
        self.assertEqual("DEBUG",detail["log_lines"][2]["level"])
        self.assertIn("[REDACTED]",detail["log_lines"][1]["text"])
        self.assertNotIn("mySecretToken123456",str(detail))
        self.assertIsNone(self.board.diagnostic_detail("../../private/auth.json"))
        with self.assertRaises(ValueError):self.board.live_diagnostics(hours=55)
        with self.assertRaises(ValueError):self.board.live_diagnostics(limit=800)


    def test_template_audit_requires_schema_and_limited_file_size(self):
        report=self.root/'report.json'
        self.assertIsNone(read_template_audit(report))
        report.write_text('{ invalid json')
        self.assertIsNone(read_template_audit(report))
        report.write_text(json.dumps({'schema':1,'templates':[{'name':'A'}]*523,'summary':{'registered':523}}))
        self.assertEqual(523,len(read_template_audit(report)['templates']))
        report.write_bytes(b' '*2_000_001)
        self.assertIsNone(read_template_audit(report))

    def test_password_signed_cookie_and_http_protection(self):
        self.assertTrue(self.board.password_ok(self.password))
        self.assertFalse(self.board.password_ok("invalid"))
        token=self.board.make_cookie()
        self.assertTrue(self.board.valid_cookie(token))
        self.assertFalse(self.board.valid_cookie(token+"wrong"))
        server=ThreadingHTTPServer(("127.0.0.1",0),Handler);server.board=self.board
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        def req(method,path,body=None,cookie=None,origin=None):
            connection=HTTPConnection("127.0.0.1",server.server_port,timeout=5)
            headers={"Host":"localhost:9097"}
            if body is not None:headers["Content-Type"]="application/json"
            if cookie:headers["Cookie"]="qa_session="+cookie
            if origin:headers["Origin"]=origin
            connection.request(method,path,json.dumps(body) if body is not None else None,headers)
            result=connection.getresponse();content=result.read();head=dict(result.getheaders());status=result.status
            connection.close()
            return status,json.loads(content) if head.get("Content-Type","").startswith("application/json") else content,head
        try:
            self.assertEqual(401,req("GET","/api/tasks")[0])
            self.assertEqual(401,req("GET","/api/template-audit")[0])
            self.assertEqual(401,req("POST","/api/login",{"password":"bad"})[0])
            status,_,headers=req("POST","/api/login",{"password":self.password})
            self.assertEqual(200,status)
            self.assertIn("HttpOnly",headers["Set-Cookie"]);self.assertIn("Secure",headers["Set-Cookie"])
            session=headers["Set-Cookie"].split(";",1)[0].split("=",1)[1]
            status,data,_=req("GET","/api/tasks",cookie=session)
            self.assertEqual(200,status);self.assertEqual(62,data["total"])
            self.assertEqual(401,req("POST","/api/tasks/ARENA/qa/start",{"build":"1.1.66"})[0])
            status,new,_=req("POST","/api/tasks/ARENA/qa/start",{"build":"1.1.66"},session)
            self.assertEqual(200,status)
            self.assertEqual(7,len(new['qa']['checks']))
            self.assertEqual('testing',self.board.detail('ARENA')['task']['status'])
            status,notes,_=req("POST","/api/tasks/ARENA/qa/operator-note",
                {"note":"The game has a popup and claim remains unavailable."},session)
            self.assertEqual(200,status)
            self.assertIn('popup',notes['qa']['round']['operator_note'])
            self.assertFalse(notes['qa']['round']['operator_signed_at'])
            self.assertEqual(400,req("POST","/api/tasks/ARENA/qa/operator-sign",{"note":"I saw the correct game result."},session)[0])
            with patch('server.TEMPLATE_AUDIT_PATH', self.root/'missing-audit.json'):
                self.assertEqual(503,req("GET","/api/template-audit",cookie=session)[0])
            self.assertEqual(403,req("POST","/api/sync",{},session,"https://evil.example")[0])
            status,feed,_=req("GET","/api/diagnostics?hours=0&limit=10",cookie=session)
            self.assertEqual(200,status)
            self.assertIn("seconds_since_latest",feed)
            self.assertEqual(400,req("GET","/api/diagnostics?severity=garbage",cookie=session)[0])
            self.assertEqual(401,req("GET","/api/diagnostics/detail?path=bad",cookie=None)[0])
            row=self.board.detail("ARENA")["task"]
            status,data,_=req("PATCH","/api/tasks/ARENA",{"version":row["version"],"status":"testing"},session)
            self.assertEqual(200,status);self.assertEqual("testing",data["task"]["status"])
            self.assertEqual(400,req("PATCH","/api/tasks/ARENA",{"version":row["version"],"status":"verified"},session)[0])
        finally:
            server.shutdown();server.server_close();thread.join(timeout=2)
if __name__=="__main__":unittest.main()