# Task-scoped training recognition and fast Home recovery — 15 November 2026

**Status:** tests pass; performance gain not yet confirmed on the Windows client.

## Before: real Windows Task Coach 1.1.67

Evidence on the authenticated diagnostic server: Task Coach session
`20261115-221149-17rgokv`, `TRAINING_TROOPS`, from 22:11:49–22:13:58 UTC.
The operator observed successful training across Infantry, Lancer and Marksman.
The coach recorded **104 template method measurements**; these represent
individual colour/multiscale/greyscale probes, not 104 game clicks.

- 39 method checks for Infantry tab templates (24 label, 15 active), many
  repeated by `TrainingRoutine.isTrainingCampInterfaceVisible()`.
- 15 `GAME_HOME_RECONNECT` measurements across general navigation.
- In the final Marksman training screen, `NavigationHelper` checked Welcome
  Back, Resplendent offer, resource overview, shared rewards, Alliance and
  Alliance Tech before finally finding the generic header Back arrow.
  This is **navigation-recovery work**, NOT evidence that Alliance or Rewards
  tasks were scheduled.
- Independent background diagnostic snapshots reported a 5-second ADB
  screenshot timeout. This is a separate potential latency bottleneck.

## After: specialized checks, no wrong-task probes in the known context

`TrainingScopedScreen` uses a **single existing ADB capture** per check, with
OpenCV matching only the Infantry training tab (which persists on all three
camp tabs) and the actual page Back arrow. The image is matched in memory:
no separate ADB command for each colour, greyscale or multiscale variant.

During final queue verification, the task first confirms this known training
screen and its Back arrow twice. It taps that arrow **once**, then positively
recognizes the City Home anchor. Only when the current page is not confirmed
as Training does it use the broad shared recovery path. Failure to return
Home after a validated training Back causes the routine to stop safely rather
than search dozens of unrelated screens or repeatedly press Back.

This deliberately does NOT globally disable reconnect protection or popup
recovery for other tasks; they may still need those guards. It also does not
change the Task Coach image evidence for the normal Train click or the
training-queue postcondition. The app's reported scheduler success still
requires checking the game queue, not simply counting a button tap.

## Reproducible tests

The 720×1280 test fixtures are real Task Coach frames from the Windows client,
masked to **only** show the top-left Back icon, Infantry footer tab and (for
City) City anchor. No user account images or full screenshots are committed.

- Infantry ready — training panel true, Back true, City false
- Lancer ready — training panel true, Back true, City false
- Marksman already training (Speedup visible on private full frame) — training
  panel true, Back true, City false
- City home — training panel false, Back false, City true
- One-click verified exit; no tap when the frame changes; no repeated Back
  on failure to confirm Home

Command:

```sh
mkdir -p "$HOME/.cache/survivalops-tests/scoped-tmp"
TMPDIR="$HOME/.cache/survivalops-tests/scoped-tmp" \
MAVEN_OPTS="-Djava.io.tmpdir=$HOME/.cache/survivalops-tests/scoped-tmp" \
./mvnw -DargLine=-Djava.io.tmpdir="$HOME/.cache/survivalops-tests/scoped-tmp" \
  -pl modules/tasks -am test
```

On this revision: **731 tests, 0 failures, 0 errors, 4 skipped.** This is a
static replay, not a benchmark on the Windows emulator. A new Windows Task
Coach run is needed to compare time and method measurements and verify that
no unrelated recovery scans are used after the third Train tap.
