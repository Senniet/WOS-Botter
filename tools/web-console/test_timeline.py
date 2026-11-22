#!/usr/bin/env python3
"""Unit tests for scheduler timeline sorting and repeated-run preservation."""
import tempfile
from pathlib import Path
import unittest
from timeline import TimelineArchive, snapshot, valid_profile


def row(id, attempt, next_time, outcome="NOT_RUN", enabled=True, running=False,
        last_success=None):
    return {"id": id, "name": id.replace("_", " ").title(),
            "lastAttempt": attempt, "next": next_time, "lastSuccess": last_success,
            "outcome": outcome, "enabled": enabled, "executing": running,
            "queued": True}


class TimelineTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.archive = TimelineArchive(Path(self.temp.name) / "task-runs.db")

    def test_sorted_schedule_and_history_tracks_repeats(self):
        first = "2026-11-19T14:00:00"
        rows = [
            row("TRAINING_TROOPS", first, "2026-11-19T18:00:00", "SUCCEEDED"),
            row("ARENA", "2026-11-19T14:30:00", "2026-11-19T16:00:00", "FAILED"),
            row("MAIL_REWARDS", None, "2026-11-19T15:30:00"),
            row("DISABLED", None, "2026-11-19T15:00:00", enabled=False),
        ]
        result = snapshot("1", rows, self.archive)
        self.assertEqual([x["task_id"] for x in result["overdue"]],
                         ["MAIL_REWARDS", "ARENA", "TRAINING_TROOPS"])
        self.assertEqual([x["task_id"] for x in result["recent"]],
                         ["ARENA", "TRAINING_TROOPS"])
        second = "2026-11-19T15:15:00"
        rows[0] = row("TRAINING_TROOPS", second, "2026-11-19T20:00:00",
                      "RUNNING", running=True)
        result = snapshot("1", rows, self.archive)
        self.assertEqual([x["task_id"] for x in result["running"]], ["TRAINING_TROOPS"])
        self.assertNotIn("TRAINING_TROOPS", [x["task_id"] for x in result["upcoming"]])
        self.assertEqual(result["recent"][0]["outcome"], "FAILED")
        rows[0] = row("TRAINING_TROOPS", second, "2026-11-19T20:00:00", "SUCCEEDED")
        result = snapshot("1", rows, self.archive)
        self.assertEqual(result["recent"][0]["outcome"], "SUCCEEDED")
        attempts = [x for x in result["recent"] if x["task_id"] == "TRAINING_TROOPS"]
        self.assertEqual(len(attempts), 2)
        self.assertEqual(attempts[0]["attempt_at"], second)
        self.assertEqual(attempts[1]["attempt_at"], first)

    def test_completed_state_does_not_regress_after_engine_restart(self):
        attempt = "2026-11-19T12:00:00"
        self.archive.observe("1", [row("ARENA", attempt, "", "FAILED")])
        self.archive.observe("1", [row("ARENA", attempt, "", "NOT_RUN")])
        self.assertEqual(self.archive.recent("1")[0]["outcome"], "FAILED")

    def test_newly_scheduled_task_has_no_fake_successful_execution(self):
        attempt = "2026-11-19T12:00:00"
        self.archive.observe("1", [row("EVENT_HERO_MISSION", attempt, "", "NOT_RUN",
                                       last_success=attempt)])
        self.assertEqual(self.archive.recent("1"), [])
        self.archive.observe("1", [row("EVENT_HERO_MISSION", attempt, "", "SUCCEEDED",
                                       last_success=attempt)])
        self.assertEqual(self.archive.recent("1")[0]["outcome"], "SUCCEEDED")

    def test_interrupted_task_is_not_stuck_as_running(self):
        attempt = "2026-11-19T12:00:00"
        self.archive.observe("1", [row("ARENA", attempt, "", "RUNNING", running=True)])
        self.archive.observe("1", [row("ARENA", attempt, "", "NOT_RUN")])
        self.assertEqual(self.archive.recent("1")[0]["outcome"], "UNKNOWN")

    def test_stopped_scheduler_does_not_show_interrupted_task_as_running(self):
        attempt = "2026-11-19T20:15:33"
        active = [row("HERO_RECRUITMENT", attempt, "2026-11-19T20:25:00",
                      "RUNNING", running=True)]
        live = snapshot("1", active, self.archive)
        self.assertEqual(len(live["running"]), 1)
        stopped = snapshot("1", active, self.archive, scheduler_running=False)
        self.assertEqual(stopped["running"], [])
        self.assertEqual(stopped["recent"], [])

    def test_newly_scheduled_but_not_run_has_no_last_attempt(self):
        rows = [row("EVENT_HERO_MISSION", "2026-11-19T20:48:35",
                    "2026-11-19T21:00:00", "NOT_RUN",
                    last_success="2026-11-19T20:48:35")]
        screen = snapshot("1", rows, self.archive, scheduler_running=False)
        self.assertEqual(screen["overdue"][0]["last_attempt"], "")
        self.assertEqual(screen["recent"], [])

    def test_future_remains_in_upcoming_and_overdue_is_separate(self):
        from datetime import datetime, timedelta
        future = (datetime.now()+timedelta(hours=2)).isoformat()
        past = (datetime.now()-timedelta(hours=2)).isoformat()
        result = snapshot("1", [row("FUTURE", None, future), row("PAST", None, past)], self.archive)
        self.assertEqual([x["task_id"] for x in result["upcoming"]], ["FUTURE"])
        self.assertEqual([x["task_id"] for x in result["overdue"]], ["PAST"])

    def test_profiles_are_isolated(self):
        attempt = "2026-11-19T12:00:00"
        self.archive.observe("1", [row("ARENA", attempt, "", "SUCCEEDED")])
        self.archive.observe("2", [row("MAIL_REWARDS", attempt, "", "FAILED")])
        self.assertEqual([r["task_id"] for r in self.archive.recent("1")], ["ARENA"])
        self.assertEqual([r["task_id"] for r in self.archive.recent("2")], ["MAIL_REWARDS"])

    def test_profile_validation(self):
        for value in ["", "../2", "1;DROP TABLE", "-1", "1?task=etc"]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                valid_profile(value)
        self.assertEqual(valid_profile("24"), "24")


if __name__ == "__main__":
    unittest.main()
