#!/usr/bin/env python3
import base64
from http.client import HTTPConnection
import hashlib
from http.server import ThreadingHTTPServer
import json
from pathlib import Path
import tempfile
import threading
import unittest
from server import Board, Handler

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
    def tearDown(self):self.temp.cleanup()

    def test_61_tasks_and_restarts_preserve_verified(self):
        self.assertEqual(61,self.board.summary()["total"])
        row=self.board.detail("STOREHOUSE_CHEST")["task"]
        state,new=self.board.edit_task(row["key"],{"version":row["version"],
         "status":"verified","notes":"Live bevestigd in emulator"},"Operator")
        self.assertEqual("ok",state);self.assertEqual("Operator",new["verified_by"])
        after=Board(self.dbfile,ROOT/"catalog.json",self.diags,self.authfile).detail("STOREHOUSE_CHEST")["task"]
        self.assertEqual("verified",after["status"])
        self.assertEqual("Live bevestigd in emulator",after["notes"])

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
            self.assertEqual(401,req("POST","/api/login",{"password":"bad"})[0])
            status,_,headers=req("POST","/api/login",{"password":self.password})
            self.assertEqual(200,status)
            self.assertIn("HttpOnly",headers["Set-Cookie"]);self.assertIn("Secure",headers["Set-Cookie"])
            session=headers["Set-Cookie"].split(";",1)[0].split("=",1)[1]
            status,data,_=req("GET","/api/tasks",cookie=session)
            self.assertEqual(200,status);self.assertEqual(61,data["total"])
            self.assertEqual(403,req("POST","/api/sync",{},session,"https://evil.example")[0])
            row=self.board.detail("ARENA")["task"]
            status,data,_=req("PATCH","/api/tasks/ARENA",{"version":row["version"],"status":"testing"},session)
            self.assertEqual(200,status);self.assertEqual("testing",data["task"]["status"])
            self.assertEqual(409,req("PATCH","/api/tasks/ARENA",{"version":row["version"],"status":"verified"},session)[0])
        finally:
            server.shutdown();server.server_close();thread.join(timeout=2)
if __name__=="__main__":unittest.main()