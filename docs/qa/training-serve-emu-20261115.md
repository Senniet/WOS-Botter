# 15 Nov 2026 — Serve-EMU troop training: reproducible diagnosis and acceptance

**Status: CODE REGRESSION TESTED, LIVE WINDOWS RESULT STILL UNVERIFIED.**

The operator's seven-stage QA round #1 on build 1.1.66, task `TRAINING_TROOPS`,
uses Task Coach session `20261115-205242-ahykb4`. Operator observation:
Infantry Camp opens, the training panel appears, but the blue Train button is
not pressed; the visible scene shifts and returns to City. No training was
claimed as successful. Four previously logged issues remain open.

Review of the private full-resolution evidence on webserver:

- Captures `008-training_camp_infantry_tab_active-hit.jpg` and
  `046-training_camp_infantry_tab_label_2026-hit.jpg`: actual Infantry/Lancer
  training screens, bright default-selected **VI** card, adjacent **VII** card
  greyed out, blue Train CTA visible at x≈525, y≈1108.
- Captures `011-training_infantry_t11-miss.jpg` and
  `013-training_infantry_t11-miss.jpg`: **City**, not a training panel.
  All legacy T11→T1 template searches missed, while old tier swipes used
  x=690→73 at y=785 (near edge, below the actual card strip).
- Snapshot `20261115T205848Z-11e289b372a8.png`: third consecutive full-screen
  **VI Heroic Marksman** unlock page with “Tap anywhere to continue”. The
  earlier dismissal loop was limited to two pages with only 650ms between taps.

Remediation:

1. A strictly bounded, current-layout-only frame classifier requires the
   selected bright rim, grey immediate next tier and blue Train button **in
   the same 720×1280 capture**. The caller validates the training panel and
   two fresh frames, then checks the blue target again immediately before the
   tap. No OCR-only, free-floating or blind Train tap.
2. Prefer the verified preselected highest available tier over the incompatible
   original tier templates. The older tier/template path remains a fallback
   for other layouts, but all swipes stay inside the card carousel, are
   budgeted, and stop if the training panel disappears.
3. Limit the troop unlock sequence to six separately confirmed prompt pages,
   with a safer wait after each tap. Never tap if the blue backdrop and
   explicit prompt are not visible together.
4. Revalidate the training panel before changing camp tabs; never tap a
   game-footer position as if it were still the troop panel.
5. No automatic currency/resource top-up. A Train submission is not a
   success: the task still requires a later queue transition to TRAINING.

The four fixture images under `training/qa-20261115` contain ONLY the selected
card, grey next card and Train CTA regions on a black background; account,
chat and world-map areas are masked. The unlock fixture preserves only five
blue sample patches and the “Tap anywhere” instruction. Private full-frame
Task Coach screenshots remain on the authenticated LAN diagnostic server.

Regression command on an OpenCV-capable Linux worker:

```sh
TMPDIR="$HOME/.cache/survivalops-tests/training-qa-tmp" \
MAVEN_OPTS="-Djava.io.tmpdir=$HOME/.cache/survivalops-tests/training-qa-tmp" \
./mvnw -DargLine=-Djava.io.tmpdir="$HOME/.cache/survivalops-tests/training-qa-tmp" \
  -pl modules/tasks -am test
```

Next acceptance: publish a new Windows app build through the updater, create a
**new** QA round with that build ID, run Task Coach for Training Troops, verify
that the highest unlocked tier is chosen and Train visibly starts the timer
for each enabled camp. Verify collection of completed troops, popup dismissal,
return to home, scheduler, and negative cases. Operator and ChatGPT reviewer
both sign off; do not close existing QA issues based only on these tests.
