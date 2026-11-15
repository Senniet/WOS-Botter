# Reduce duplicate template checks on Serve-EMU 720×1280

**Scope:** Nov 16, 2026. Only disable **runtime probes** where the exact
expected screen was visible in Task Coach and the current title matched.
Do not delete library images, remove safety/reconnect handling, or change
matching thresholds. This is not proof that a low-scoring template is always
bad: a missing screen is supposed to produce a miss.

## Evidence from 20 live sessions (1,358 method measurements)

| Expected page, both templates actually tried | Earlier template score | Current template score |
|---|---:|---:|
| Exploration | `SERVE_EMU_EXPLORATION_TITLE` ≤38.76%, 0/24 method hits | `SERVE_EMU_EXPLORATION_TITLE_NOV_2026` reached 100%, 3/21 hits |
| Exploration | original `EXPLORATION_SCREEN_TITLE` ≤50.18%, 0/22 hits | above NOV_2026 on the visible page |
| Idle Income | `SERVE_EMU_IDLE_INCOME_TITLE` ≤28.61%, 0/15 hits | `SERVE_EMU_IDLE_INCOME_TITLE_NOV_2026` reached 100%, 3/18 hits |
| Idle Income | original `EXPLORATION_IDLE_INCOME_TITLE` did not match current 2026 session | above NOV_2026 on the visible page |

Historical test fixtures from previous game layouts **can** match the older
Frostguard templates. Therefore old PNGs remain in Git, with their enum and
manifest entries intact. They are **not used in the current runtime hot path**
on 720×1280. If the game updates its layout, the current title must fail
safely and emit new Task Coach evidence; it must not silently fall through
to old, weak matches.

## Removed from active probes

- `DoExplorationRoutine.explorationTitleVisible()` now checks only the
  current NOV_2026 title **once**, at the correct 2026 top-left ROI.
- `ExplorationRoutine.isExplorationMainScreenVisible()` does the same.
- `ExplorationRoutine.idleIncomeVisible()` checks NOV_2026 only, then retains
  its **narrow OCR fallback** to support a future text-only layout variation.
- `KnownGameScreenResume` no longer spends time on the early obsolete
  Exploration title when recovering an already-running account.

The older two Exploration matches previously used as many as **two full
attempts each** in `DoExplorationRoutine`, including method fallback
multiscale/greyscale. Reducing them eliminates futile work and repeatedly
captured screenshots. The old Title PNGs stay available for comparison in
source and the private template audit.

## What stays

- `GAME_HOME_RECONNECT` (0 positive hits is expected in a healthy game).
- `GAME_START_WELCOME_BACK_TITLE` and named one-off overlays, because they
  can occur unexpectedly and must be safely detected.
- Rewards templates used for different modal layouts. Similar names and low
  scores on *other screens* are not evidence that every variant is obsolete.
- Precise task-specific action verification and all two-frame click gates.

## Acceptance

Full Maven reactor and static regression tests must pass. After deploying
an updated Windows build, compare a fresh Task Coach `DO_EXPLORATION` and
`EXPLORATION_CHEST` session against the Nov 15 baseline. Confirm the expected
page/title recognition, idle reward claims, no old-title probes, game result,
and scheduler outcome before ticking those tasks as verified.
