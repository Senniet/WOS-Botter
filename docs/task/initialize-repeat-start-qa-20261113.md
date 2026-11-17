# Initialize repeat-start latency — WOS_Sennie121 (2026-11-13)

## Observed code path and defect

TaskQueue.start() deliberately forces Initialize on each new queue run, including after Stop → Start. Initialize used to require City/World immediately; if the previous task left the existing WOS process on Hero Recruitment, Heroes, Exploration, or Squad Settings, `waitForHomeScreen()` did ten slow passive retries, each including a five-second sleep, **before** its first bounded Back recovery. This can add at least fifty seconds even though the game and ADB are already working.

A fresh, read-only WOS_Sennie121 screenshot on 2026-11-13 found the running game on `Squad Settings` with a clear game-native Back button, not City/World. A later read-only capture found the game on World. No taps were sent during this QA, to avoid disrupting any ongoing user tasks.

## Fix

- Keep mandatory Initialize on each new queue run; never skip character verification, game install check, reconnect checks, mandatory update, or required resource downloads.
- Only when the Whiteout Survival process was **already running** on entry, and existing startup checks find neither City nor World, attempt an immediate resume **once** before the first five-second passive retry.
- Recognize a strict allowlist of current in-game pages from their real title plus normal Back arrow, including Hero Recruitment, Heroes, Squad Settings, Daily, Exploration, Mail, VIP, Arena and Research.
- Require two fresh captures of the **same** known page before the first Back. Verify game foreground before **every** Back. After each Back, search for verified home/world and stop at once when found. When not home, the next screen must again show a recognized page with its Back arrow on two fresh frames. At most **three** Back actions; never tap on Rewards, unknown overlays, login or non-game screens.
- Recognize live 2026 City/World HUD anchors in a small bottom-right ROI as a fallback when old Home templates no longer match. The new current-World artwork is anchored to a captured image and does not match a nested Squad or Recruitment screen.
- Record number of Back actions, elapsed milliseconds and safe stop reason. Character switch loading explicitly disables the already-running resume optimization.

## Tests and limitations

- Redacted actual WOS_Sennie121 screen snapshots retained only the page titles, Back-arrow crop or home/world anchor, never account details.
- Frame tests exercise production OpenCV matching for Hero Recruitment → Heroes and current Squad Settings, and deny Back on Rewards/City and on unsupported resolutions.
- Deterministic navigation-sequence tests verify 2 Back actions on Hero Recruitment → Heroes → Home and Squad Settings → Exploration → Home, with zero Back on an unknown screen, lost foreground and a strict 3-Back limit.
- Current-World and City anchor recognition tested on live redacted images. Startup emulator retries and existing recovery-policy tests remain unchanged.
- **Limit:** a timed second-run test from the installed Windows client was not possible from the headless Linux CI host. The navigation sequence is replayed using actual emulator screenshots and independent fake-frame callbacks; the installed app's live stopwatch must be verified in user desktop diagnostics.
