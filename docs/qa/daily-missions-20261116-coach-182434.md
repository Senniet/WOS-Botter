# Daily Missions 16 November 2026: excessive scans, optional 160 milestone, failed close

Task Coach ID: **20261116-182434-c40b3e**, task `DAILY_MISSIONS`, from 18:24:35 UTC; operator reports Claim All and both Rewards claims completed but the Daily Missions page stayed open and many unrelated screen checks were attempted. Coach `summary.txt` reports **58 image checks, 11 hits, 23 frames** (scheduler `SUCCEEDED` is *not* proof of final Home state).

## Observed cause and recovery

- The current `DAILY_MISSION_SCREEN_TITLE` reached **100%** on the Daily page. `SERVE_EMU_DAILY_TITLE` was checked **12 times, 0 matches**, with 15–43% scores. Its color→multiscale→mono retries were wasted (both during initial navigation and behind the first Rewards overlay). The fixed three-attempt initial title lookup also caused a delay before opening the page.
- Claim All was correctly recognized at **100%**. The first Rewards overlay was recognized under `SERVE_EMU_REWARDS_BANNER` at 90.91–97.29%; a subsequent 160-points milestone Rewards overlay was also recognized at 93.64–95.26% and closed. After that, current Daily title was again recognized at **100%**.
- The old Daily exit action tapped y=133..186, which falls below the actual white top-right X near **(688,125)**. As a result the Daily page remained on screen, and a broad navigation recovery scanned unrelated `Reconnect`, `Welcome Back`, `Offer`, three Rewards variants, Furnace, World, etc. (some screenshots plainly show Daily Missions still open).
- From source-cropped before/after frames, initial progress **145** points with the 160 milestone chest **closed** changes after Claim All to **175** points with the chest **open**. The 160 chest cannot be assumed available on every run.

## Fixed source behavior

1. Use the proven Daily title with one scan for Serve-EMU instead of three retries and a failed four-method legacy fallback. Other emulator types retain their legacy fallback.
2. Keep Claim All and current verified Rewards overlay dismissal. After Claim All, optionally attempt the **160-point chest** only when the gold progress bar positively passes the 160 marker **and** the correct chest is still closed, confirmed over two independently captured frames. Skip entirely if below 160 or already open. A claimed milestone may display one additional Rewards overlay; close it only when verified. No purchase or blind fixed-coordinate chest click.
3. Detect **Daily title AND the exact current top-right X** on the same frame; confirm a second stable frame and tap at the positively detected X. Then require the Daily title to disappear **and** the City HUD anchor to reappear. Unknown/no Home/unchanged page marks task incomplete and schedules a retry, never false `SUCCEEDED`.
4. Use small source-cropped/masked screen fixtures: Daily before/after, Rewards, City HUD and positive synthetic milestone state (165+ progress combined with a still-closed chest), plus negative states. This demonstrates that the rule distinguishes below-threshold, already-open and eligible conditions. The positive **composite is a deliberate test fixture, not a recorded live 160-available screen**.

## Verification

- Full tasks reactor: **770 Java tests, no failures/errors, 4 skipped**.
- Full desktop Maven application package **BUILD SUCCESS**.
- A new Windows Task Coach run must confirm the real final two-frame X click and City HUD after both Rewards variants, and must separately cover a day with **no 160-point chest**. Until then the task is not jointly verified.
