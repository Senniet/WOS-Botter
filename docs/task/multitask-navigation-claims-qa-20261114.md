# SurvivalOps multitask incident — 13 Nov 2026 23:43 to 14 Nov 00:09 CET

## Incident evidence

The private LAN diagnostics inbox was examined in `/home/michel/survivalops-diagnostics/inbox/2026-11-13`. Timestamped snapshots were used to create *redacted, limited-region* regression samples in `modules/automation/src/test/resources/navigation/recovery-20261113/` and in the corresponding `modules/tasks/src/test/resources` folders.

| CET | Observed failure | Verified repair/test |
| --- | --- | --- |
| 23:43–23:45 | Arena Challenge List and Daily Challenges chest checked; next task stayed on Arena overlay | Captured list title and X at y≈185. Added a double-verified list X close and subsequent verified Home navigation; no blind Android Back if a chest overlay is unrecognized |
| 23:50 | `AllianceHelper` logged missing War button; contribution modal (user screenshot: `Long Live Our Alliance`) remained open | AllianceTech closes the modal only after two stable OCR readings of the exact title, then checks Home through NavigationHelper. On the user screenshot the title OCR region (145,20)-(600,105) reads `Long Live Our Alliance`; no gem contribution was added |
| 23:52 | `HomeNotFoundException` on Intel despite visible blue arrow; old back template match scored 71.6%; resource recovery scanned Wood and Meat | Added actual Intel, Events, Hero, and Arena header variants from private screenshots, two-frame consensus, corrected top header ROIs; prioritized verified on-screen Back over unrelated Wood/Meat detection; preserved Quit game safety and popup rejection |
| 23:54–23:55 | Exploration outer Claim opened `Idle Income`, but title templates scored only ~21%; inner Claim visibly ready | New current-title template tested at correct ROI y=233..330 together with live green inner Claim detector. OCR fallback title ROI corrected from the `Idle Time` line to the `Idle Income` header |
| 23:58 | Hero Recruitment displayed two countdowns and keys but failed `currentHeroRecruitmentVisible` | Current modern heading artwork is recognized on the exact 23:58 screenshot; old template remains fallback. No unverified key/gem spending added |
| 23:59–00:00 | Do Exploration entry not recognized after previous tasks | Improved shared safe Home navigation and page-header recovery; the Do Exploration end-to-end execution still requires an online emulator |
| 00:01 | System mail held a persistent unread `[Survey] Alliance Communication` invitation with no reward | After a claim pass with no progress, a single remaining System Survey title confirmed via two OCR frames can be left unread without being reported as an unclaimed reward. All other mail items retain their existing reward verification/retry handling. User screenshot OCR of (125,155)-(675,285) confirms `[Survey] Alliance Communication` |
| 00:05 | Research had no unlocked action due to prerequisite/Furnace level, then left Tech Research open | On unavailable research, attempt safe return to verified Home. Screenshot OCR of old title area (80,0)-(360,80) reads `Tech Research`; no locked research or resources touched |
| 00:07 | Tundra Truck `Remaining Departures: 0/4` reported unreadable; next task could not leave Events | Old narrow OCR x=477..527 clipped the `4`; widened to x=483..578, y=1142..1182 and allowed labeled counter strings. Java test uses actual snapshot + OCR and confirms 0/4. Existing no-departure dispatch block remains. Tundra event attempts return Home through verified header navigation |

## Recovery safety

`NavigationHelper.ensureCorrectScreenLocation(HOME/WORLD)` still uses 2-frame agreement for recognized arrows, limited recovery passes, and refuses blind Android Back when the game may display a Quit game confirmation. Unknown Rewards or other popup screens are **not** treated as pages with a safe Back arrow. When a close or Home postcondition cannot be verified, only log and schedule retry; never duplicate a claim or paid action. No gem or in-app purchase flow was modified.

## Verification / limitation

- Tests exercised the recorded screenshot from the actual failed time for Intel, Events/Tundra, Hero, Arena, and Exploration; negative popup matches were rejected.
- 1,213 Java unit/regression tests (296 suites, 0 failures, 0 errors, 4 skipped) passed for `-pl modules/tasks -am` including the final Hero/Arena header additions.
- Maven package preflight for provisional 1.1.46 passed (source bundle only; no MSI or update feed published).
- **No complete WOS_Sennie121 automatic task sequence was rerun:** the emulator became unauthorized/offline; the qemu process was absent on TrueNAS when inspected at 00:25 CET. Do not claim live end-to-end success or publish a Windows release on the strength of screenshot regressions alone.
