# Alliance / War navigation recovery — 14 Nov 2026

## Diagnostic evidence

- **16:05–16:06 CET**: the queued City Upgrade / navigation flow logged multiple failed Back-arrow recognition attempts (highest visible screen-specific score 79.1%, below 90%) and then `HomeNotFoundException`. The subsequent Initialize pass also failed to identify a Home/World root. The next sampled screen shows the **Alliance lobby** with a top-left arrow and an `Alliance` title.
- **16:24–16:25 CET**: a further diagnostic group lost Home/World on a screen which the latest sample confirms was the **War Rally** panel, also featuring a top-left Back arrow and a `War` heading. In both cases the standard City/World anchor is legitimately absent while a nested screen is open.
- Evidence files in the private LAN inbox: `20261114T150848Z-4339898071be.png` (Alliance), `20261114T152529Z-65b6adc1666f.png` (War), and their corresponding diagnostic JSON entries. Redacted test fixtures retain only header pixels.

## Implementation

Add distinct Alliance and War page-title and Back-arrow templates, each extracted from the real 720×1280 capture. `NavigationHelper.findVerifiedBackArrow()` accepts a typed arrow only when **both** the specific page title and matching Back arrow are independently detected in two consecutive frames at 92% confidence. The shared Home recovery can then navigate War → Alliance → City without a blind Android Back. All existing preemption and unknown-screen safety logic remains.

## Validation / limits

`NavigationAllianceWarCurrentFrameTest` verifies Alliance/War titles and their arrows on positive samples and rejects unrelated City, World and Intel titles; all targeted navigation tests passed. The combined Java regression suite is required before publication. No in-game interaction or Windows client installation was performed; the running MSI uses an older code version. Do not assert that the full War → Alliance → City sequence is live-tested.

## Initialize recovery is fixed too

`KnownGameScreenResume` had a second independent blocker: its generic header arrow search only examined y=65..155, so the real Alliance/War arrows at y~5..65 were invisible to the fast second-start logic. The page enum and capture-level recognition now include typed `ALLIANCE`/`WAR` matches, requiring both the exact page heading and its Back arrow. `InitializeRoutine.resumeKnownPageToHome()` already requires confirmation from a fresh second capture and applies a bounded Back budget, so no additional blind input is introduced. The fixture tests explicitly reject false positives from unrelated City/World/Intel headers.
