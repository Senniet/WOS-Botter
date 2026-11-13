# Hero Recruitment — Advanced, Epic, then Points Chest (2026-11-13)

## Reproduced on WOS_Sennie121

- Emulator AVD `WOS_Sennie121` reached through ADB `192.168.1.20:15559`, normalized 720x1280.
- The existing modern routine looked only for a green **Free** button and never tried the orange `Recruit once x1` buttons with saved keys. It also checked **Points Chest first**, instead of after both categories. The desktop only exposed the single parent toggle.
- On the live Hero Recruitment screen, Advanced free had a timer with **2 gray keys** and Epic free had a timer with **3 gold keys**. One intentional manual Advanced `Recruit once x1` spent exactly one gray key (2 → 1), produced the Rewards overlay and returned to Hero Recruitment after a verified exit. One intentional manual Epic `Recruit once x1` spent exactly one gold key (3 → 2), and also returned after Rewards. Gems stayed at **5,560**. No gem action was used.
- After both recruitments, Points Chest was checked. The tooltip showed **14.72%**, so there was no ready claim. No Points Chest reward was spent or claimed on this attempt.
- Live captures were redacted to retain only HUD key counters, key-button bands, category and Rewards anchors. The regression tests use these exact images and the project's real OpenCV/Tesseract recognition code.

## New controls (Other Tasks)

The existing Hero Recruitment parent toggle has five visible per-profile sub-options:

1. Advanced Recruitment free (default on)
2. Epic Recruitment free (default on)
3. Advanced — use already-owned keys, max 1 per task run (default **off**)
4. Epic — use already-owned keys, max 1 per task run (default **off**)
5. Points Chest after both types (default on)

Saved-key recruitment requires explicit opt-in. It performs two matching, positive top-HUD key count readings, recognizes the matching left-side **Recruit once ×1** key artwork (not a generic orange button), taps once, verifies Rewards heading and safe exit, returns to the recruitment screen, and confirms exactly a one-key decrement. No gems, no multipulls, no blind retries. An ambiguous result stops the remaining sequence and schedules retry.

The order is Advanced → Epic → Points Chest. A disabled category is skipped and does not prevent the next one.

## Test scope

The two user-requested types of **manual live recruitment were demonstrated** and the new Java matching/OCR/order policies were tested against those recorded live frames. Automated execution from the installed Windows SurvivalOps desktop client has not been claimed or directly observed in this validation. The GUI binding is statically verified on headless Linux. Tests requiring a visible JavaFX desktop need a graphical runner rather than the headless webserver.
