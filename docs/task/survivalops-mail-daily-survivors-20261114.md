# SurvivalOps — Mail, Daily Missions and New Survivors (14 November 2026)

## Evidence from LAN diagnostic snapshots

- **Mail** — an 11:31 CET attempt logged `Could not find mail menu icon`; a later snapshot at 12:22 CET shows the screen inside an *opened Battle Overview report* where the HUD mail icon cannot be seen. Neither finding proves no rewards exist. Screenshot: 20261114T112245Z-7f46292b707f. The Mail detail header is **above** the legacy inbox-title ROI.
- **Daily Missions** — 11:30 CET: the *Rewards* overlay was visibly present, while Daily title recognition failed (scores 47.1 and 38.3) and later `Daily Missions remained visible after verified close action` was logged. Snapshot: 20261114T103001Z-3358b44baca8. The actual reward was already exposed; it must not be re-claimed blindly.
- **New Survivors** — 11:23 CET: the `Survivor Status` panel was already open, showing **32/32 survivors**, **25 Working, 1 Sick, 6 Resting**. The on-screen `Allocate All` control was visible while individual plus buttons in the visible rows were gray. Snapshot: 20261114T102347Z-3cfe1a231598. The log recorded `lower survivor assignment slots did not settle` despite this newer interface.
- **Scheduling** — `Mail Rewards` and `Daily Missions` default regular offsets are 720 minutes and `City Survivors` 360 minutes, though account settings can override them. Multiple higher-priority `ManualRallyJoin` preemptions were also recorded. This work does **not** disable or reorder task priorities.

## Code changes

1. **Mail:** on an opened report, double-verify the dedicated Mail label and blue Back arrow, then leave the report exactly once and verify the inbox list. Otherwise recover a verified City root *before* looking for the mailbox button. A report that cannot be exited remains an unverified/retryable task.
2. **Daily Missions:** when a Rewards overlay is visible, reuse the existing bounded, visually verified dismissal flow and require the underlying Daily page. Never blindly tap a HUD shortcut through the overlay. If the page/claim remains unresolved, keep a short retry with incomplete task status.
3. **New Survivors:** resume a previously opened Survivor Status or Welcome In screen. After a confirmed welcome, verify the Survivor Status and Allocate All label in two frames, press it only once and close the panel with its independently verified X. Retain the old per-slot assignment path for different interfaces. Do not assume six resting survivors can be assigned when workstations are full.
4. **Task state:** explicit `markWorkIncomplete` for failed/unverified steps, so retrying is not recorded as a successful completion.

## Safety and limitations

- Added sanitized real-frame regression fixtures and negative checks against City/World screens. No gem spending, mail deletions, or unverified taps.
- A successful screenshot/template test proves recognition only. The compiled source is **not** installed into the existing Windows runtime, and these changes cannot be claimed as live-validated end-to-end until an explicitly requested MSI/updater release is made and installed.
- No MSI, signed feed, release tag, or Windows installation is changed in this work.
