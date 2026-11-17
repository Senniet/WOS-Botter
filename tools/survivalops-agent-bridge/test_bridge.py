"""Stdlib regression tests for the no-side-effect diagnostic trigger gateway."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("repair_bridge", Path(__file__).with_name("bridge.py"))
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)


class FakeResponse:
    status = 202
    def __enter__(self): return self
    def __exit__(self, *args): pass


class BridgeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.inbox = self.root / "inbox"
        self.day = self.inbox / "2026-11-15"
        self.day.mkdir(parents=True)
        self.out = self.root / "out"

    def tearDown(self): self.tmp.cleanup()

    def write(self, moment, activity="training", kind="task-error-homenotfoundexception", **extras):
        path = self.day / (moment + "-aaa.json")
        path.write_text(json.dumps({"schema": 1, "activity": activity, "kind": kind,
                                    "timestamp": "2026-11-15T18:00:00Z", **extras}))
        return path

    def test_bootstrap_ignores_history_then_sends_only_new_errors(self):
        self.write("20261115T180000Z")
        b = bridge.RepairBridge(self.inbox, self.out)
        self.assertEqual(b.scan_once()["bootstrapped"], 1)
        self.assertEqual(b.scan_once()["processed"], 0)
        self.write("20261115T180100Z", activity="recognition", kind="game-home")
        incident = self.write("20261115T180200Z", log_tail="secret private data")
        self.assertEqual(b.scan_once()["processed"], 1)
        report = json.loads((self.out / "reports" / (incident.stem + ".json")).read_text())
        self.assertEqual(report["state"], "dry-run")
        self.assertNotIn("secret private data", json.dumps(report))
        self.assertEqual(b.scan_once()["processed"], 0)

    def test_1000_historical_files_never_resend_after_restart(self):
        for i in range(70):
            self.write(f"20261115T18{i//60:02d}{i%60:02d}00Z")
        b = bridge.RepairBridge(self.inbox, self.out)
        b.scan_once()
        self.assertEqual(b.scan_once()["processed"], 0)
        self.assertEqual(bridge.RepairBridge(self.inbox, self.out).scan_once()["processed"], 0)

    def test_workspace_trigger_idempotency_and_secret_not_returned(self):
        called = []
        def fake(req, timeout):
            called.append((req.full_url, dict(req.headers), json.loads(req.data)))
            return FakeResponse()
        event = {"incident_id": "abc", "activity": "training", "kind": "task-error"}
        with patch.dict("os.environ", {"CHATGPT_WORKSPACE_AGENT_TOKEN": "privateSecretToken",
                                       "CHATGPT_WORKSPACE_AGENT_CHANNEL": "agtch_12345"}):
            result = bridge.call_workspace_agent(event, "abc", opener=fake)
        self.assertEqual(result["state"], "accepted")
        self.assertNotIn("privateSecretToken", str(result))
        self.assertIn("Idempotency-key", called[0][1])
        self.assertTrue(called[0][0].startswith("https://api.chatgpt.com/v1/workspace_agents/"))
        self.assertTrue(called[0][2]["conversation_key"].startswith("survivalops-"))

    def test_repeated_same_live_failure_is_rate_limited(self):
        self.write("20261115T180000Z")
        self.write("20261115T180100Z")
        b = bridge.RepairBridge(self.inbox, self.out, mode="workspace-agent", include_existing=True)
        with patch.object(bridge, "call_workspace_agent", return_value={"state": "accepted"}) as trigger:
            result = b.scan_once()
        self.assertEqual(result["processed"], 1)
        self.assertEqual(trigger.call_count, 1)
        self.assertEqual(b.scan_once()["processed"], 0)

    def test_failed_trigger_remains_for_retry(self):
        self.write("20261115T180000Z")
        b = bridge.RepairBridge(self.inbox, self.out, mode="workspace-agent", include_existing=True)
        with patch.object(bridge, "call_workspace_agent", side_effect=RuntimeError("network down")):
            result = b.scan_once()
        self.assertEqual(result["processed"], 0)
        self.assertEqual(result["failed"], 1)
        with patch.object(bridge, "call_workspace_agent", return_value={"state": "accepted"}):
            self.assertEqual(b.scan_once()["processed"], 1)

    def test_scope_excludes_template_misses_and_path_traversal(self):
        self.assertFalse(bridge.is_failure({"activity":"taskcoachtraining-troops", "kind":"miss-button"}))
        self.assertFalse(bridge.is_failure({"activity":"recognition", "kind":"train-button"}))
        self.assertTrue(bridge.is_failure({"activity":"training", "kind":"task-error-homenotfoundexception"}))
        file = self.write("20261115T180000Z", image_file="../../secret.png")
        b = bridge.RepairBridge(self.inbox, self.out, include_images=True, include_existing=True)
        self.assertEqual(b.scan_once()["processed"], 1)
        self.assertNotIn("secret.png", (self.out / "reports" / (file.stem + ".json")).read_text())

    def test_first_run_explicit_history_mode(self):
        self.write("20261115T180000Z")
        b = bridge.RepairBridge(self.inbox, self.out, include_existing=True)
        self.assertEqual(b.scan_once()["processed"], 1)
        self.assertEqual(b.scan_once()["processed"], 0)


if __name__ == "__main__": unittest.main()
