#!/usr/bin/env python3
"""Smoke/security tests for the SurvivalOps web gateway (no game input)."""
import importlib.util
import json
from pathlib import Path
import ssl
import sys
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

# The gateway imports the local H.264 live-stream module alongside server.py.
# Spec-based loading does not automatically add the source directory to sys.path.
sys.path.insert(0, str(Path(__file__).resolve().parent))
SPEC = importlib.util.spec_from_file_location("survivalops_web", Path(__file__).with_name("server.py"))
WEB = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(WEB)


class GatewayTests(unittest.TestCase):
    def test_operator_interface_uses_english(self):
        """English game terminology should remain consistent across all web screens."""
        import re
        source = Path(__file__).parent
        html = (source / "index.html").read_text(encoding="utf-8")
        app = (source / "static/app.js").read_text(encoding="utf-8")
        self.assertIn('<html lang="en">', html)
        self.assertIn('<section data-view="timeline"', html)
        self.assertIn('id="dashboard-timeline"', html)
        self.assertIn('id="timeline-upcoming"', html)
        self.assertIn('id="timeline-history"', html)
        self.assertIn('setInterval(loadTimeline,5000)', app)
        self.assertNotIn('id="logout"', html)
        self.assertNotIn('id="password-form"', html)
        self.assertNotIn('/api/change-password', app)
        self.assertIn("'en-GB'", app)
        self.assertIn('Daily Tasks', app)
        self.assertIn('Hero Recruitment', app)
        self.assertIn('Claim Recruitment Points Chest', app)
        dutch_ui = re.compile(
            r"\b(?:instellingen|takenbeheer|wachtwoord|inloggen|overzicht|"
            r"ingeschakeld|uitgeschakeld|beloningen|troepen|gebruiken|"
            r"verwijderen|opgeslagen|verversen|wachtrij|beveiligde|"
            r"verzamelen|profielen|laad|beschikbaar|niet|geen|voor)\b",
            re.IGNORECASE,
        )
        for label, content in (("index", html), ("app", app)):
            self.assertIsNone(dutch_ui.search(content),
                              f"Dutch operator text remains in {label}")

    def test_manual_control_only_when_no_queue_can_execute(self):
        allowed = WEB.manual_input_allowed
        self.assertTrue(allowed({"running": False, "executing": False, "queues": []}))
        self.assertTrue(allowed({"running": True, "executing": False,
                                 "queues": [{"paused": True, "executing": False}]}))
        self.assertFalse(allowed({"running": True, "executing": False, "queues": []}))
        self.assertFalse(allowed({"running": True, "executing": False,
                                  "queues": [{"paused": False, "executing": False}]}))
        self.assertFalse(allowed({"running": True, "executing": True,
                                  "queues": [{"paused": True, "executing": True}]}))
        self.assertFalse(allowed({"running": True, "executing": False,
                                  "queues": [{"paused": True}, {"paused": False}]}))
        self.assertFalse(allowed({"running": True, "executing": False,
                                  "queues": [{"paused": True}, {"paused": True, "executing": True}]}))

    def test_live_player_shared_on_desktop_and_mobile(self):
        source = Path(__file__).parent
        script = (source / "static/emulator-mobile.js").read_text(encoding="utf-8")
        html = (source / "index.html").read_text(encoding="utf-8")
        self.assertIn('id="emu-manual-toggle"', html)
        self.assertIn("frame.src = '/api/emulator/live'", script)
        self.assertIn("return emulatorManualState().blocked;", script)
        self.assertIn("frame.removeAttribute('src')", script)
        self.assertIn("native.removeAttribute('src')", script)
        self.assertNotIn('id="emu-live" src=', html)
        self.assertNotIn("nativeWorkerUnavailable", script)

    def test_qa_directory_traversal_is_not_available(self):
        self.assertIsNone(WEB.qa_indexed_record("../../etc/passwd"))
        self.assertIsNone(WEB.qa_indexed_record("2026-11-18/../secret.json"))


class LiveReadonlyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base = "https://10.0.0.100:9098"
        cls.context = ssl._create_unverified_context()

    def get_status(self, path):
        try:
            with urlopen(Request(self.base + path), context=self.context, timeout=5) as response:
                return response.status
        except HTTPError as err:
            return err.code

    def test_health_exposes_only_online_flag(self):
        self.assertEqual(self.get_status("/health"), 200)

    def test_lan_console_is_available_without_a_cookie(self):
        with urlopen(self.base + "/", context=self.context, timeout=5) as response:
            html = response.read().decode("utf-8")
        self.assertIn('id="dashboard-timeline"', html)
        self.assertNotIn('id="login-form"', html)
        self.assertEqual(self.get_status("/static/style.css"), 200)
        self.assertEqual(self.get_status("/static/app.js"), 200)
        self.assertEqual(self.get_status("/api/status"), 200)
        self.assertEqual(self.get_status("/api/timeline?profile=1"), 200)
        self.assertEqual(self.get_status("/api/qa/summary"), 200)

    def post_status(self, path, headers=None, body=b"{}"):
        request = Request(self.base + path, data=body, method="POST",
                          headers=headers or {})
        try:
            with urlopen(request, context=self.context, timeout=5) as response:
                return response.status
        except HTTPError as err:
            return err.code

    def test_mutations_keep_origin_and_request_header_protection(self):
        # The request must be rejected before it can reach the engine.
        self.assertEqual(self.post_status("/api/engine"), 403)
        self.assertEqual(self.post_status("/api/engine", {
            "Host": "10.0.0.100:9098",
            "Origin": "https://other-host.example",
            "X-SurvivalOps-Request": "web"
        }), 403)

    def test_login_and_password_routes_are_removed(self):
        header = {"X-SurvivalOps-Request": "web"}
        self.assertEqual(self.get_status("/api/session"), 404)
        self.assertEqual(self.get_status("/static/login.js"), 404)
        self.assertEqual(self.post_status("/login", header), 404)
        self.assertEqual(self.post_status("/api/change-password", header), 404)
        self.assertEqual(self.post_status("/api/logout", header), 404)

    def test_unknown_paths_fail_closed(self):
        self.assertEqual(self.get_status("/../../etc/passwd"), 404)

    def test_qa_readonly_sql_schema(self):
        if not WEB.QA_DB.exists():
            self.skipTest("Existing QA archive not installed")
        summary = WEB.qa_summary()
        self.assertGreaterEqual(summary["total_tasks"], 62)
        self.assertIn("diagnostics", summary)
        for report in summary["diagnostics"][:3]:
            self.assertIsNotNone(WEB.qa_details(report["path"]))


if __name__ == "__main__":
    unittest.main()
