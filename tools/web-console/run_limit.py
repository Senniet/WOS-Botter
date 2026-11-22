"""Opt-in active/rest schedule for SurvivalOps; game lifecycle through Serve-Emu ADB."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from pathlib import Path
import json
import os
import threading

CONFIG = Path.home() / ".config/survivalops-web/run-limit.json"
LOCK = threading.RLock()
DEFAULT = {
    "enabled": False, "maxMinutes": 180, "restMinutes": 60,
    "startedAt": None, "stopIssued": False,
    "pendingClose": False, "gameClosed": False, "restUntil": None,
}


def read():
    with LOCK:
        try:
            config = {**DEFAULT, **json.loads(CONFIG.read_text())}
            if (type(config["enabled"]) is not bool
                    or type(config["maxMinutes"]) is not int
                    or not 15 <= config["maxMinutes"] <= 1440
                    or type(config["restMinutes"]) is not int
                    or not 5 <= config["restMinutes"] <= 1440):
                raise ValueError("invalid settings")
            return config
        except (OSError, ValueError, TypeError):
            return dict(DEFAULT)


def write(config):
    CONFIG.parent.mkdir(parents=True, exist_ok=True)
    dest = CONFIG.with_suffix(".tmp")
    dest.write_text(json.dumps(config, indent=2))
    os.chmod(dest, 0o600)
    os.replace(dest, CONFIG)


def configure(data):
    if (type(data.get("enabled")) is not bool
            or type(data.get("maxMinutes")) is not int
            or not 15 <= data["maxMinutes"] <= 1440
            or type(data.get("restMinutes")) is not int
            or not 5 <= data["restMinutes"] <= 1440):
        raise ValueError("Enabled must be boolean; runtime 15–1440 and rest 5–1440 minutes")
    with LOCK:
        state = read()
        state.update(enabled=data["enabled"], maxMinutes=data["maxMinutes"],
                     restMinutes=data["restMinutes"])
        # Disabling automatic operation cancels a future autonomous restart,
        # but keeps any pending safe close and gameClosed state intact.
        if not data["enabled"]:
            state["restUntil"] = None
        write(state)
        return state


def observe(running, now=None, stop=None, *, executing=False, close_app=None,
            launch_app=None, start=None):
    """Process active/rest cycle once per observation.

    Shutdown is ordered: stop scheduler -> wait for active routine -> close game.
    Restart only after rest deadline and a confirmed game close. Manual starts
    override pending rest. A manual stop does not establish a new rest period.
    """
    if now is None:
        now = datetime.now(timezone.utc)
    with LOCK:
        state = read()
        if not running:
            if state["pendingClose"] and not executing:
                if close_app is None:
                    raise RuntimeError("Missing close-app callback")
                close_app()
                state.update(pendingClose=False, gameClosed=True)
                write(state)

            if state["startedAt"] is not None or state["stopIssued"]:
                state.update(startedAt=None, stopIssued=False)
                write(state)

            deadline = state["restUntil"]
            if state["enabled"] and deadline and not state["pendingClose"] and not executing:
                if now >= datetime.fromisoformat(deadline):
                    if start is None:
                        raise RuntimeError("Missing start-scheduler callback")
                    # Clear deadline only after Java confirms restart. If Java
                    # declines start, retry next observation without looping.
                    start()
                    state["restUntil"] = None
                    write(state)
            return state

        # A deliberate manual start takes precedence over a pending sleep.
        if state["restUntil"] and not state["stopIssued"]:
            state["restUntil"] = None
            write(state)

        if state["gameClosed"]:
            if launch_app is None:
                raise RuntimeError("Missing launch-app callback")
            launch_app()
            state.update(gameClosed=False, pendingClose=False)
            write(state)

        if state["startedAt"] is None:
            state["startedAt"] = now.isoformat()
            write(state)

        if state["enabled"] and not state["stopIssued"]:
            elapsed = (now - datetime.fromisoformat(state["startedAt"])).total_seconds()
            if elapsed >= state["maxMinutes"] * 60:
                if stop is None:
                    raise RuntimeError("Missing stop callback")
                stop()
                state.update(stopIssued=True, pendingClose=True,
                             restUntil=(now + timedelta(minutes=state["restMinutes"])).isoformat())
                write(state)
        return state


def start_monitor(engine, adb=None, emulator_endpoint=None, interval=5):
    def game_action(*command):
        if adb is None or emulator_endpoint is None:
            raise RuntimeError("Game control not configured")
        endpoint = emulator_endpoint()
        adb("-s", endpoint, "shell", *command, timeout=12, limit=2048)

    def poll():
        import time
        while True:
            try:
                code, raw = engine("/api/status")
                if code == 200:
                    status = json.loads(raw)

                    def engine_action(action):
                        code, _ = engine("/api/engine", "POST",
                                         json.dumps({"action": action}).encode())
                        if code // 100 != 2:
                            raise RuntimeError("Java engine rejected " + action)

                    observe(bool(status.get("running")),
                            executing=bool(status.get("executing")),
                            stop=lambda: engine_action("stop"),
                            start=lambda: engine_action("start"),
                            close_app=lambda: game_action("am", "force-stop", "com.gof.global"),
                            launch_app=lambda: game_action(
                                "monkey", "-p", "com.gof.global", "-c",
                                "android.intent.category.LAUNCHER", "1"))
            except (OSError, ValueError, RuntimeError) as exc:
                print("Run limit monitor: " + type(exc).__name__ + ": " + str(exc)[:180], flush=True)
            time.sleep(interval)

    threading.Thread(target=poll, name="survivalops-run-limit", daemon=True).start()
