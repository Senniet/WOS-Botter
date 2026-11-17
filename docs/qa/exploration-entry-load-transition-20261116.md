# Exploration loading transition: Task Coach `20261116-203812-1v2iwed`

Actual 1.1.76 uploaded run: scheduler **DEFERRED**, 26 recognized-template method observations, only 1 positive and 13 captured screens; the task did not reach a verified battle. It began on a normal City HUD (City anchor 99.95%), tapped the bottom-left Exploration entry, but checked the exact title before the opening animation finished. At 20:38:17 and 20:38:19 the title scored 41.53%; at 20:38:21 it scored 34.39%. Next captured frame at 20:38:29 was already the fully loaded Exploration screen **with the actual blue Explore button**, but the bot had entered the unrelated global Home/World recovery routine, looking for Reconnect/Welcome Back/Offer/Rewards/quit and causing a false failure.

### Repair

- After clicking the known bottom-left shortcut, **do not treat a single immediate title miss as navigation failure**. Passively observe up to 12 fresh screenshots with a small 200-ms delay; require both the current header and Explore CTA on the **same frame**, in **two consecutive frames**. No extra clicks, no generic recovery and no user menu taps during this transition.
- If the task starts while the loading animation is already underway and neither City nor World root anchor is present, briefly inspect three fresh frames for a valid Exploration page before beginning standard guarded navigation.
- Unknown/persistently blocked screens still stop and retry conservatively: this does not lower title thresholds, guess an Explore button, or treat an unrelated blue button as success.
- Regression tests replay masked/cropped screenshots **003** (opening animation; not yet ready) and **004** (fully loaded Exploration with Explore CTA) from the exact Task Coach run, including the ordered transition and negative alternating/unknown states. The normal quick/slow combat tests are unchanged.

Validation: **784 Java tests, zero failures/errors, 4 skipped; full desktop Maven package SUCCESS**. No claim that the full Windows route is now working until a fresh Task Coach run reaches Explore/Fight and verifies actual completion.
