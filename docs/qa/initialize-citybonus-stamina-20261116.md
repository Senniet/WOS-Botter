# 16 Nov 2026: Initialize mistakenly opens City Bonus

## Reproduced evidence
- Windows diagnostic 10:13:28 CET on version 1.1.71: StaminaHelper logged "Stamina dialog was not confirmed after HUD tap" and uploaded snapshot `20261116T091328Z-be3fba21d3a7.png`. The snapshot showed the **City Bonus / Wars** screen, not **Chief Stamina / Obtain more**.
- Reproduced directly on the already-running TrueNAS Serve-EMU Android 16 game (`emulator-5584`, 720×1280): pressing the `staminaHudArrow2026.png` image at (34,140) **opens City Bonus**. The asset was misnamed in the earlier recognition code.
- Closed City Bonus using its observed header back (43,36); pressed the **green stamina bar** at (40,106). This opened **Obtain more**, with **Chief Stamina 582/200** and the normal reward/purchase list. We did not claim, buy, or replace items.
- A separate live Android Back closed City Bonus and returned to World (root tab visible). This confirms the bounded startup recovery path can safely dismiss this known game page.

## Source repair
- The StaminaHelper no longer clicks the misidentified City Bonus arrow or unverified legacy coordinates. It requires an existing City/World anchor, a green stamina bar observed in *two independent frames*, and taps only within (29,103)-(56,110). If the bar is absent/unstable, it logs and skips reading without clicking another HUD icon. The existing positive Obtain more title, stamina OCR and safe cleanup are retained.
- The startup known-page recovery now recognizes **City Bonus** from the exact, current in-game title and independent back arrow on the same frame, and confirms that state on the next frame before one Android Back. This prevents an already-open City Bonus screen from trapping Initialize on the next run.
- The legacy `SERVE_EMU_STAMINA_HUD_ARROW_2026` asset ID is left in place for compatibility with audit/history and is explicitly commented as **City Bonus**; it is no longer used as a stamina action.

## Acceptance
- New and old screenshot replay tests: current City and World positive stamina bars, no stamina match on Hero chest and City Bonus, exact City Bonus Back recognition, no Back on other pages, and normal stamina reading service mock/exception tests.
- Full Maven tasks reactor: **755 tests, zero failures/errors, 4 skipped**. Live click target verified manually on the real emulator.
- Windows **1.1.71** still contains the bug. The fix is for release **1.1.72**, and a fresh Initialize Windows run is needed to independently verify the full task sequence.
