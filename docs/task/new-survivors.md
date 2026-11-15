# New Survivors — admission, allocation, return Home

## Current 2026 game evidence

The 2026 Survivor Status panel on the 720×1280 serve-emu has a stable
`Survivor Status` heading and top-right X on **both Status and Details tabs**.
The top HUD has a survivor-counter portrait icon. Never tap the old fixed
coordinate `(309,20)` without first verifying the current Home HUD and the
counter icon. The old routine did not dismiss Survivor Status after assigning.
That allowed subsequent queued tasks to click on the wrong popup/tab.

Actual, minimally redacted diagnostic frames:

- 2026-11-14: 21 working, 11 resting, blue enabled `Allocate All`.
- 2026-11-15, 10:37 local: 26 working, 4 resting, **grey disabled** `Allocate All`,
  yet text/template matching still found the label and wrongly reported
  `allocate-all-unconfirmed`.

The label is identical in both states. Require the Survivor Status heading,
the selected **Status tab**, the appropriate button identity, and background
blue/grey color. The screenshots establish the visible color states; they do
not demonstrate that every survivor is guaranteed a workplace.

## Completion evidence

1. Only attempt when the New Survivors home notification is verified.
2. Tap verified `Welcome In` and require it to disappear. This proves admission,
   not yet allocation or full task completion.
3. Open Survivor Status only via a verified Home HUD icon, or reuse the
   already-open status panel. Never blindly select `Details`.
4. If `Allocate All` is blue, verify its identity from two fresh frames,
   tap only once, and require a change to the positively identified grey state.
5. If `Allocate All` was already grey, report **no allocatable slots**;
   resting survivors may remain when workplaces are at capacity. No tap needed.
6. Close the panel using the detected X regardless of which bottom tab was
   selected; ensure the heading disappears, then verify actual Home via the
   shared navigation helper before scheduling the normal interval.
7. Unknown recognition, unchanged blue button or Home not verified must mark
   the task incomplete, produce a diagnostic frame and schedule a bounded
   five-minute retry. No blind repeating Claim/Allocate or phantom success.

The live installed Windows client must be validated after a new release.
Unit tests and saved real frames do not establish live task completion.
