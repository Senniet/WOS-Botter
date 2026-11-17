# WOS_Sennie121 live QA — VIP, Storehouse, Recruitment, Labyrinth

Date: 2026-11-13. Target: only `WOS_Sennie121`, serve-emu ADB TCP `192.168.1.20:15559`, normalized 720×1280. No purchases, keys, gems, MSI or release.

## Live game outcomes (manual ADB actions)

- **VIP:** opened the visible VIP 2 HUD and Sign-in Streak calendar. The VIP 2 Daily Free Bundle was `Locked`, and the sign-in popup explicitly said to claim **tomorrow** with a remaining timer. No free claim button and no purchase attempted. The redacted VIP captures now form regression tests that verify the locked banner and rejection of green Claim detection.
- **Storehouse:** switched sidebar Daily → City, selected the Research Center, then selected Storehouse. Opened a ready crate bubble, saw a chest reward animation and next-chest timer. A stamina can appeared; opened its `A Warm Welcome` dialog, tapped verified green `Claim` and observed `Chief Stamina +120` and HUD stamina 450→570. Current before/after frames are now regression tests for both bubble kinds, their disappearance, and the lowered reward title/Claim button.
- **Hero Recruitment:** opened `Heroes` → `Recruit Hero`. Advanced next-free and Epic next-free timers were active, so no keys/gems used. Separate `Points Chest` had red `1` badge. Tapped it once, observed `Rewards` and dismissed only via the `Tap anywhere to exit` label; badge cleared. Added a bounded, screen-gated Points Chest flow; tests validate the red badge before/after, Rewards heading and exact safe exit label.
- **Daily Labyrinth / Land of Heroes:** checked the Daily sidebar with both completed-missions filters and scrolled to the bottom. No `Land of Heroes` destination was present on this account. No safe live battle was possible; do not claim this function passes. Existing routine retries in one hour if it finds no completed challenge, rather than waiting until daily reset.

## Diagnosed code issues and commits

- `3c1611b`: Hero Recruitment previously omitted the free `Points Chest`; bounded badge and reward verification added, paid recruitment excluded.
- `74dc91f`: After a missed City tab tap, modern sidebar now retries modern geometry when fresh evidence still shows modern layout; based on a real logged `requested=CITY observed=DAILY` Storehouse error and current screenshot samples.
- `ad7f148`: The 2026 `A Warm Welcome` title appears below the old Storehouse search ROI; current live screenshot initially **failed** the original recognition test and passed after widening the ROI. This is a reproduced defect and fix.
- `346c950`: Logged ADB `ShellCommandUnresponsiveException`/offline screenshot errors now get at most one fresh capture retry, without repeating taps or restarting a remote emulator; simulated-error tests pass.
- `d8e5fca`: Added privacy-redacted regression scenes for VIP locked state and Storehouse before/after bubble classification.
- Earlier `584a506`: disabled paid VIP purchases in automatic daily claim execution.
- Earlier `7261f8f`: failed Labyrinth challenge check now retries after an hour.

## Scope and remaining verification

- The game claims above were performed live through ADB; recognition tests use captured and redacted live frames. **A full automated Java/Windows SurvivalOps run against these updated commits was not executed here.** Do not report any complete automatic task as end-to-end verified.
- The VIP/Advanced/Epic free actions were on cooldown or locked; no available free action to collect. Labyrinth was unavailable in the sidebar.
- The original network/ADB outage and missed sidebar tab are historical diagnostics; their recovery code is regression-tested but the precise failure was not reproduced after the patch.
- No MSI or GitHub release was created. Changes were committed and pushed to `fix/daily-exploration-mail-five-actions-20261113` only.
