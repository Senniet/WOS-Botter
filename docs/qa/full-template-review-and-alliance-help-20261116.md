# Complete 523-template review + a testable Alliance Help Request task

**Code state, 16 Nov 2026:** automated full inventory, known bad duplicate
fallbacks disabled, standalone alliance Help enabled in source. **Not live
verified on updated Windows** until a new build is installed and tested.

## All 523 templates classified

The private audit (read-only `tools/template-system-audit/audit.py`) now
labels every registered template by **runtime role**, separate from its match
outcome. Current totals:

- 5 **retired** historical title/HUD lookups: `SERVE_EMU_EXPLORATION_TITLE`,
  `EXPLORATION_SCREEN_TITLE`, `SERVE_EMU_IDLE_INCOME_TITLE`,
  `EXPLORATION_IDLE_INCOME_TITLE`, `SERVE_EMU_WORLD_ANCHOR`.
- 7 **protected** guards/alternatives, including mandatory reconnect, welcome
  back, blocking offers and alternate Rewards pages.
- 398 other **active** Java-referenced keys; 113 **registered but not directly
  referenced in production Java**, requiring review before deletion. Direct
  enum-use is not a complete measure of dynamic plugin usage.
- 7 missing image files, 28 extra unregistered PNGs, 4 shared-file alias pairs.

The four alias pairs are `GATHER_DEPLOY_BUTTON/RALLY_DEPLOY_BUTTON`,
`DAILY_MISSION_CLAIM_BUTTON/ALLIANCE_CHEST_CLAIM_BUTTON`,
`ARENA_REWARDS_BANNER/SHARED_REWARDS_BANNER` and
`INTEL_AGNES/AGNES_CLAIM_INTEL`. They share bytes but are names used in
**different task contexts**, not necessarily redundant searches. Retain them
until a runtime sequence demonstrates both are queried on the same page.

The status and evidence note can be filtered in the private QA Templates tab.
No game images or account details are returned to that tab. `template-decisions.json`
is source-controlled and each retired key MUST have zero direct production
references; audit will fail if someone reintroduces one accidentally.

## New World HUD cleanup

The original twenty-coach-run baseline found zero old World HUD hits vs many
high-confidence CURRENT hits; three additional sessions show a single old
World hit (99.95%) when the CURRENT variant also matched repeatedly. This
means the old template was *not completely unusable*; it **is redundant** on
the confirmed current screen. We removed its runtime lookups from
NavigationHelper, StaminaHelper, SidebarNavigator, DeploymentHelper and
InitializeRoutine. Existing real World and City regression screenshots still
pass with CURRENT only. Historical PNG retained for comparison.

No mass elimination of `GAME_HOME_RECONNECT`, `GAME_START_WELCOME_BACK_TITLE`,
Rewards variants, mail, popup and update overlays. Zero hits on absent
screens are not evidence of a broken template. Retiring them indiscriminately
would make the bot unsafe.

## Alliance Help Request is no longer an invisible injection

Previously `HelpAllianceInjectionRule` clicked the Help shortcut only during
another task's sleep. The Alliance UI had a checkbox, but there was **no**
`TpDailyTaskEnum` member, TaskRegistrations factory, Task Coach selector row,
status or QA task to test independently. That explains why it was missing.

- `ALLIANCE_HELP_REQUEST` is a new stable task ID **41**, in Alliance, backed
  by `AllianceHelpRoutine`, and uses the **existing** `ALLIANCE_HELP_BOOL`
  checkbox. No new profile setting or purchases.
- Task Coach and the scheduler enumerate `TpDailyTaskEnum`, so the new task
  now appears in both. Shared QA catalog also contains it, initially **not
  verified**; previous 61 task statuses and feedback remain untouched.
- The old injected click is no longer registered. A stand-alone task checks
  every 10 minutes when enabled, and avoids injecting clicks while some other
  task is performing an unrelated action.
- Strict 720×1280 City HUD + correct Help shortcut on **two fresh frames**,
  stable tap position, then a fresh City frame with the request absent.
  If it is absent initially, log "no request" without claiming help was
  performed. If the click's effect is unverified, mark work incomplete.
- The routine avoids premium dialogs, other screens, blind retries and
  foreign-task UI scans. It must NOT be marked verified until Task Coach
  captures an actual **visible** Help shortcut, the real tap, and the
  disappearance on City without touching other UI.

## Next acceptance

After the new Windows version is installed, enable **Alliance Help Request**
in Alliance settings and run its Task Coach on Home while a real help request
is visible. The operator and reviewer check the new independent QA row and
sign off only after screenshots prove the request disappeared. Also rerun
Exploration Chest and a World-navigation task to confirm reduced template
lookups and no regressions. Then work through the remaining 113 unreferenced
and 53 only-miss keys with *correct-screen* evidence before removing any more.
