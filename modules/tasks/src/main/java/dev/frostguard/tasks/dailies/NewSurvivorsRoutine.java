package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig;
import dev.frostguard.engine.nav.SearchConfigConstants;
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


        // An interrupted run can leave the Survivor Status / Allocate All
        // popup on screen. Resume it before navigating to the City or tapping
        // the New Survivors notification a second time.
        if (isSurvivorStatusVisible()) {
            logInfo(routineLogNewSurvivorsLine(
                    "Resuming the previously opened Survivor Status panel"));
            if (!allocateAllFromVerifiedStatus()) {
                scheduleUnknownRetry("Survivor Status allocation not confirmed", "allocate-all-unverified");
            } else {
                rescheduleNextNormalVisit();
            }
            return;
        }

        ImageSearchResultData alreadyWelcoming = templateSearchHelper.locatePattern(
                TemplatesEnum.GAME_HOME_NEW_SURVIVORS_WELCOME_IN,
                SearchConfigConstants.DEFAULT_SINGLE);
        if (!alreadyWelcoming.isFound()) {
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME, this::checkPreemption);
        }
        logInfo(routineLogNewSurvivorsLine("Scanning for the 'New Survivors' notification."));
        ImageSearchResultData newSurvivors = alreadyWelcoming.isFound()
                ? ImageSearchResultData.miss() : templateSearchHelper.locatePattern(
                TemplatesEnum.GAME_HOME_NEW_SURVIVORS, SearchConfigConstants.DEFAULT_SINGLE);
        if (newSurvivors.isFound() || alreadyWelcoming.isFound()) {
            if (newSurvivors.isFound()) {
                tapInside(newSurvivors);
                sleepTask(1000);
            }

            logInfo(routineLogNewSurvivorsLine("New survivors detected. Welcoming them in."));
            ImageSearchResultData welcomeIn = alreadyWelcoming.isFound()
                    ? alreadyWelcoming : templateSearchHelper.locatePattern(
                    TemplatesEnum.GAME_HOME_NEW_SURVIVORS_WELCOME_IN, SearchConfigConstants.DEFAULT_SINGLE);
            if (welcomeIn.isFound()) {
                tapInside(welcomeIn);
                // The current game transitions to Survivor Status, where
                // the central Allocate All replaces tapping 20 per-building
                // +/- buttons. Poll the actual UI before any legacy swipes.
                for (int settle=0; settle<6; settle++) {
                    sleepTask(350);
                    if (isSurvivorStatusVisible()) {
                        logInfo(routineLogNewSurvivorsLine(
                                "Welcome In accepted; Survivor Status is now visible"));
                        if (!allocateAllFromVerifiedStatus()) {
                            scheduleUnknownRetry("New survivors welcome confirmed, allocation still unverified",
                                    "allocate-all-unverified");
                        } else {
                            rescheduleNextNormalVisit();
                        }
                        return;
                    }
                }
                logInfo(routineLogNewSurvivorsLine(
                        "Modern Survivor Status not visible; preserving the legacy per-slot assignment flow."));
                sleepTask(10000);

                tapNear(new PointData(309, 20), 3);
                sleepTask(300);


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

private static final SearchConfig SURVIVOR_STATUS_TITLE_SEARCH = SearchConfig.builder()
        .withMaxAttempts(1).withThreshold(90).withArea(AreaData.of(10,320,260,420)).build();
private static final SearchConfig SURVIVOR_STATUS_ALLOCATE_SEARCH = SearchConfig.builder()
        .withMaxAttempts(1).withThreshold(92).withArea(AreaData.of(180,1040,560,1190)).build();
private static final SearchConfig SURVIVOR_STATUS_CLOSE_SEARCH = SearchConfig.builder()
        .withMaxAttempts(1).withThreshold(92).withArea(AreaData.of(640,310,720,405)).build();

private boolean isSurvivorStatusVisible() {
    return templateSearchHelper.locatePattern(
            TemplatesEnum.SERVE_EMU_SURVIVORS_STATUS_TITLE_NOV_2026,
            SURVIVOR_STATUS_TITLE_SEARCH).isFound();
}

/** One verified modern Allocate All command; never spam disabled plus buttons. */
private boolean allocateAllFromVerifiedStatus() {
    ImageSearchResultData title = templateSearchHelper.locatePattern(
            TemplatesEnum.SERVE_EMU_SURVIVORS_STATUS_TITLE_NOV_2026,
            SURVIVOR_STATUS_TITLE_SEARCH);
    ImageSearchResultData allocate = templateSearchHelper.locatePattern(
            TemplatesEnum.SERVE_EMU_SURVIVORS_ALLOCATE_ALL_NOV_2026,
            SURVIVOR_STATUS_ALLOCATE_SEARCH);
    if (!title.isFound() || !allocate.isFound()) return false;
    sleepTask(170);
    ImageSearchResultData titleAgain = templateSearchHelper.locatePattern(
            TemplatesEnum.SERVE_EMU_SURVIVORS_STATUS_TITLE_NOV_2026,
            SURVIVOR_STATUS_TITLE_SEARCH);
    ImageSearchResultData allocateAgain = templateSearchHelper.locatePattern(
            TemplatesEnum.SERVE_EMU_SURVIVORS_ALLOCATE_ALL_NOV_2026,
            SURVIVOR_STATUS_ALLOCATE_SEARCH);
    if (!RecognitionConsensus.agrees(title,titleAgain,12)
            || !RecognitionConsensus.agrees(allocate,allocateAgain,12)) return false;
    logInfo(routineLogNewSurvivorsLine(
            "Survivor Status and Allocate All verified in two frames; tapping once"));
    tapInside(allocateAgain);
    sleepTask(700);
    if (!isSurvivorStatusVisible()) {
        logWarning(routineLogNewSurvivorsLine(
                "Survivor Status vanished after Allocate All; unable to verify outcome"));
        return false;
    }
    logInfo(routineLogNewSurvivorsLine(
            "Allocate All command submitted. The game decides available workstations; "
                    + "resting or sick survivors are not proof that Welcome In failed."));
    return closeVerifiedSurvivorStatus();
}

private boolean closeVerifiedSurvivorStatus() {
    ImageSearchResultData close = templateSearchHelper.locatePattern(
            TemplatesEnum.SERVE_EMU_SURVIVORS_STATUS_CLOSE_NOV_2026,
            SURVIVOR_STATUS_CLOSE_SEARCH);
    if (!isSurvivorStatusVisible() || !close.isFound()) return false;
    sleepTask(170);
    ImageSearchResultData closeAgain = templateSearchHelper.locatePattern(
            TemplatesEnum.SERVE_EMU_SURVIVORS_STATUS_CLOSE_NOV_2026,
            SURVIVOR_STATUS_CLOSE_SEARCH);
    if (!RecognitionConsensus.agrees(close,closeAgain,12)
            || !isSurvivorStatusVisible()) return false;
    tapInside(closeAgain);
    sleepTask(350);
    if (isSurvivorStatusVisible()) {
        logWarning(routineLogNewSurvivorsLine(
                "Survivor Status still open after its verified close action"));
        return false;
    }
    return true;
}

private void rescheduleNextNormalVisit() {
    Integer offset = profile.getConfig(ConfigurationKeyEnum.CITY_ACCEPT_NEW_SURVIVORS_OFFSET_INT,
            Integer.class);
    reschedule(LocalDateTime.now().plusMinutes(offset == null ? 15 : Math.max(5,offset)));
}

private boolean assignVisibleSlots(String section) {
        for (int attempt = 0; attempt < MAX_ASSIGNMENT_ATTEMPTS; attempt++) {
            ImageSearchResultData plusButton = templateSearchHelper.locatePattern(
                    TemplatesEnum.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON, SearchConfigConstants.DEFAULT_SINGLE);
            if (!plusButton.isFound()) {
                return true;
            }
            tapInside(plusButton);
            sleepTask(50);
        }

        ImageSearchResultData remaining = templateSearchHelper.locatePattern(
                TemplatesEnum.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON, SearchConfigConstants.DEFAULT_SINGLE);
        logDebug(routineLogNewSurvivorsLine(section + " assignment pass reached "
                + MAX_ASSIGNMENT_ATTEMPTS + " taps; remaining control found=" + remaining.isFound()));
        return !remaining.isFound();
    }

private void scheduleUnknownRetry(String reason, String type) {
        markWorkIncomplete("New Survivors: " + reason);
        LocalDateTime retryAt = LocalDateTime.now().plusMinutes(5);
        String snapshot = TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER, "newsurvivors", type);
        logWarning(routineLogNewSurvivorsLine(reason + "; retrying at "
                + retryAt.format(DATETIME_FORMATTER) + "; " + snapshot + "."));
        reschedule(retryAt);
    }

@Override
    protected LaunchPoint getRequiredStartLocation() {
        // An interrupted Welcome/Status popup must be resumed before Home
        // navigation; execute() verifies Home for fresh notification checks.
        return LaunchPoint.ANY;
    }

private String routineLogNewSurvivorsLine(String note) {
        return "NewSurvivorsRoutine | " + note;
    }
}
