# Emulator-scoped recognition: Serve-EMU policy, safe migration

Status: **implementation + unit regressions; runtime savings and live game
success require a fresh Windows Task Coach run on the new release**.

## Root cause confirmed in code

The selected emulator was routed through `EmulatorController.initialize()` to
one specific ADB/backend class. The *vision layer* still used one common
`TemplatesEnum` asset pool for all five emulator types. `TemplateSearchHelper`
then made up to three independent searches for a failed named template:
colour, multiscale, monochrome, potentially multiplied by explicit retries
and alternate image IDs. **Three method checks are not three emulators.**

## Policy for all task routines, not another task-specific workaround

The selected backend now exposes its exact `EmulatorType`, read by the common
`TemplateSearchHelper` **at search time**, not once when helpers are created.
`EmulatorController` also checks the same policy on its own direct template
matching APIs, so routine code cannot bypass a retired variant simply by
skipping `TemplateSearchHelper`.

For `SERVE_EMU` on the proven 720×1280 UI:

- **Five obsolete names** blocked centrally wherever called:
  `SERVE_EMU_EXPLORATION_TITLE`, `EXPLORATION_SCREEN_TITLE`,
  `SERVE_EMU_IDLE_INCOME_TITLE`, `EXPLORATION_IDLE_INCOME_TITLE`,
  `SERVE_EMU_WORLD_ANCHOR`. Both current replacement assets remain active.
- On **four verified passive titles** (`SERVE_EMU_EXPLORATION_TITLE_NOV_2026`,
  `SERVE_EMU_IDLE_INCOME_TITLE_NOV_2026`, current Alliance header and current
  Alliance Tech header), a regular single-match lookup keeps exact colour
  recognition and configured attempts, but stops automatically adding two
  expensive scale/mono fallback methods after an exact miss. A failed title
  is a failed title, NOT authority to click an approximate button.
- The general Home navigator uses **one exact Reconnect check first** on
  Serve-EMU. If Home or World is already visible, it avoids the two fallback
  methods. If BOTH anchors are missing, it runs the FULL Reconnect detector
  **before** attempting any other recovery action. Any later unknown-screen
  pass also runs full Reconnect; failed or changed views still abort safely.
- **Safety and action templates are not globally restricted:** Welcome Back,
  Reconnect, known offers, Rewards banners, Train, Fight, Deploy and unproven
  variants keep their old resilient algorithm, two-frame click verification,
  and post-click state checks. Crucially, genuine World and City anchors keep
  multiscale/mono: in 26 real sessions, World CURRENT produced **2 scale
  and 4 mono hits**, City produced **4 mono hits**. Cutting these would be a
  regression; they are explicitly exempt.
- The other emulator types (MuMu, MEmu, BlueStacks, LDPlayer) follow the
  original Frostguard template and fallback semantics with no forced Serve-EMU
  exclusions. A helper instantiated before `initialize()` also defaults to
  the original semantics until a backend has been selected.

This does not assert each of the 523 templates has been functionally checked:
most have never appeared in the sampled game sessions. Do **not** blindly
remove those, including rare safety overlays. Similarly, this change does
not disable other-task UI when the genuine navigator cannot yet classify an
unknown screen. Each task's calls are still owned by that task.

## Evidence-driven follow-up built into the audit

`tools/template-system-audit/audit.py` now reports a **per-task profile** of
colour, multiscale, mono, multi and multi-mono observations. It counts actual
recognition calls (rather than assuming they were emulator switches); no
account IDs, screenshot bytes, usernames or logs are included in the report.
The audit treats `EmulatorRecognitionPolicy`'s blocked ID references as
non-executable metadata, not runtime usage. If a retired template is used
in an actual routine again, the audit fails.

Baseline at initial rollout: 26 Task Coach sessions, 1651 method checks,
114 distinct template IDs observed, 59 with positive hits. These are **not**
performance numbers for the new policy, which still needs real Windows runs.

## Regression and acceptance

1. Unit tests check all five banned variants on Serve-EMU and all four other
   emulators' unchanged behaviour; specifically protect fallback for World,
   City, Reconnect, Welcome Back, Rewards, Fight and Train.
2. Existing navigation tests must prove safe abort/preemption and no infinite
   recovery. Linux full Maven tasks reactor, desktop packaging and audit tests
   must pass.
3. Release via the signed Windows updater. Start Task Coach with Serve-EMU,
   confirm 720×1280/320 DPI and execute **Training Troops, Exploration Chest,
   Alliance Tech, Daily Missions and one task with actual Rewards**. Compare
   the per-task method counts and elapsed time with the previous run. Confirm
   no formerly unrecognized actions are clicked and no game currency spent.
4. In an unknown-screen/actual Reconnect or Welcome Back scenario, verify
   the guard remains functional. Do not mark entire project verified from a
   successful no-popup sample.
5. Only retire additional old names when the *intended page is visible* and
   current/old template scores are compared on the SAME frame plus negatives.
