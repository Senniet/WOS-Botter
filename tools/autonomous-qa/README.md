# SurvivalOps — autonome Serve-EMU QA-controller

This is the new **server-based test-control foundation**, separate from the Windows MSI. It uses the existing TrueNAS Serve-EMU over the webserver's trusted SSH key and attaches read-only evidence to the existing authenticated QA dashboard.

## State (phase 1)

- A pinned Android 16 emulator at **720×1280 / DPI 320**, with Whiteout Survival running. No silent failover to another serial. First bind: `python3 controller.py discover`, then `python3 controller.py bind emulator-5554`. When serial changes, bind must be explicitly updated.
- SQLite job queue, **one worker**, run IDs, screenshots, hashes, timing and current-template recognition scores. Every one of the **62 QA catalog tasks** has a separate exported manifest in `workflows.json`, currently **observe-only**.
- One reviewed **active** scenario for `DO_EXPLORATION`: verifies City/World on two frames, or recovers exactly the positively recognized Tech Research title and header Back once, then taps only the bottom-left free Exploration shortcut once and confirms the current Exploration title and Explore CTA. Unknown pages stop **without clicking**.
- SHA-based screenshots stored private under `~/.local/share/survivalops-autoqa/evidence/<run>/`; QA site serves them only to logged-in users. Run events are appended to the task's existing events history. **No operator notes, seven QA checkboxes, signoffs or verified status are modified.**
- Read-only snapshot comparison uses current Serve-EMU template PNGs from the repository, NumPy and ImageMagick already available on the server. Matching diagnostics never authorize purchases or unknown clicks.
- A half-hourly timer creates one observation of `INITIALIZE`. This checks emulator connectivity and records evidence without modifying the game.
- The QA site includes an **Autotest** tab, live queue view, run duration, screenshots, visual scores, and an ordered 62-task audit. Enqueue supports observation for every task and narrowly reviewed actions for Exploration navigation and free Daily Missions Claim All.

## Limits to be addressed in subsequent phases

This is **not yet a replacement for the Windows task engine**. Most task plans do not execute game actions, automatically rewrite Java code or auto-approve QA. Each needs a real recorded positive action and verified visual preconditions/postconditions. Start with Initialize, Daily Missions, Exploration and Training; then roll the same engine across the rest. The UI clearly distinguishes `observed` from `navigation_verified`, neither of which means the complete task works.

Unknown, changing or blocked screens are not tapped. No in-app purchases, gems/keys, quitting the game or emulator switching. A server-side AI/code-fix loop would require an explicitly configured agent and review/test gates; it is not silently running.

## Maintenance

`systemctl --user status survivalops-autoqa.service survivalops-autoqa-preflight.timer`

`python3 controller.py status`

`python3 controller.py enqueue INITIALIZE`

`python3 controller.py enqueue DO_EXPLORATION --mode navigate`

`python3 -m unittest -q test_controller.py`

The controller is deliberately Python stdlib + existing numpy/ImageMagick and requires **no paid API calls**.

## Confirmed deployment on 16 November 2026

- QA service: `survivalops-qa-dashboard.service`, URL `https://10.0.0.100:9097/`, new **Autotest** tab.
- Worker: `survivalops-autoqa.service` (enabled, single process).
- Scheduler: `survivalops-autoqa-preflight.timer` (every 30 minutes, **observation only**).
- Bound test emulator `emulator-5554`, Android 16 API 36, WOS running, 720×1280/DPI 320, SHA256-fingerprinted; original separate emulator is untouched.
- The exact QA site authenticated API, queue and evidence routes were tested; unauthorized access = HTTP 401, unreviewed active task = HTTP 400.
- The first automated read-only `INITIALIZE` run captured two frames and eight visual scores; later `TRAINING_TROOPS` observation ran through the authenticated website and ended with zero actions.
- The first reviewed `DO_EXPLORATION --mode navigate` run found **Camp Expansion III**, not a valid City/World root; it correctly refused to tap (zero actions) rather than touch Finish/Speedup gem buttons.
- A defect in the coarse vision matcher that incorrectly scored flat blue backgrounds as 100% matches was corrected before release. All saved run screenshots were re-scored without changing QA verdicts; stable unrecognized screens now generate **inactive, pending-review** candidate crops.

## Next-stage acceptance gates

1. **Initialize**: independent game state, popups, session health and Home root verification, no arbitrary task clicks.
2. **Daily Missions**: verified Daily title/Claim All and optional 160 milestone, free rewards + independent City return; negative frame and purchase interception tests.
3. **Exploration**: safe entry already built; add conditional Quick/normal branch, battle HUD, Victory/Defeat and safe result actions with bounded retries.
4. **Training**: training tier detection, free Train + completed troop claim, Home return, no unlock/gem screens.
5. Roll out to remaining tasks in the catalog **one at a time**, using Task Coach screenshots to establish positive *and negative* pre/postconditions. Lock actions behind reviewed scenario manifests.
6. Templates only promoted after positive/negative replay, scored speed comparison, full Java build and full task-run evidence. Never auto-merge source changes without tests and review.

The controller does **not** claim to be an always-on ChatGPT session or an autonomous code-writing model. Scheduled evidence collection runs without chat; code fixes, new action workflows and QA reviewer signoffs are still performed during an active assistant session or by a separately configured, audited local agent.

### Performance telemetry

The server uses a private 50-second OpenSSH multiplex connection for successive screenshot requests. Each new screenshot now records separate `capture_ms` and `recognition_ms` timings so transport latency can be addressed without lowering validation thresholds.

## Extended safety and task analysis (16 November 2026)

- Authenticated **Pauzeren** control on the QA website blocks new runs and is checked before **every** approved ADB tap; resuming requires an authenticated action. Actions already transmitted cannot be cancelled.
- The UI now shows **the last actual verified emulator heartbeat**, not simply that an ADB serial was once configured. The 30-minute observation timer refreshes connectivity; observations are never considered complete game tasks.
- An independent **15-minute Task Coach audit timer** inspects all 62 catalog entries, 52 historical uploaded sessions for 19 tasks (2,646 template method observations), and current QA issues. It ranks investigations while preserving verified statuses and operator notes. Safety guard misses (Reconnect, Welcome Back, critical dialogs) are excluded from obsolete-template recommendations.
- `DAILY_MISSIONS --mode claim_free` may claim free rewards only when **current Daily Missions title and Claim All are visible twice** at the proven location, Rewards banner plus **Tap anywhere to exit** are each confirmed twice, and Claim All has disappeared on the final Daily screen. Optional milestone chests, individual claims and other tasks are intentionally excluded. If the page is not already open, it stops with **zero taps**. Tests replay the exact 2026-11-16 Coach hit, Rewards and post-claim screenshots as positive/negative fixtures.
- Live A16 test on City correctly blocked Daily Claim All without a tap. The full free-claim sequence has been tested on replay fixtures and mocked ADB actions but **not yet end-to-end on the live Daily screen**. No score or claim of full task completion is assigned.

### Backlog priorities from actual evidence

Highest-ranked investigations are `TRAINING_TROOPS`, `ALLIANCE_TECH`, `DO_EXPLORATION`, `CITY_UPGRADE_FURNACE`, `ARENA`, and `ALLIANCE_TRIUMPH`. This is **prioritization, not a claim that the most frequently missed button is broken**. Examples: `TRAINING_CAMP_INFANTRY_TAB_LABEL_2026` often missed, Exploration's Victory scan can be correctly absent during an ongoing battle, and `GAME_HOME_RECONNECT` misses may be the desired result when the game is healthy.

The next implementation gates are: activate the Daily Missions route only on an observed live Daily page; complete optional 160-point rewards and Home return as separate validated stages; expand normal/Quick Exploration (including delayed fights and two-button Victory); then training completion/claim and remaining game tasks. **No autonomous Java rewrite or automatic release is advertised or active.**
