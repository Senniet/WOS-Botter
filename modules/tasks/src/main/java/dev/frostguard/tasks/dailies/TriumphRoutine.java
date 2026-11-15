package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.vision.convert.GameTimeUtils;

import java.time.LocalDateTime;

/** Alliance Triumph: read and verify daily claims before reporting success. */
public class TriumphRoutine extends DelayedTask {
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
        if (!claimedBefore) {
            ImageSearchResultData claim = templateSearchHelper.locatePattern(
                    TemplatesEnum.ALLIANCE_TRIUMPH_DAILY, SearchConfigConstants.DEFAULT_SINGLE);
            available = claim.isFound();
            if (available) {
                // A single verified tap, not ten taps on a control that may
                // disappear or turn into a different UI after the first click.
                tapped = tapInside(claim);
                sleepTask(1000);
                claimedAfter = templateSearchHelper.locatePattern(
                        TemplatesEnum.ALLIANCE_TRIUMPH_DAILY_CLAIMED,
                        SearchConfigConstants.DEFAULT_SINGLE).isFound();
                if (!claimedAfter) {
                    logWarning(line("Daily claim was tapped, but Claimed state was not recognized"));
                }
            } else {
                logWarning(line("Neither daily Claimed nor daily Claim control recognized; "
                        + "possible outdated Triumph template or screen context"));
            }
        }

        boolean dailyVerified = TriumphClaimVerification.dailyVerified(
                claimedBefore, available, tapped, claimedAfter);
        if (dailyVerified) {
            logInfo(line("Daily Triumph is confirmed claimed"));
            reschedule(GameTimeUtils.dailyResetTime());
        } else {
            markWorkIncomplete("Alliance Triumph: daily reward unresolved; claim and claimed states not verified");
            scheduleUnverifiedRetry();
        }

        // Weekly claims are optional; an absent weekly claim must not override
        // an otherwise verified daily reward. Conversely, never log that a
        // weekly claim was successful merely because we clicked a template.
        ImageSearchResultData weekly = templateSearchHelper.locatePattern(
                TemplatesEnum.ALLIANCE_TRIUMPH_WEEKLY, SearchConfigConstants.DEFAULT_SINGLE);
        if (weekly.isFound()) {
            if (tapInside(weekly)) {
                sleepTask(1500);
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

        // Use the existing screen-aware navigation rather than blindly sending
        // two Android Back presses, which could open a Quit game dialog.
        navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME, this::checkPreemption);
    }

    private void scheduleUnverifiedRetry() {
        Integer configured = profile.getConfig(ConfigurationKeyEnum.ALLIANCE_TRIUMPH_OFFSET_INT,
                Integer.class);
        int minutes = Math.max(10, configured == null ? 10 : configured);
        LocalDateTime proposed = LocalDateTime.now().plusMinutes(minutes);
        LocalDateTime next = GameTimeUtils.clampToResetWindow(proposed);
        logInfo(line("Unverified rewards; next review scheduled at " + next));
        reschedule(next);
    }

    private static String line(String note) {
        return "TriumphRoutine | " + note;
    }
}
