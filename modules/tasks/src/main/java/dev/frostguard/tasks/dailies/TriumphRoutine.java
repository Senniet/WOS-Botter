package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.vision.ocr.OcrEngine;
import dev.frostguard.vision.ocr.OcrException;
import dev.frostguard.tasks.dailies.TriumphClaimVerification.ChestProgress;
import dev.frostguard.tasks.dailies.TriumphClaimVerification.DailyState;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.vision.convert.GameTimeUtils;

import java.time.LocalDateTime;

/** Alliance Triumph: read and verify daily claims before reporting success. */
public class TriumphRoutine extends DelayedTask {
    // 720x1280 Activity Triumph personal-chest counter (e.g. 50/800).
    // This region deliberately excludes the weekly total and reset countdown.
    private static final PointData CHEST_COUNTER_TOP_LEFT = new PointData(85, 900);
    private static final PointData CHEST_COUNTER_BOTTOM_RIGHT = new PointData(230, 949);
    private static final OcrSettingsData CHEST_COUNTER_OCR = OcrSettingsData.configurator()
            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
            .allowedGlyphs("0123456789/")
            .build();
    private static final int UNVERIFIED_RETRY_MINUTES = 10;
    public TriumphRoutine(AccountDescriptor profile, TpDailyTaskEnum task) {
        super(profile, task);
    }

    @Override
    public LaunchPoint getRequiredStartLocation() {
        return LaunchPoint.HOME;
    }

    @Override
    protected void execute() {
        logInfo(line("Opening Alliance"));
        tapInside(new dev.frostguard.api.domain.PointData(493, 1187),
                new dev.frostguard.api.domain.PointData(561, 1240));
        sleepTask(3000);

        ImageSearchResultData triumphButton = templateSearchHelper.locatePattern(
                TemplatesEnum.ALLIANCE_TRIUMPH_BUTTON, SearchConfigConstants.DEFAULT_SINGLE);
        if (!triumphButton.isFound()) {
            logWarning(line("Triumph menu not recognized; daily rewards unverified"));
            markWorkIncomplete("Alliance Triumph: menu unavailable, no daily reward verified");
            scheduleUnverifiedRetry();
            return;
        }

        tapInside(triumphButton);
        sleepTask(2000);

        boolean claimedBefore = templateSearchHelper.locatePattern(
                TemplatesEnum.ALLIANCE_TRIUMPH_DAILY_CLAIMED,
                SearchConfigConstants.DEFAULT_SINGLE).isFound();
        boolean available = false;
        boolean tapped = false;
        boolean claimedAfter = false;
        ChestProgress progress = null;
        if (!claimedBefore) {
            ImageSearchResultData claim = templateSearchHelper.locatePattern(
                    TemplatesEnum.ALLIANCE_TRIUMPH_DAILY, SearchConfigConstants.DEFAULT_SINGLE);
            available = claim.isFound();
            if (available) {
                // A single verified tap, not ten taps on a control that may
                // disappear or turn into a different UI after the first click.
                tapped = tapInside(claim);
                sleepTask(1000);
                // The real Triumph claim often opens *Weekly Rewards — Tap
                // anywhere to close*, obscuring the Claimed indicator.
                // Dismiss only this double-confirmed named overlay, never an
                // unknown popup or premium confirmation.
                dismissVerifiedWeeklyRewards();
                claimedAfter = templateSearchHelper.locatePattern(
                        TemplatesEnum.ALLIANCE_TRIUMPH_DAILY_CLAIMED,
                        SearchConfigConstants.DEFAULT_SINGLE).isFound();
                if (!claimedAfter) {
                    logWarning(line("Daily claim was tapped, but Claimed state was not recognized"));
                }
            } else {
                progress = readStableChestProgress();
            }
        }

        DailyState daily = TriumphClaimVerification.dailyState(
                claimedBefore, available, tapped, claimedAfter, progress);
        switch (daily) {
            case CLAIMED -> {
                logInfo(line("Daily Triumph reward confirmed claimed"));
                reschedule(GameTimeUtils.dailyResetTime());
            }
            case NOT_READY -> {
                logInfo(line("Personal daily chest is not claimable yet: " + progress.current()
                        + "/" + progress.required() + " points; no reward collected"));
                scheduleProgressReview();
            }
            case UNVERIFIED -> {
                logWarning(line("Daily claim unresolved: neither a verified Claimed state nor a "
                        + "stable not-ready counter was detected"));
                markWorkIncomplete("Alliance Triumph: daily reward status unverified");
                scheduleUnverifiedRetry();
            }
        }

        // Weekly claims are optional; an absent weekly claim must not override
        // an otherwise verified daily reward. Conversely, never log that a
        // weekly claim was successful merely because we clicked a template.
        ImageSearchResultData weekly = templateSearchHelper.locatePattern(
                TemplatesEnum.ALLIANCE_TRIUMPH_WEEKLY, SearchConfigConstants.DEFAULT_SINGLE);
        if (weekly.isFound()) {
            if (tapInside(weekly)) {
                sleepTask(1500);
                dismissVerifiedWeeklyRewards();
                boolean stillVisible = templateSearchHelper.locatePattern(
                        TemplatesEnum.ALLIANCE_TRIUMPH_WEEKLY,
                        SearchConfigConstants.DEFAULT_SINGLE).isFound();
                if (stillVisible) {
                    logWarning(line("Weekly reward control still visible after tap; no verified claim"));
                    markWorkIncomplete("Alliance Triumph: weekly reward click not verified");
                    scheduleUnverifiedRetry();
                } else {
                    logInfo(line("Weekly reward control disappeared after tap; "
                            + "not proof that the reward was collected"));
                    // With no independently confirmed weekly-claimed state,
                    // retain an unfinished outcome until a subsequent run
                    // supplies evidence. Disappearance can be a reward popup.
                    markWorkIncomplete("Alliance Triumph: weekly reward claim not independently verified");
                    scheduleUnverifiedRetry();
                }
            }
        } else {
            logInfo(line("Weekly claim not detected (may be unavailable or already claimed)"));
        }

        // An additional payout modal can appear after the weekly-check.
        // Handle that exact overlay before invoking generic Home recovery.
        dismissVerifiedWeeklyRewards();
        try {
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME, this::checkPreemption);
            logInfo(line("Home verified after Triumph rewards"));
        } catch (dev.frostguard.engine.error.HomeNotFoundException missing) {
            markWorkIncomplete("Alliance Triumph reward flow ended outside Home");
            logWarning(line("Home unverified after Triumph: "+missing.getMessage()));
        }
    }

    private void dismissVerifiedWeeklyRewards() {
        var result=TriumphWeeklyRewardsFrame.dismiss(
                ()->{checkPreemption();return emuManager.captureScreen(EMULATOR_NUMBER);},
                point->{checkPreemption();tapNear(point);},
                ()->sleepTask(250));
        if(result==TriumphWeeklyRewardsFrame.Result.DISMISSED) {
            logInfo(line("Exact Weekly Rewards overlay closed in one verified tap"));
        } else if(result==TriumphWeeklyRewardsFrame.Result.UNVERIFIED) {
            markWorkIncomplete("Alliance Triumph Weekly Rewards overlay exit not confirmed");
            logWarning(line("Weekly Rewards changed or remained visible, no repeat tap"));
        }
    }

    private ChestProgress readStableChestProgress() {
        try {
            String first = OcrEngine.recognizeText(emuManager.captureScreen(EMULATOR_NUMBER),
                    CHEST_COUNTER_TOP_LEFT, CHEST_COUNTER_BOTTOM_RIGHT, CHEST_COUNTER_OCR);
            ChestProgress a = TriumphClaimVerification.parseChestProgress(first);
            if (a == null) {
                logWarning(line("Personal chest progress could not be read: '" + first + "'"));
                return null;
            }
            sleepTask(200);
            String second = OcrEngine.recognizeText(emuManager.captureScreen(EMULATOR_NUMBER),
                    CHEST_COUNTER_TOP_LEFT, CHEST_COUNTER_BOTTOM_RIGHT, CHEST_COUNTER_OCR);
            ChestProgress b = TriumphClaimVerification.parseChestProgress(second);
            if (!a.equals(b)) {
                logWarning(line("Personal chest progress not stable across two captures: '"
                        + first + "' versus '" + second + "'"));
                return null;
            }
            return a;
        } catch (OcrException failed) {
            logWarning(line("Personal chest OCR unavailable: " + failed.getClass().getSimpleName()));
            return null;
        }
    }

    private void scheduleProgressReview() {
        Integer configured = profile.getConfig(ConfigurationKeyEnum.ALLIANCE_TRIUMPH_OFFSET_INT,
                Integer.class);
        LocalDateTime next = TriumphClaimVerification.nextProgressReview(
                LocalDateTime.now(), GameTimeUtils.dailyResetTime(),
                configured == null ? 120 : configured);
        logInfo(line("Chest not unlocked; next progress review at " + next));
        reschedule(next);
    }

    private void scheduleUnverifiedRetry() {
        LocalDateTime next = LocalDateTime.now().plusMinutes(UNVERIFIED_RETRY_MINUTES);
        logInfo(line("Unverified rewards; next review scheduled at " + next));
        reschedule(next);
    }

    private static String line(String note) {
        return "TriumphRoutine | " + note;
    }
}
