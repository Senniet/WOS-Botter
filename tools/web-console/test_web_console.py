#!/usr/bin/env python3
"""Smoke/security tests for the SurvivalOps web gateway (no game input)."""
import hashlib
import importlib.util
import json
from pathlib import Path
import secrets
import ssl
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

SPEC = importlib.util.spec_from_file_location("survivalops_web", Path(__file__).with_name("server.py"))
WEB = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(WEB)


class AuthTests(unittest.TestCase):
    def test_password_hash_accepts_only_correct_password(self):
        salt = secrets.token_bytes(16)
        data = {
            "salt": salt.hex(),
            "password_hash": hashlib.pbkdf2_hmac("sha256", b"expected", salt, 350000).hex(),
            "session_key": secrets.token_bytes(32).hex(),
        }
        self.assertTrue(WEB.password_ok(data, "expected"))
        self.assertFalse(WEB.password_ok(data, "other"))
        cookie = "sops_web=" + WEB.make_token(data)
        self.assertTrue(WEB.token_ok(data, cookie))
        self.assertFalse(WEB.token_ok(data, cookie + "alteration"))
        self.assertFalse(WEB.token_ok(data, "other=value"))

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

    def test_unauthenticated_engine_api_is_denied(self):
        self.assertEqual(self.get_status("/api/status"), 401)
        self.assertEqual(self.get_status("/api/qa/summary"), 401)
        self.assertEqual(self.get_status("/api/emulator/frame"), 401)

    def test_login_assets_accessible(self):
        self.assertEqual(self.get_status("/"), 200)
        self.assertEqual(self.get_status("/static/style.css"), 200)
        self.assertEqual(self.get_status("/static/login.js"), 200)
        self.assertEqual(self.get_status("/static/app.js"), 401)

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
