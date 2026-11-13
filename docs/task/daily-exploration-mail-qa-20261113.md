# SurvivalOps focused QA — Daily Missions, Exploration and Mail (2026-11-13)

Target emulator: TrueNAS `serve-emu`, Android `emulator-5554`, 720×1280, `com.gof.global`. No paid actions.

| Setting | Observed live flow | Improvement | Remaining limitation |
|---|---|---|---|
| Claim daily missions | Daily Missions HUD opens; one Claim All produced two sequential Rewards overlays; after two individually verified dismissals progress advanced 70→90 | Dismiss overlay only while verified, max four, safe close X, unverified retry | Updated Java flow not run in the deployed Windows application |
| Auto schedule daily mission claim | Scheduler code inspection: `setRecurring(!autoScheduleEnabled)` removed task after first auto run in TaskQueue | Always retain recurring task, let auto/manual mode select next schedule; regression test checks both | Live waiting for next scheduled run not checked |
| Do Exploration | Stage 152 opens Squad Settings showing Quick Deploy and Fight; Quick Deploy action is available | Require two-frame template confirmation of both Squad Settings actions; safe Victory footer exit | On manual Fight, WOS Unity process exited with SIGABRT; live autonomous battle currently blocked by emulator/game crash |
| Use Quick Deploy before each fight | Actual Squad Settings blue button tested; checkbox maps to configuration key | Recognize actual Quick Deploy label before tapping, only when enabled, recheck Fight afterwards | Full battle after updated code not executed |
| Claim exploration chest | Outer Claim opened Idle Income, inner Claim produced Rewards with 12.6K/33.3K on test account | Resume already opened Exploration/Idle Income screen without an extra blind bottom-left entry tap | Updated Java automated loop not run live |
| Claim mail rewards | Reports `Read & Claim All` opened Rewards; one exit tap returned to Mail with markers cleared; Wars and Starred accessible | Verify Rewards overlay dismissal and Mail title before checking markers; retry incomplete tab and cover all five modern tabs, legacy three tabs preserved | Updated Java automated loop not run live |

Diagnostic caveat: ADB logcat reports `Fatal signal 6 (SIGABRT)` in `com.gof.global` at 2026-11-13 14:55:19 after manually tapping Fight on Exploration stage 152. Do not count that battle as a successful end-to-end bot run.
