# Serve-EMU recognition performance, November 17, 2026

## Actual bottleneck and fix

`TemplateSearchHelper.retrySearch` performs a primary template search. On misses and default threshold ≥90, it may fall back to multiscale **and** grayscale searches. Previously `verifiedFallback` used `doSearchMultiScale` and `doSearchGrey` separately, each calling the emulator remote ADB screen capture. For Serve-EMU this incurred **two separate remote full-frame transfers for fallback misses** in addition to the original color attempt. Low scores (10%, 20%, etc.) on absent buttons are normal misses and should not trigger extra captures just to recheck the same static screen.

New `ServeEmuFrameFallback`: one newly captured frame runs multiscale then grayscale locally. If both miss, no second fallback screenshot is requested. If either hits, a **second fresh remote screenshot** must confirm the same action position, otherwise no click. No threshold reductions, no removal of reconnect/optional guards and no change to other emulator backends. A fallback miss now requires one capture instead of two; confirmation still requires a fresh frame.

For `DoExplorationRoutine.detectExploreButton` the explicit image template and blue-button geometry now share the first fresh screenshot. At Exploration startup a single captured frame also supplies the existing Quick result, Quick modal geometry and initial Exploration/root probes; a second fresh frame still confirms a root shortcut before any tap. The earlier root-verified path now avoids re-fetching an already checked Exploration header immediately before a single confirmed shortcut tap. The selected method is confirmed against a separate second screenshot before tapping. This eliminates repeated captures on the no-hit path and keeps two-frame authorization on the successful path. Fight-result polling already shares a frame and is left unchanged. No blind shortcut or faster but unsafe tap was introduced.

## Validation and caveats

Five focused `ServeEmuFrameFallbackTest` tests verify capture count, multiscale/mono ordering, fresh positive confirmation, moving-target rejection and invalid-frame handling. Full Maven test reactor of all modules through tasks (incl Exploration regressions) passes, and desktop app compilation/package is separately checked. These are test results, **not proof of end-to-end live Windows performance improvement**: quantitative before/after timings require a fresh installed Windows task run. The test-specific findings use actual 2026 Task Coach evidence, but SSH server emulation is not the same network route as Windows remote ADB.

No old templates were physically removed: disabling high-miss guards without screen context could break optional rewards, login or fault recovery. The code change attacks the repeated expensive captures across all tasks on Serve-EMU.
