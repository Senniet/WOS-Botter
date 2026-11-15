# SurvivalOps task audit

`audit_tasks.py` enumerates **every scheduler task**, its Java factory and
implementation, possible test references, saved-frame test candidates, and
recent diagnostic incident types. It can capture one screenshot through the
serve-emu **GET** API, without sending touches, keys, ADB shell commands or
other game input.

Run from the repository root:

```bash
python3 tools/task-audit/audit_tasks.py --strict
python3 -m unittest discover -s tools/task-audit -p 'test_*.py'
```

The generated inventory is `docs/qa/survivalops-task-inventory.md`.
The live screenshot is retained outside the repository, under
`~/.cache/survivalops-audit/latest-readonly.png` by default. It must not be
committed to GitHub: it may contain account/private chat information.
Use `--no-live` when the emulator is offline or for CI.

## Interpretation

- A registered factory is **not** proof a task works.
- A unit-test reference is **not** proof of live completion; the candidate
  count intentionally includes approximate references for triage.
- A saved-frame test is **not** proof that tapping produces the expected
  game state on the currently running Android image.
- An incident snapshot shows observed failure context, not necessarily a
  persistent current failure or a unique task execution.
- A full-pass result requires an actual `before -> action -> after` state
  transition, explicit success/block/error classification, a bounded retry
  and a correct next schedule.

## Safe live programme

1. Reconfirm device serial, screenshot geometry, WOS build, and 0 UI margin.
2. Observe the current screen before navigating. If a task is unavailable,
   do not force it or mark it passed.
3. Verify an expected icon/template in the correct ROI. Use a secondary
   signal when nearby elements look similar (e.g. OCR + colors + template).
4. Execute only permitted free actions, serializing ownership with the active
   bot. Observe the resulting screen on a fresh frame; report an actual
   capacity/locked/busy game response separately from input failure.
5. Save a minimally redacted fixture with before/after evidence and run the
   task's unit/vision tests. Never include account IDs, aliases or chat logs.
6. Record outcome, latency, retries, next attempt, version and whether a
   real live pass was demonstrated. Do not reuse a cached image across taps.

The registry currently contains conditional events, lifecycle actions and a
user-supplied Custom Task. Those cannot be exhausted with one blind sweep;
feature availability, resources and account state determine safe testability.
No premium purchases, FC upgrades or irreversible alliance actions are part
of the automatic test programme.
