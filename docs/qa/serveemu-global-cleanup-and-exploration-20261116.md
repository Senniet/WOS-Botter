# Serve-EMU-only template cleanup and normal Exploration fight

## Scope and measured baseline

The user will deploy exclusively to the custom **Serve-EMU** (720×1280 at
320 DPI), not the three upstream emulator backends. The global runtime
recognition policy therefore gates old variants **only** when Serve-EMU is
selected. Original assets remain versioned, not physically deleted.

Audited **all 62 QA dashboard task entries** against their Java dispatch
class; 19 have real uploaded Task Coach recognition events. Source-controlled
per-task inventory: `docs/qa/serveemu-whole-task-recognition-review-20261116.md`.
The 19 measured tasks contain **2,386 recognition method observations**.
Each colour, multiscale and mono attempt is a method observation, not another
emulator; these historic counts **are not an after-build timing measurement**.

Top historic repetition (includes pre-fix builds): Training 395 checks,
Alliance Triumph 345, Do Exploration 303, Daily Missions 231, Alliance Tech
186, Exploration Chest 174. Not every miss should be removed: several are
correctly absent optional buttons, unavailable tiers, or blocked dialogs.

## Active Serve-EMU runtime cleanup

- Three more **proven obsolete** variants are skipped: `SERVE_EMU_DAILY_TITLE`
  (36 misses; `DAILY_MISSION_SCREEN_TITLE` 20 hits including 100% on the same
  current page), `GAME_HOME_WORLD` (obsolete original HUD; current World
  proved positive in colour **and** multiscale/mono), and
  `ARENA_CHALLENGE_BUTTON` (original zero positive attempts, current Challenge
  confirmed ~99.7% on the actual Arena page). Together with the five earlier
  retired Exploration/Idle Income/World variants, **8** are skipped only in
  Serve-EMU recognition calls.
- Every task benefits from a shared root-first navigation policy: when a
  positive Serve-EMU City or World HUD anchor is visible, skip a redundant
  global Reconnect lookup. When root is absent/obscured, the **full verified
  Reconnect/Welcome Back/offer/safety recovery remains mandatory**.
- Reviewed old World dependencies in Intel Beast deployment, Polar Terror,
  and Prioritise Furnace. Each now explicitly uses the *current* World anchor
  with a bottom-right ROI, and the Prioritise Furnace route requires a
  confirmed City root before its subsequent actions. The current title also
  replaces the old Daily recovery page title. Other code that already tries
  the current root first remains unchanged.
- **Do not retire** the current City/World HUD mono/scaled alternatives:
  Task Coach recorded real positives (e.g., Victory run and World navigation).
  No purchases, optional rewards, lock indicators, T1–T11 tier assets, or
  emergency prompts are disabled just because they were absent in a healthy
  test session. Tasks without uploaded runs have a static source inventory
  but cannot be declared accurate/inaccurate based on an unseen game screen.

## Normal Exploration fixes from real Task Coach frames

Two runs `20261116-184542-f2ptnb` and `20261116-184419-1v8kfun` showed the
normal battle underway while the old code allowed only **25 seconds** to
find Victory/Defeat. The actual Victory screen later appeared and read
**Return to City** rather than *Tap anywhere to exit*.

1. When Exploration is already visible, stay there. Otherwise, if a confirmed
   City **or** World HUD is visible in two fresh captures, tap the existing
   bottom-left Exploration shortcut directly. **Do not switch City→World
   first.** Unknown pages retain conservative root recovery.
2. A normal battle may continue for up to **3 minutes per battle** (session
   bounded to 12 minutes); never issue additional Fight/Back taps merely
   because the old 25-second timer expired. Result scans are paced at
   2.5-second intervals, not continuously.
3. Real live battle screens have two specific controls, **Auto** and **x2**.
   When both are positively visible on the same frame, do **not** run the
   large Victory/Defeat colour→multiscale→mono scans or quick-dialog OCR for
   that polling iteration. If these controls change/disappear, fall back to
   the resilient result scans — this preserves valid mono Victory hits.
4. Both the Victory banner and the exact **Return to City** button must be
   recognized on two fresh frames. Tap the button once, then passively
   verify City (bounded ~3-second settle), not an old blind footer location.
5. To preserve normal repeated Exploration, the next stage may be opened
   from *confirmed* City using the same direct shortcut, verifying Explore,
   optional Quick Confirmation and Squad Settings before another Fight.
   At most four normal battles per session, and only when enough of the
   session budget remains. If no next Explore is available, finish safely.
   A missing verdict or unknown page is DEFERRED, not a false success.
6. The optional Quick Exploration completion/Rewards/Idle Income logic from
   version 1.1.73 is retained; this change is specific to normal battles.

## Test & signoff policy

Replay fixtures contain cropped game UI only: City, World, normal battle
Auto/x2 on two moving frames, actual Victory with Return to City, and
negative Squad/Exploration screens. Added timing and scoped-navigation unit
checks; existing Rescue, Reward, deployment and safety regression tests run.

No new Windows **end-to-end** measurement exists yet for this exact build.
A fresh Task Coach run must measure the drop in attempts and prove direct
City→Exploration, delayed Victory, Return to City, and a possible next stage.
Do not mark any task jointly verified based only on successful unit tests.
