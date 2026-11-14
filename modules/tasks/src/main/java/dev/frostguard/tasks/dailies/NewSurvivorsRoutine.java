package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import java.time.LocalDateTime;

public class NewSurvivorsRoutine extends DelayedTask {

private static final int MAX_ASSIGNMENT_ATTEMPTS = 20;

public NewSurvivorsRoutine(AccountDescriptor profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

@Override
    protected void execute() {


        logInfo(routineLogNewSurvivorsLine("Scanning for the 'New Survivors' notification."));
        ImageSearchResultData newSurvivors = templateSearchHelper.locatePattern(
                TemplatesEnum.GAME_HOME_NEW_SURVIVORS, SearchConfigConstants.DEFAULT_SINGLE);
        if (newSurvivors.isFound()) {
            tapInside(newSurvivors);
            sleepTask(1000);


            logInfo(routineLogNewSurvivorsLine("New survivors detected. Welcoming them in."));
            ImageSearchResultData welcomeIn = templateSearchHelper.locatePattern(
                    TemplatesEnum.GAME_HOME_NEW_SURVIVORS_WELCOME_IN, SearchConfigConstants.DEFAULT_SINGLE);
            if (welcomeIn.isFound()) {
                // "Welcome In" is the admission action, not the assignment step.
                // Verify that admission changed the UI before assigning anybody.
                tapInside(welcomeIn);
                sleepTask(650);
                ImageSearchResultData welcomeStillVisible = templateSearchHelper.locatePattern(
                        TemplatesEnum.GAME_HOME_NEW_SURVIVORS_WELCOME_IN,
                        SearchConfigConstants.DEFAULT_SINGLE);
                if (welcomeStillVisible.isFound()) {
                    scheduleUnknownRetry("Welcome In remained visible after admission tap", "welcome-not-confirmed");
                    return;
                }
                logInfo(routineLogNewSurvivorsLine("Welcome In dismissed; proceeding to assignment."));
                // The old fixed 10-second wait delayed every admission even when
                // the game had already returned to its normal UI. Refresh after
                // the Welcome In transition; slot recognition below is the gate.
                sleepTask(700);
                emuManager.captureScreen(EMULATOR_NUMBER);

                tapNear(new PointData(309, 20), 3);
                sleepTask(300);


                // 14 Nov diagnostic frame shows a single blue "Allocate All"
                // button while five workers are resting. Prefer this verified
                // bulk assignment over repeatedly probing per-building pluses.
                ImageSearchResultData allocateAll = templateSearchHelper.locatePattern(
                        TemplatesEnum.GAME_HOME_NEW_SURVIVORS_ALLOCATE_ALL_2026,
                        SearchConfigConstants.DEFAULT_SINGLE);
                if (allocateAll.isFound()) {
                    sleepTask(150);
                    ImageSearchResultData stable = templateSearchHelper.locatePattern(
                            TemplatesEnum.GAME_HOME_NEW_SURVIVORS_ALLOCATE_ALL_2026,
                            SearchConfigConstants.DEFAULT_SINGLE);
                    if (!RecognitionConsensus.agrees(allocateAll, stable, 12)) {
                        scheduleUnknownRetry("Allocate All control unstable", "allocate-all-unstable");
                        return;
                    }
                    tapInside(stable);
                    sleepTask(450);
                    emuManager.captureScreen(EMULATOR_NUMBER);
                    ImageSearchResultData stillEnabled = templateSearchHelper.locatePattern(
                            TemplatesEnum.GAME_HOME_NEW_SURVIVORS_ALLOCATE_ALL_2026,
                            SearchConfigConstants.DEFAULT_SINGLE);
                    if (stillEnabled.isFound()) {
                        scheduleUnknownRetry("Allocate All remained enabled after tap", "allocate-all-unconfirmed");
                        return;
                    }
                    logInfo(routineLogNewSurvivorsLine("Allocate All blue control disappeared after tap."));
                    this.reschedule(LocalDateTime.now().plusMinutes(
                            profile.getConfig(ConfigurationKeyEnum.CITY_ACCEPT_NEW_SURVIVORS_OFFSET_INT, Integer.class)));
                    return;
                }

                logInfo(routineLogNewSurvivorsLine("Assigning survivors to available building slots."));
                emuManager.swipeScreen(EMULATOR_NUMBER, new PointData(340, 610), new PointData(340, 900));
                sleepTask(200);

                if (!assignVisibleSlots("lower")) {
                    scheduleUnknownRetry("lower survivor assignment slots did not settle", "lower-slots");
                    return;
                }


                emuManager.swipeScreen(EMULATOR_NUMBER, new PointData(340, 900), new PointData(340, 610));
                sleepTask(200);
                if (!assignVisibleSlots("upper")) {
                    scheduleUnknownRetry("upper survivor assignment slots did not settle", "upper-slots");
                    return;
                }

                logInfo(routineLogNewSurvivorsLine("Survivor assignment complete. Planning next run task."));
                this.reschedule(LocalDateTime.now().plusMinutes(
                        profile.getConfig(ConfigurationKeyEnum.CITY_ACCEPT_NEW_SURVIVORS_OFFSET_INT, Integer.class)));
            } else {
                scheduleUnknownRetry("notification opened but Welcome In control was not detected", "welcome-control");
                return;
            }

        } else {
            logInfo(routineLogNewSurvivorsLine("Zero new survivors detected. Planning next run task."));
            this.reschedule(LocalDateTime.now().plusMinutes(
                    profile.getConfig(ConfigurationKeyEnum.CITY_ACCEPT_NEW_SURVIVORS_OFFSET_INT, Integer.class)));

        }

    }

private boolean assignVisibleSlots(String section) {
        for (int attempt = 0; attempt < MAX_ASSIGNMENT_ATTEMPTS; attempt++) {
            ImageSearchResultData plusButton = templateSearchHelper.locatePattern(
                    TemplatesEnum.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON, SearchConfigConstants.DEFAULT_SINGLE);
            if (!plusButton.isFound()) {
                return true;
            }
            sleepTask(150);
            ImageSearchResultData confirmed = templateSearchHelper.locatePattern(
                    TemplatesEnum.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON, SearchConfigConstants.DEFAULT_SINGLE);
            if (!RecognitionConsensus.agrees(plusButton, confirmed, 12)) {
                logWarning(routineLogNewSurvivorsLine(section + " assignment control unstable; stopping"));
                return false;
            }
            tapInside(confirmed);
            sleepTask(350);
            ImageSearchResultData afterTap = templateSearchHelper.locatePattern(
                    TemplatesEnum.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON, SearchConfigConstants.DEFAULT_SINGLE);
            if (afterTap.isFound() && RecognitionConsensus.agrees(confirmed, afterTap, 12)) {
                logWarning(routineLogNewSurvivorsLine(section
                        + " assignment control did not change after tap; refusing repeated taps"));
                return false;
            }
        }

        ImageSearchResultData remaining = templateSearchHelper.locatePattern(
                TemplatesEnum.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON, SearchConfigConstants.DEFAULT_SINGLE);
        logDebug(routineLogNewSurvivorsLine(section + " assignment pass reached "
                + MAX_ASSIGNMENT_ATTEMPTS + " taps; remaining control found=" + remaining.isFound()));
        return !remaining.isFound();
    }

private void scheduleUnknownRetry(String reason, String type) {
        LocalDateTime retryAt = LocalDateTime.now().plusMinutes(5);
        String snapshot = TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER, "newsurvivors", type);
        logWarning(routineLogNewSurvivorsLine(reason + "; retrying at "
                + retryAt.format(DATETIME_FORMATTER) + "; " + snapshot + "."));
        markWorkIncomplete("New Survivors: " + reason);
        reschedule(retryAt);
    }

@Override
    protected LaunchPoint getRequiredStartLocation() {
        return LaunchPoint.HOME;
    }

private String routineLogNewSurvivorsLine(String note) {
        return "NewSurvivorsRoutine | " + note;
    }
}
