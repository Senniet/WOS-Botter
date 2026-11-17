# SurvivalOps core-task live QA — 13 November 2026

This audit extends `docs/serve-emu-qa-20261113.md` without re-testing previously fixed Daily Missions, Mail, Exploration, Storehouse, FC or event workflows.

## Live interactions (TrueNAS serve-emu, 720×1280, DPI 320)

- **Arena:** open Daily sidebar → Arena → initially disabled Challenge until defensive squad is configured. The tutorial required entering Def. Lineup, Quick Deploy, then Save. After that Challenge became active. Opened Challenge List, selected a free opponent, Quick Deploy, Fight, and observed **Victory** and daily attempts decrease from 5 to 4. No gems purchased. The saved defensive squad is a real account change, not an isolated simulation.
- **Intel:** open Lighthouse Intel from the Daily sidebar, enter the Lighthouse building, open Intel map, choose a Beast Hunting marker, tap verified View, and observe the expected wilderness target modal (Attack costs 10 stamina). No attack was dispatched during the Intel audit. The more specialized Intel Rescue/Journey flows remain unverified.
- **Research:** open City sidebar → Center Research; discovered the queue's current `Idle` text is lower than the old OCR region. Entered Research Center, opened the Tech Research tree; found title at y ≈ 90 instead of the previous y < 80. No research purchased or started.
- **Training:** verified City sidebar `Idle` statuses for Infantry, Lancer, Marksman, and Research with OCR on masked captured frame. Opened Infantry Camp and used the free Train control to queue **266 Hardy Infantry**, confirmed a running 02:25:00 timer. Did not tap Finish (gem cost).
- **Gathering:** opened World Search → Meat, searched a level-1 abandoned farm, tapped Gather, opened formation and tapped Deploy once; confirmed an actual free march in the Marching list (1/4). Repeated the search for Wood to capture a current Deploy template; did not deploy the second formation. Search, Gather and Deploy were validated on the active game.

## Code changes

- Arena's Challenge entry now checks the Arena header, confirms the button across frames and verifies the Challenge List afterward. A disabled first-run Challenge logs useful diagnostics rather than clicking blindly; automatic account setup is not yet implemented.
- Research and Training queue OCR select the current sidebar regions when the current layout is visually confirmed, while retaining old BlueStacks coordinates.
- Research tree title is detected with a current live template before older OCR fallback.
- Gathering's current Deploy control is recognized with frame consensus; a tap without verified screen transition does **not** count as a successful deployment and cannot be blindly retried.

## Verification boundaries

- Live ADB taps prove that the **emulator** handles actions, not that the installed Windows MSI has completed all these steps autonomously. MSI and updater validation on the user's Windows installation is still necessary.
- Neither all 58 task classes nor all mission/account/level variations have been individually live tested. Temporary events, FC and gem purchases were intentionally excluded.
- UI templates and regression screenshots are cropped/masked; no account ID, private conversations or login data intentionally bundled.
- Accuracy percentages from a small set of captured screens do not establish a real-world nearly-100% recognition rate.

## Additional live core tasks

- **VIP (free-only):** verified HUD entry, title, calendar and the Sign-in Streak panel; VIP 1/2 Daily Free Bundles were locked. The old blind point-reward click could instead hit a shop cart; modern code confirms the page and skips `Locked`, with only two-frame green `Free` allowed. No purchases made.
- **Hero Recruitment:** opened the Hero Recruitment screen through the Heroes menu and tapped one Advanced green `Recruit once Free` and one Epic green `Recruit once Free`. Both produced real Rewards screens with `Tap anywhere to exit`, returned to the correct page, and then showed orange paid-key buttons. No keys or gems spent. Modern flow now verifies title, green Free action twice and the Rewards exit label twice; no orange action is ever targeted in that flow.
- **Availability:** Pet Adventure, Life Essence and some alliance routines were not available on the test account (not in an alliance, early Furnace level). These cannot be truthfully marked live validated. No currently-running game events or FC tasks were exercised.
