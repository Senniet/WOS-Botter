# SurvivalOps emulator screen-coordinate baseline

This is the starting checklist for investigating wrong clicks on a fresh
Whiteout Survival emulator. Do **not** fix isolated button coordinates until
these shared invariants have been checked.

## Tested baseline (Android 16 serve-emu, 15 November 2026)

- Android: portrait, physical screenshot **720 × 1280**, density **320 DPI**.
- Whiteout Survival > Settings > Interface-edge or screen Distance: **0**.
- Keep language and graphics quality consistent with recognition templates.
- Check the active Whiteout Survival profile before assigning a queue worker.
- Android's `wm size` and `wm density` describe the Android display, **not**
  necessarily the screenshot/Unity layout. The engine logs physical screenshot
  dimensions and the normalized recognition dimensions separately.
- Unity's saved `Screenmanager Resolution Width=640` and `Height=1138` are **not**
  proof of wrong ADB click coordinates. Diagnose the ADB screenshot and mapped
  input geometry instead. A 720 × 1280 capture is identity-mapped.

## Read-only validation

Using the ADB endpoint configured in SurvivalOps (not a guessed emulator port):

```sh
adb -s DEVICE shell wm size
adb -s DEVICE shell wm density
adb -s DEVICE shell dumpsys window | grep mCurrentFocus
adb -s DEVICE exec-out screencap -p > screenshot.png
```

Check that the screenshot is a correct portrait game frame and compare where the
button visually appears with the configured recognition ROI. Before acting on a
button, confirm the page and the expected post-click transition; never use a
blind button tap to diagnose a coordinate mismatch.

`SETTING_CANVAS_ADJUST_SIZE_NEW_2` may appear in the game's Unity PlayerPrefs
as `0` on a device whose in-game distance is already zero, but this key is
**not an Android display setting**. Do not edit the app's private preferences
while the game is running; manual in-game adjustment is the current supported
configuration procedure.

## Shared engine safety rules

- `ScreenGeometry` normalizes supported portrait captures into the historical
  720 × 1280 recognition space and maps taps back to the real capture pixels.
- Serve-EMU and BlueStacks refuse input if safe geometry cannot be confirmed.
- Each physical ADB serial owns one queue slot, including aliases such as
  configured index `0` versus direct `host:port` on the same process.
- A monitor only evaluates preemption and injection rules for the profile that
  currently owns the physical device. A screenshot of profile A is **not**
  evidence that profile B should take action.
- Every dispatched input invalidates the cached OCR frame; OCR after a tap
  must use a fresh capture unless the caller explicitly controls the frame.
- Interrupted slot waiters are removed from the waiting queue.

## When diagnosing remaining misses

For each failed action record: device/serial, current game profile, raw and
normalized capture dimensions, detected page, expected template, template ROI,
match confidence, attempted target center and expected postcondition. Preserve
both screenshots before/after if diagnostics sharing is enabled. Do not lower
confidence thresholds merely to compensate for a wrong coordinate system.

These are code-level and image-fixture validations. A new Windows release still
requires a live end-to-end test of several tasks on the current emulator.
