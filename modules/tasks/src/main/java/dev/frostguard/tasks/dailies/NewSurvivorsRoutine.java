package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.error.HomeNotFoundException;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import dev.frostguard.vision.convert.ImageConverter;

import java.time.LocalDateTime;

public class NewSurvivorsRoutine extends DelayedTask {

    private static final int MAX_ASSIGNMENT_ATTEMPTS = 20;
    private static final AreaData SURVIVOR_STATUS_TITLE_AREA = AreaData.of(10, 320, 385, 430);
    private static final AreaData SURVIVOR_STATUS_CLOSE_AREA = AreaData.of(640, 318, 715, 418);
    private static final AreaData SURVIVOR_COUNTER_ICON_AREA = AreaData.of(223, 0, 290, 55);
    private static final AreaData ALLOCATE_ALL_BUTTON_AREA = AreaData.of(185, 1030, 535, 1180);
    private static final int TEMPLATE_THRESHOLD = 88;
    private static final long STATUS_SETTLE_MILLIS = 450;

    public NewSurvivorsRoutine(AccountDescriptor profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected void execute() {
        logInfo(routineLogNewSurvivorsLine("Scanning for the New Survivors notification."));
        ImageSearchResultData notification = templateSearchHelper.locatePattern(
                TemplatesEnum.GAME_HOME_NEW_SURVIVORS, SearchConfigConstants.DEFAULT_SINGLE);
        if (!notification.isFound()) {
            // No new arrivals does not mean the city's existing survivors are
            // fully assigned. Live 5554 showed 25 working / 7 resting with an
            // enabled Allocate All but no Welcome In notification. Three
            // workers were assigned when that button was pressed.
            logInfo(routineLogNewSurvivorsLine(
                    "No Welcome In notification; checking existing survivors for free work slots."));
            if (!openVerifiedSurvivorStatus()) {
                scheduleUnknownRetry("Existing survivor allocation panel could not be verified",
                        "existing-status-not-opened");
                return;
            }
            SurvivorAdmissionCompletionPolicy.Assignment allocation = allocateAvailableSurvivors();
            boolean returnedHome = returnToVerifiedHome();
            if (SurvivorAdmissionCompletionPolicy.allocationOnly(allocation, returnedHome)
                    != SurvivorAdmissionCompletionPolicy.Decision.COMPLETE) {
                scheduleUnknownRetry("Existing survivor assignment or return Home was unconfirmed"
                        + " (assignment=" + allocation + ", home=" + returnedHome + ")",
                        "existing-allocation-or-home-unconfirmed");
                return;
            }
            logInfo(routineLogNewSurvivorsLine(
                    "No new arrivals; existing survivor allocation resolved and Home verified."));
            scheduleNormalInterval();
            return;
        }

        tapInside(notification);
        sleepTask(650);
        ImageSearchResultData welcomeIn = templateSearchHelper.locatePattern(
                TemplatesEnum.GAME_HOME_NEW_SURVIVORS_WELCOME_IN, SearchConfigConstants.DEFAULT_SINGLE);
        if (!welcomeIn.isFound()) {
            scheduleUnknownRetry("notification opened but Welcome In was not found", "welcome-control");
            return;
        }

        logInfo(routineLogNewSurvivorsLine("Welcome In located; admitting new survivors."));
        tapInside(welcomeIn);
        sleepTask(700);
        ImageSearchResultData stillWelcome = templateSearchHelper.locatePattern(
                TemplatesEnum.GAME_HOME_NEW_SURVIVORS_WELCOME_IN, SearchConfigConstants.DEFAULT_SINGLE);
        if (stillWelcome.isFound()) {
            scheduleUnknownRetry("Welcome In remained visible after admission tap", "welcome-not-confirmed");
            return;
        }
        logInfo(routineLogNewSurvivorsLine(
                "Welcome In dismissed; admission verified, now checking available work slots."));

        if (!openVerifiedSurvivorStatus()) {
            scheduleUnknownRetry("admission dismissed but Survivor Status could not be verified",
                    "status-not-opened");
            return;
        }

        SurvivorAdmissionCompletionPolicy.Assignment assignment = allocateAvailableSurvivors();
        // Close from either the Status or Details tab; the top title and X
        // are the verified invariants, not the currently selected bottom tab.
        boolean homeVerified = returnToVerifiedHome();
        if (SurvivorAdmissionCompletionPolicy.decide(true, assignment, homeVerified)
                != SurvivorAdmissionCompletionPolicy.Decision.COMPLETE) {
            scheduleUnknownRetry("Welcome In accepted but assignment or return Home was unconfirmed"
                    + " (assignment=" + assignment + ", home=" + homeVerified + ")",
                    "allocation-or-home-unconfirmed");
            return;
        }

        if (assignment == SurvivorAdmissionCompletionPolicy.Assignment.NO_AVAILABLE_SLOTS) {
            logInfo(routineLogNewSurvivorsLine(
                    "Welcome In confirmed; Allocate All is grey/disabled: no more available work slots."
                    + " Remaining resting survivors do not imply a failed admission."));
        } else {
            logInfo(routineLogNewSurvivorsLine(
                    "Welcome In and available survivor assignments confirmed; returned to Home."));
        }
        scheduleNormalInterval();
    }

    private boolean openVerifiedSurvivorStatus() {
        RawImageData initial = emuManager.captureScreen(EMULATOR_NUMBER);
        if (survivorStatusVisible(initial)) {
            return true;
        }

        // The legacy fixed (309,20) tap could hit another HUD element when a
        // different overlay or tab was active. Verify Home and the 2026 icon.
        try {
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME, this::checkPreemption);
        } catch (HomeNotFoundException missing) {
            logWarning(routineLogNewSurvivorsLine("Home not verified before opening Survivor Status: "
                    + missing.getMessage()));
            return false;
        }

        RawImageData city = emuManager.captureScreen(EMULATOR_NUMBER);
        ImageSearchResultData survivorHud = findOnFrame(city,
                TemplatesEnum.GAME_HOME_SURVIVOR_COUNTER_ICON_2026, SURVIVOR_COUNTER_ICON_AREA);
        if (!survivorHud.isFound()) {
            logWarning(routineLogNewSurvivorsLine(
                    "Verified Home lacks the survivor counter icon; refusing a blind HUD tap."));
            return false;
        }

        tapNear(survivorHud.getPoint(), 3);
        sleepTask(STATUS_SETTLE_MILLIS);
        RawImageData opened = emuManager.captureScreen(EMULATOR_NUMBER);
        if (!survivorStatusVisible(opened)) {
            logWarning(routineLogNewSurvivorsLine(
                    "Survivor counter tapped but Survivor Status title was not verified."));
            return false;
        }
        return true;
    }

    private SurvivorAdmissionCompletionPolicy.Assignment allocateAvailableSurvivors() {
        RawImageData before = emuManager.captureScreen(EMULATOR_NUMBER);
        if (!survivorStatusVisible(before)) {
            return SurvivorAdmissionCompletionPolicy.Assignment.UNCONFIRMED;
        }

        if (!SurvivorAllocationButtonState.statusTabSelected(ImageConverter.toBufferedImage(before))) {
            logWarning(routineLogNewSurvivorsLine(
                    "Survivor Status is not on the verified Status tab; not touching Details."));
            return SurvivorAdmissionCompletionPolicy.Assignment.UNCONFIRMED;
        }
        SurvivorAllocationButtonState.State first = classifyButton(before);
        if (first == SurvivorAllocationButtonState.State.DISABLED) {
            ImageSearchResultData disabled = findOnFrame(before,
                    TemplatesEnum.GAME_HOME_NEW_SURVIVORS_ALLOCATE_ALL_DISABLED_2026,
                    ALLOCATE_ALL_BUTTON_AREA);
            if (!disabled.isFound()) {
                logWarning(routineLogNewSurvivorsLine(
                        "Grey pixels found but disabled Allocate All identity is unverified."));
                return SurvivorAdmissionCompletionPolicy.Assignment.UNCONFIRMED;
            }
            // The 2026 grey button means no allocatable work slots remain.
            // It does not mean the already-confirmed Welcome In failed.
            return SurvivorAdmissionCompletionPolicy.Assignment.NO_AVAILABLE_SLOTS;
        }

        ImageSearchResultData allocateAll = findOnFrame(before,
                TemplatesEnum.GAME_HOME_NEW_SURVIVORS_ALLOCATE_ALL_2026,
                ALLOCATE_ALL_BUTTON_AREA);

        if (first == SurvivorAllocationButtonState.State.ENABLED) {
            if (!allocateAll.isFound()) {
                logWarning(routineLogNewSurvivorsLine(
                        "Blue Allocate All detected, but the button identity is not verified."));
                return SurvivorAdmissionCompletionPolicy.Assignment.UNCONFIRMED;
            }

            sleepTask(150);
            RawImageData stableFrame = emuManager.captureScreen(EMULATOR_NUMBER);
            ImageSearchResultData stable = findOnFrame(stableFrame,
                    TemplatesEnum.GAME_HOME_NEW_SURVIVORS_ALLOCATE_ALL_2026,
                    ALLOCATE_ALL_BUTTON_AREA);
            if (!survivorStatusVisible(stableFrame)
                    || classifyButton(stableFrame) != SurvivorAllocationButtonState.State.ENABLED
                    || !RecognitionConsensus.agrees(allocateAll, stable, 12)) {
                logWarning(routineLogNewSurvivorsLine("Allocate All identity/color unstable; no tap."));
                return SurvivorAdmissionCompletionPolicy.Assignment.UNCONFIRMED;
            }

            tapInside(stable);
            sleepTask(STATUS_SETTLE_MILLIS);
            RawImageData after = emuManager.captureScreen(EMULATOR_NUMBER);
            if (survivorStatusVisible(after)
                    && SurvivorAllocationButtonState.statusTabSelected(ImageConverter.toBufferedImage(after))
                    && classifyButton(after) == SurvivorAllocationButtonState.State.DISABLED
                    && findOnFrame(after, TemplatesEnum.GAME_HOME_NEW_SURVIVORS_ALLOCATE_ALL_DISABLED_2026,
                            ALLOCATE_ALL_BUTTON_AREA).isFound()) {
                logInfo(routineLogNewSurvivorsLine(
                        "Allocate All changed from blue/enabled to grey/disabled after tap."));
                return SurvivorAdmissionCompletionPolicy.Assignment.APPLIED;
            }

            logWarning(routineLogNewSurvivorsLine(
                    "Allocate All transition to disabled state was not verified after one tap."));
            return SurvivorAdmissionCompletionPolicy.Assignment.UNCONFIRMED;
        }

        // Never infer a valid modern button from text alone: the grey and blue
        // screenshots have the same white lettering and can both match a template.
        if (allocateAll.isFound()) {
            logWarning(routineLogNewSurvivorsLine(
                    "Allocate All text found, but enabled/disabled color state is unknown."));
            return SurvivorAdmissionCompletionPolicy.Assignment.UNCONFIRMED;
        }

        // Preserve the older per-building assignment path only when a real
        // legacy plus control is positively detected on the verified Status tab.
        ImageSearchResultData legacyPlus = templateSearchHelper.locatePattern(
                TemplatesEnum.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON,
                SearchConfigConstants.DEFAULT_SINGLE);
        if (!legacyPlus.isFound()) {
            logWarning(routineLogNewSurvivorsLine(
                    "No modern Allocate All state or legacy assignment control was confirmed."));
            return SurvivorAdmissionCompletionPolicy.Assignment.UNCONFIRMED;
        }
        logInfo(routineLogNewSurvivorsLine("Using verified legacy per-building assignment controls."));
        emuManager.swipeScreen(EMULATOR_NUMBER, new PointData(340, 610), new PointData(340, 900));
        sleepTask(200);
        if (!assignVisibleSlots("lower")) {
            return SurvivorAdmissionCompletionPolicy.Assignment.UNCONFIRMED;
        }
        emuManager.swipeScreen(EMULATOR_NUMBER, new PointData(340, 900), new PointData(340, 610));
        sleepTask(200);
        if (!assignVisibleSlots("upper")) {
            return SurvivorAdmissionCompletionPolicy.Assignment.UNCONFIRMED;
        }
        return SurvivorAdmissionCompletionPolicy.Assignment.APPLIED;
    }

    private SurvivorAllocationButtonState.State classifyButton(RawImageData capture) {
        return SurvivorAllocationButtonState.classify(ImageConverter.toBufferedImage(capture));
    }

    private boolean survivorStatusVisible(RawImageData frame) {
        return findOnFrame(frame,
                TemplatesEnum.GAME_HOME_NEW_SURVIVORS_STATUS_TITLE_2026,
                SURVIVOR_STATUS_TITLE_AREA).isFound();
    }

    private ImageSearchResultData findOnFrame(
            RawImageData frame, TemplatesEnum template, AreaData region) {
        return emuManager.locatePattern(EMULATOR_NUMBER, frame, template,
                region.topLeft(), region.bottomRight(), TEMPLATE_THRESHOLD);
    }

    private boolean returnToVerifiedHome() {
        RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
        if (survivorStatusVisible(frame)) {
            ImageSearchResultData close = findOnFrame(frame,
                    TemplatesEnum.GAME_HOME_NEW_SURVIVORS_STATUS_CLOSE_2026,
                    SURVIVOR_STATUS_CLOSE_AREA);
            if (!close.isFound()) {
                logWarning(routineLogNewSurvivorsLine(
                        "Survivor Status is open but its close icon was not recognized."));
                return false;
            }
            tapNear(close.getPoint(), 3);
            sleepTask(STATUS_SETTLE_MILLIS);
            RawImageData after = emuManager.captureScreen(EMULATOR_NUMBER);
            if (survivorStatusVisible(after)) {
                logWarning(routineLogNewSurvivorsLine(
                        "Survivor Status remained open after tapping its verified X."));
                return false;
            }
        }

        try {
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME, this::checkPreemption);
            logInfo(routineLogNewSurvivorsLine("Verified return to Home from Survivor Status."));
            return true;
        } catch (HomeNotFoundException missing) {
            logWarning(routineLogNewSurvivorsLine(
                    "Return to Home not verified: " + missing.getMessage()));
            return false;
        }
    }

    private boolean assignVisibleSlots(String section) {
        for (int attempt = 0; attempt < MAX_ASSIGNMENT_ATTEMPTS; attempt++) {
            ImageSearchResultData plusButton = templateSearchHelper.locatePattern(
                    TemplatesEnum.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON,
                    SearchConfigConstants.DEFAULT_SINGLE);
            if (!plusButton.isFound()) {
                return true;
            }
            sleepTask(150);
            ImageSearchResultData confirmed = templateSearchHelper.locatePattern(
                    TemplatesEnum.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON,
                    SearchConfigConstants.DEFAULT_SINGLE);
            if (!RecognitionConsensus.agrees(plusButton, confirmed, 12)) {
                logWarning(routineLogNewSurvivorsLine(section + " assignment control unstable; stopping."));
                return false;
            }
            tapInside(confirmed);
            sleepTask(350);
            ImageSearchResultData afterTap = templateSearchHelper.locatePattern(
                    TemplatesEnum.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON,
                    SearchConfigConstants.DEFAULT_SINGLE);
            if (afterTap.isFound() && RecognitionConsensus.agrees(confirmed, afterTap, 12)) {
                logWarning(routineLogNewSurvivorsLine(section
                        + " assignment control did not change after tap; refusing repeated taps."));
                return false;
            }
        }
        ImageSearchResultData remaining = templateSearchHelper.locatePattern(
                TemplatesEnum.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON,
                SearchConfigConstants.DEFAULT_SINGLE);
        logDebug(routineLogNewSurvivorsLine(section + " assignment pass reached "
                + MAX_ASSIGNMENT_ATTEMPTS + " taps; remaining control found=" + remaining.isFound()));
        return !remaining.isFound();
    }

    private void scheduleNormalInterval() {
        reschedule(LocalDateTime.now().plusMinutes(profile.getConfig(
                ConfigurationKeyEnum.CITY_ACCEPT_NEW_SURVIVORS_OFFSET_INT, Integer.class)));
    }

    private void scheduleUnknownRetry(String reason, String type) {
        // Unknown outcomes must never leave the survivor overlay for the next
        // queued task. Closing the known modal is safe; it is not a claim.
        // Preserve the failing panel before recovery changes the evidence.
        String snapshot = TaskDiagnosticSnapshots.capture(
                emuManager, EMULATOR_NUMBER, "newsurvivors", type);
        boolean recovered = returnToVerifiedHome();
        LocalDateTime retryAt = LocalDateTime.now().plusMinutes(5);
        String fullReason = reason + (recovered ? "" : "; Home still unverified");
        logWarning(routineLogNewSurvivorsLine(fullReason + "; retrying at "
                + retryAt.format(DATETIME_FORMATTER) + "; " + snapshot + "."));
        markWorkIncomplete("New Survivors: " + fullReason);
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
