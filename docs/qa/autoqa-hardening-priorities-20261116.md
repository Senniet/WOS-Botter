# AutoQA improvement programme – second pass (2026-11-16)

## Verified on running servers

- Dedicated A16 Serve-EMU pinned to `emulator-5554`, 720×1280, 320 DPI, actual foreground-screen screenshots via ADB over multiplexed SSH. Game process detected, one worker.
- `https://10.0.0.100:9097` authenticated Autotest tab displays runs, screenshots, current-template scores, visual candidate proposals, an ordered audit across all 62 tasks, and a user-accessible pause/resume control.
- At 23:36 local, new read-only `INITIALIZE` capture completed in approximately 2.94 seconds with 8 template checks and 0 actions. Device heartbeat now records last actual successful check; saved config does not imply the emulator is online.
- The existing Task Coach archive contributes **52 sessions** among **19 distinct tasks**, **2,646 method observations**. Global QA retains **12 open defects**. An independent 15-minute audit reports priorities without promoting any QA status or treating scheduler SUCCEEDED as proof.
- Exploration navigation was previously confirmed on live A16: from 2-frame verified Tech Research with Back to verified City and then actual Exploration with title + Explore button; two safe taps. Normal Fight/Quick Rewards not yet tested end to end by new controller.
- Daily Missions limited `claim_free` workflow: actual Coach frames from the Claim All available screen, verified Rewards banner with Tap anywhere hint, and post-Claim All disappeared screen have passed replay negatives. Live test correctly blocked at City without tapping because the Daily page was not open. This does **not** yet cover optional 160-point chest or Home return, and does not mark Daily Missions as fully verified.
- Noodstop: authenticated POST `paused:true` returns success; unauthenticated request is rejected HTTP 401; queued actions rejected while paused; `paused:false` restores operation. Every known ADB tap checks the stop before sending.

## Prioritised investigations

| Priority | Task | Evidence-driven focus | Live completion proved? |
|---|---|---|---|
| 1 | Training | Unlock overlays, Train action and finished troop claims, existing four open defects; review missing Infantry tab matches | No |
| 2 | Alliance Tech | Incorrect resource/reward overlays and reliable return to City | No |
| 3 | Exploration | Fast loading, normal fight >25 seconds, real Victory / Continue variants, Quick/normal rewards and next stage | No |
| 4 | City Upgrade Furnace | Current upgrade screen and open defects, avoid accidental gems | No |
| 5 | Arena | Daily chest, 5 challenges, Home return and current screen variants | No |
| 6 | Alliance Triumph | Reward readiness and multiple title/overlay misses | No |
| 7 | Daily Missions | Optional 160-point chest, repeated rewards and verified Home return | No |
| Subsequent | Remaining tasks | One positive and one negative real screen per action, no cross-task template loops | No |

Historical images with weak scores are **not** automatically deleted; availability and optional-dialog absence may be correct. Every newly proposed candidate template remains inactive until independently validated against positive and negative frames.

## Acceptance requirements for full autonomy

A task is complete only when a single-task end-to-end execution demonstrates navigation, verified action, observed postcondition, clean return/scheduling and regression; QA two-party signoff remains separate. New code and MSI releases must pass relevant tests, and no in-game purchases or in-app speedups are permitted. A true unattended code-edit agent is **not** running, only read-only periodic evidence collection and the explicitly reviewed restricted game actions above.
