# Exploration fast recognition – Serve-EMU (16 Nov 2026)

## Operator finding

SurvivalOps 1.1.75 feels slow in Exploration, both when identifying visual
states and sending actions. Latest uploaded Task Coach `20261116-194405-1skoszu`
(`DO_EXPLORATION`, DEFERRED) reports 42 recognition method attempts and 21
captures; `EXPLORATION_BUTTON` was 98.31% positive twice and Fight 100%
positive, but large intervals remained between actions.

In the recognition CSV the first Victory miss at 19:45:16 was followed by
multiscale at 19:45:18, monochrome at 19:45:20, Defeat color at 19:45:21,
Defeat scale at 19:45:23 and Defeat mono at 19:45:24. Those are not six
independent game states: they are repeated transfers of a changing frame
interleaved with expensive matching. The earlier 1.1.75 battle poll also
inserted **2,500 ms of deliberate waiting** between checks.

The 19:45:37 confirmed Victory capture uses **both Return to City and
Continue** buttons (not the single-button Victory variant). That is a
separate result-layout issue requiring a live state-machine test; this patch
focuses on the requested scan/input pacing without introducing an unverified
Continue click.

## Implementation

- New `ExplorationFastResultFrame` classifies Auto+x2, Victory and Defeat
  against **one newly captured frame per poll**. It uses exact-color then
  monochrome matching on that same memory buffer, and a less frequent
  multiscale fallback when necessary. Missing variants no longer each issue
  an additional remote ADB screenshot. Active-battle control confirmation
  prevents large Victory/Defeat scans on a still-fighting frame.
- A positive result requires **two fresh captures** agreeing on Victory or
  Defeat, protecting against transition-frame false positives.
- Normal battle poll sleep reduced **2,500 ms → 250 ms**. Initial result
  polling delay reduced 1,500 → 350 ms; Explore tap follow-up and Quick
  Deploy waits shortened because screen capture and input already incur
  significant real-world ADB latency. The 180-second battle safety deadline
  remains unchanged.
- Every first and twelfth poll logs separate `capture=...ms` and local
  `recognition=...ms`, so a follow-up run can distinguish ADB transport
  latency from CPU/OpenCV cost.
- Context-scoped safety and result postconditions are retained. Unknown
  states remain DEFERRED; no blind Continue action is introduced.

## Validation

- Replayed privacy-masked actual battle and Victory frames, plus unrelated
  menu and two-frame confirmation negatives.
- Full tasks test suite: **782 tests, 0 failed, 0 errors, 4 skipped**.
- Full desktop Maven package: **BUILD SUCCESS**.
- A new Windows Task Coach run must measure the before/after cadence,
  particularly ADB capture milliseconds, and validate Victory's two-button
  variant before signing the entire route as verified.
