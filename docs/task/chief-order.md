# Chief Orders on Serve-EMU

The Chief Order board at 720×1280 contains distinct named books for Rush Job,
Urgent Mobilization and Productivity Day. Their book templates are absent under
`Active` or `On cooldown` banners. Recognize the exact book name and its own
status before searching for an available-order icon. Both banners mean the
order has already been used; do not activate or claim it again. Confirm the
banner on a second fresh frame. A countdown read on only one frame is not a
validated timer; inconsistent or unreadable OCR triggers an explicitly labeled
30-minute availability recheck, not a falsely precise cooldown.

An available book still requires a positive icon match, the expected book title,
and an explicitly **Free** button before any Enact action on Serve-EMU.
Productivity Day previously displayed a 50,000 Chief Order points cost;
points, premium currency and other priced orders are not spent automatically.
An authorized claim can either produce a verified Rewards overlay or return
straight to City. A cooldown/active order returns directly from the list with
one confirmed Back tap; a book may need Book → List → City (two Back taps).
Unknown pages never authorize blind taps. Global Home recovery is fallback only.

Regression assets: `modules/tasks/src/test/resources/chieforder/live-20261117/`
contains redacted real emulator frames for an available order, an active Urgent
Mobilization, a cooldown Productivity Day, and a priced Productivity Day book.
`ChiefOrderScopedExitTest` checks no paid action from used states and the exact
number of back taps. Manual ADB confirmed the list → City route on emulator-5554
on 2026-11-17. This is not a Windows-client bot E2E pass; that requires a real
scheduled run on the current updated client and proof of final City and future
schedule. No new signed/updater MSI is implied by these source tests.
