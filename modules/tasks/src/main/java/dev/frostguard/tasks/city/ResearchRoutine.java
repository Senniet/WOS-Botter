package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.ResearchCategoryEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.PriorityItemData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.api.domain.ResearchBadgeData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.engine.config.PriorityConfigResolver;
import dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig;
import dev.frostguard.engine.nav.SidebarDestination;
import dev.frostguard.engine.nav.SidebarFrameClassifier;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.emulator.EmulatorType;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import dev.frostguard.tasks.city.ResearchDialogClassifier.ResearchDialogState;
import dev.frostguard.tasks.city.ResearchNodeSelectionPolicy.ResearchNode;
import dev.frostguard.tasks.city.ResearchNodeSelectionPolicy.ResearchRow;
import dev.frostguard.vision.convert.GameTimeUtils;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.vision.ocr.ResearchBadgeReader;
import dev.frostguard.vision.ocr.OcrEngine;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import dev.frostguard.vision.ocr.OcrException;

public class ResearchRoutine extends DelayedTask {

private static final int HAND_CLICK_OFFSET_X_VALUE = -73;

private static final int HAND_CLICK_OFFSET_Y_VALUE = 88;

private static final int RESEARCH_SCROLL_SETTLE_MILLIS = 450;

private static final int RESEARCH_DRAG_DURATION_MILLIS = 750;

private static final int RESEARCH_TOP_RESET_SWIPE_COUNT = 4;

private static final int RESEARCH_TOP_RESET_GAP_MILLIS = 450;

private static final PointData RESEARCH_TOP_RESET_FROM = new PointData(489, 300);

private static final PointData RESEARCH_TOP_RESET_TO = new PointData(489, 1050);

private static final int RESEARCH_TOP_RESET_DURATION_MILLIS = 100;

private static final PointData RESEARCH_SCROLL_DOWN_FROM = new PointData(489, 980);

private static final PointData RESEARCH_SCROLL_DOWN_TO = new PointData(489, 380);

private static final int RESEARCH_ENTRY_ATTEMPTS = 2;

private static final int RESEARCH_ENTRY_RETRY_MINUTES = 5;

private static final int RESEARCH_SAFE_TAP_Y = 180;

private static final PointData RESEARCH_TITLE_TOP_LEFT = new PointData(80, 0);

private static final PointData RESEARCH_TITLE_BOTTOM_RIGHT = new PointData(360, 80);

private static final PointData RESEARCH_TREE_TOP_LEFT = new PointData(0, 150);

private static final PointData RESEARCH_TREE_BOTTOM_RIGHT = new PointData(720, 1180);

private static final PointData RESEARCH_REQUIREMENTS_TOP_LEFT = new PointData(70, 810);

private static final PointData RESEARCH_REQUIREMENTS_BOTTOM_RIGHT = new PointData(500, 1035);

private static final PointData RESEARCH_GO_TOP_LEFT = new PointData(480, 800);

private static final PointData RESEARCH_GO_BOTTOM_RIGHT = new PointData(670, 1040);

private static final PointData RESEARCH_ACTION_TOP_LEFT = new PointData(500, 1135);

private static final PointData RESEARCH_ACTION_BOTTOM_RIGHT = new PointData(710, 1250);

private static final SearchConfig RESEARCH_ACTION_RESILIENT = SearchConfig.builder()
        .withMaxAttempts(5)
        .withDelay(300)
        .withThreshold(90)
        .withCoordinates(RESEARCH_ACTION_TOP_LEFT, RESEARCH_ACTION_BOTTOM_RIGHT)
        .build();

private static final SearchConfig RESEARCH_ACTION_RECHECK = SearchConfig.builder()
        .withMaxAttempts(2)
        .withDelay(300)
        .withThreshold(90)
        .withCoordinates(RESEARCH_ACTION_TOP_LEFT, RESEARCH_ACTION_BOTTOM_RIGHT)
        .build();

private static final SearchConfig REPLENISH_BUTTON_RECHECK = SearchConfig.builder()
        .withMaxAttempts(2)
        .withDelay(300)
        .withThreshold(90)
        .withCoordinates(new PointData(180, 1070), new PointData(535, 1195))
        .build();

private static final OcrSettingsData RESEARCH_TITLE_OCR = OcrSettingsData.assembler()
        .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)

        .build();

private static final PointData REPLENISH_CONFIRM_POINT = new PointData(511, 1056);

private static final int COMPLETED_RESEARCH_RETRY_SECONDS = 10;

private static final int RESEARCH_TIMER_RETRY_MINUTES = 1;

private static final int INSUFFICIENT_RESOURCES_RETRY_MINUTES = 60;

public ResearchRoutine(AccountDescriptor profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

@Override
    protected LaunchPoint getRequiredStartLocation() {
        return LaunchPoint.HOME;
    }

    @Override
    protected boolean acceptsInjections() {
        // A queued home-screen action can steal the transition after the Research Center tap.
        return false;
    }

    @Override
    protected void execute() {
        boolean preempted=false;
        try {
            executeResearchWork();
        } catch (dev.frostguard.engine.error.TaskPreemptedException
                 | java.util.concurrent.CancellationException stop) {
            preempted=true;
            throw stop;
        } finally {
            // Never consume a higher-priority task's time while it is
            // preempting us. Otherwise end the research UI transaction with
            // a page-specific, verified Back rather than broad screen scans.
            if (!preempted && !Thread.currentThread().isInterrupted()) {
                returnHomeAfterResearchIfVisible();
            }
        }
    }

    private void executeResearchWork() {

        var constructionReservation = ConstructionBlockerRegistry.reservationFor(
                profile, ConstructionBlockerRegistry.Consumer.RESEARCH);
        if (constructionReservation.isPresent()) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime retryAt = constructionReservation.get().retryAt();
            if (!retryAt.isAfter(now)) {
                retryAt = now.plusMinutes(5);
            }
            logInfo(routineLogResearchLine(
                    "Research Center remains reserved until construction start is verified. Next check at "
                            + retryAt + "; training camps remain independent."));
            this.reschedule(retryAt);
            return;
        }


        navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME);


        marchHelper.openLeftMenuCitySection(true);
        AreaData queueArea = researchQueueStatusArea(
                SidebarFrameClassifier.isCurrentLayout(
                        ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER))));

        logDebug(routineLogResearchLine("Inspecting research queue status via OCR in " + queueArea + "..."));
        try {
            String queueStatus = emuManager.readText(
                    EMULATOR_NUMBER,
                    queueArea.topLeft(),
                    queueArea.bottomRight()).trim();

            logInfo(routineLogResearchLine("Research queue OCR status: '" + queueStatus + "'"));

            if (!ResearchQueueStatusPolicy.isExplicitlyIdle(queueStatus)) {
                if (ResearchQueueStatusPolicy.isBareUpgrading(queueStatus)) {
                    // The standalone orange "Upgrading" in Tech Research is
                    // NOT a clock. The Research Center building itself may be
                    // upgrading in either of the two City construction rows.
                    // Requiring the exact construction title and a second
                    // independent frame avoids mistaking a stale queue for
                    // an active research, while never visiting premium UI.
                    if (scheduleBuildingConstructionIfVerified()) return;
                    scheduleUnknownQueueRetry(
                            "Tech Research shows Upgrading without a timer; Research Center construction not confirmed",
                            20);
                    return;
                }
                logInfo(routineLogResearchLine("Research queue is busy. Attempting to read remaining time..."));
                var busyTimeRead = durationHelper.attemptRecognitionDetailed(
                        queueArea.topLeft(),
                        queueArea.bottomRight(),
                        5,
                        300,
                        null,
                        GameTimeUtils::isAcceptedFormat,
                        GameTimeUtils::parseDuration);
                Duration busyTime = busyTimeRead.value();

                if (busyTimeRead.accepted() && busyTime != null) {
                    Duration recheckDelay = ResearchTimerPolicy.recheckDelay(busyTime);
                    LocalDateTime rescheduleTime = LocalDateTime.now().plus(recheckDelay);
                    logInfo(routineLogResearchLine("Research busy for "
                            + formatDuration(busyTime)
                            + ". Planning next run at half time in "
                            + formatDuration(recheckDelay) + "."));
                    this.reschedule(rescheduleTime);
                } else {
                    scheduleUnknownQueueRetry("remaining time OCR status=" + busyTimeRead.status()
                            + " attempts=" + busyTimeRead.attempts()
                            + (busyTimeRead.failure() == null ? "" : " failure=" + busyTimeRead.failure()));
                }
                return;
            }
        } catch (IOException | OcrException | RuntimeException e) {
            CityUpgradeFlow.rethrowControlSignal(e);
            scheduleUnknownQueueRetry("queue status OCR failed: " + e.getClass().getSimpleName());
            return;
        }

        logInfo(routineLogResearchLine("Research queue is Idle. Proceeding..."));


        if (!openResearchTree()) {
            logWarning(routineLogResearchLine("Research tree did not open after "
                    + RESEARCH_ENTRY_ATTEMPTS + " attempts. Planning next run in "
                    + RESEARCH_ENTRY_RETRY_MINUTES + " minutes."));
            markWorkIncomplete("Research: tree did not open after verified navigation attempts");
            this.reschedule(LocalDateTime.now().plusMinutes(RESEARCH_ENTRY_RETRY_MINUTES));
            return;
        }


        ImageSearchResultData researchTextResult = findResearchInPriorityCategories();
        if (researchTextResult == null) {
            logWarning(routineLogResearchLine("No enabled category had an available research. Planning next run in 1 hour."));
            this.reschedule(LocalDateTime.now().plusHours(1));
            // A maxed/locked research tree must not strand the next task on
            // Tech Research. Only the central verified-arrow navigation may
            // leave it; never use a blind Android Back or tap a locked item.
            // The finally block performs a single verified exit/return Home.
            // Do not repeat a full navigation scan here as well.
            return;
        }

        logInfo(routineLogResearchLine("Research text template detected."));


                tapInside(researchTextResult);
                sleepTask(500);


                if (!replenishResourcesAndRetryStart(researchTextResult.getPoint())) {
                    return;
                }


                if (!ensureResearchIsRunningAfterHelp()) {
                    return;
                }


                logInfo(routineLogResearchLine("Reading research time via OCR..."));
                var researchTimeRead = durationHelper.attemptRecognitionDetailed(
                        new PointData(226, 1194),
                        new PointData(422, 1234),
                        5,
                        300,
                        null,
                        GameTimeUtils::isAcceptedFormat,
                        GameTimeUtils::parseDuration);
                Duration researchTime = researchTimeRead.value();

                if (researchTimeRead.accepted() && researchTime != null) {
                    Duration recheckDelay = ResearchTimerPolicy.recheckDelay(researchTime);
                    LocalDateTime rescheduleTime = LocalDateTime.now().plus(recheckDelay);

                    logInfo(routineLogResearchLine("Research time is "
                            + formatDuration(researchTime)
                            + ". Planning next run at half time in "
                            + formatDuration(recheckDelay) + "."));
                    logInfo(routineLogResearchLine("Research task completed. Planning next run for: " + rescheduleTime));
                    this.reschedule(rescheduleTime);
                    return;
                } else {
                    logWarning(routineLogResearchLine("Could not OCR running research time: status="
                            + researchTimeRead.status() + " attempts=" + researchTimeRead.attempts()
                            + (researchTimeRead.failure() == null ? "" : " failure=" + researchTimeRead.failure())
                            + ". Retrying in " + RESEARCH_TIMER_RETRY_MINUTES + " minute."));
                }

        this.reschedule(LocalDateTime.now().plusMinutes(RESEARCH_TIMER_RETRY_MINUTES));
    }

    /**
     * Identify a Research Center BUILDING upgrade before any Tech Research
     * duration OCR. This is a normal scheduling wait, not a failed research
     * attempt, and may be shortened by game alliance help or speedups.
     * The confirmed construction timer is a lower-bound check: recheck at
     * half time so that an accelerated build cannot strand the next research.
     */
    private boolean scheduleBuildingConstructionIfVerified() {
        try {
            checkPreemption();
            var first = ResearchCenterConstructionProbe.inspect(
                    emuManager.captureScreen(EMULATOR_NUMBER));
            if (first == null) return false;
            sleepTask(180);
            checkPreemption();
            var second = ResearchCenterConstructionProbe.inspect(
                    emuManager.captureScreen(EMULATOR_NUMBER));
            if (!ResearchCenterConstructionProbe.agrees(first, second)) {
                logInfo(routineLogResearchLine(
                        "Research Center construction state changed across two frames; no inferred queue time"));
                return false;
            }
            if (second.remaining() == null) {
                logInfo(routineLogResearchLine(
                        "Research Center construction verified, but construction clock unreadable"));
                return false;
            }
            Duration untilNext=ResearchTimerPolicy.recheckDelay(second.remaining());
            LocalDateTime next=LocalDateTime.now().plus(untilNext);
            logInfo(routineLogResearchLine(
                    "Research Center BUILDING upgrading in construction queue " + second.queue()
                            + " (remaining=" + formatDuration(second.remaining())
                            + "). No Tech Research timer exists; checking after "
                            + formatDuration(untilNext) + " at " + next
                            + " rather than repeating invalid OCR"));
            reschedule(next);
            return true;
        } catch (IOException | OcrException ex) {
            logWarning(routineLogResearchLine(
                    "Research Center construction timer unreadable: " + ex.getClass().getSimpleName()));
            return false;
        }
    }

    private void scheduleUnknownQueueRetry(String reason) {
        scheduleUnknownQueueRetry(reason, 5);
    }

    private void scheduleUnknownQueueRetry(String reason, int retryMinutes) {
        LocalDateTime retryAt = LocalDateTime.now().plusMinutes(retryMinutes);
        String snapshot = TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER, "research", "queue-ocr");
        markWorkIncomplete("Research queue unknown: " + reason);
        logWarning(routineLogResearchLine("Research queue state is unknown; " + reason
                + ". Retrying at " + retryAt.format(DATETIME_FORMATTER) + "; " + snapshot + "."));
        reschedule(retryAt);
    }

static boolean mayUseResourceReplenishment(EmulatorType backend) {
        // The unattended Serve-EMU task is authorized for ordinary free
        // research only. Replenish All can consume resource items or send the
        // player to premium offers, so never operate it on this backend.
        return backend != EmulatorType.SERVE_EMU;
    }

private boolean replenishResourcesAndRetryStart(PointData researchButton) {
        ImageSearchResultData replenishAllButton = templateSearchHelper.locatePattern(
                TemplatesEnum.REPLENISH_ALL_BUTTON, REPLENISH_BUTTON_RECHECK);
        if (!isFound(replenishAllButton)) {
            return true;
        }

        if (!mayUseResourceReplenishment(emuManager.getSelectedEmulatorType())) {
            markWorkIncomplete("Research needs resources; automatic replenishment is disabled");
            String evidence = TaskDiagnosticSnapshots.capture(
                    emuManager, EMULATOR_NUMBER, "research", "resources-required");
            logWarning(routineLogResearchLine(
                    "Research blocked by resources; no item use or purchase. " + evidence));
            this.reschedule(LocalDateTime.now().plusMinutes(INSUFFICIENT_RESOURCES_RETRY_MINUTES));
            return false;
        }

        // An unaffordable research opens the shared "Obtain more" screen instead
        // of Help/Speedup. Use owned resource items, confirm, then retry Research.
        logInfo(routineLogResearchLine("Insufficient resources detected. Replenishing and retrying start."));
        tapInside(replenishAllButton);
        sleepTask(500);
        tapNear(REPLENISH_CONFIRM_POINT);
        sleepTask(1000);
        tapNear(researchButton);
        sleepTask(800);

        replenishAllButton = templateSearchHelper.locatePattern(
                TemplatesEnum.REPLENISH_ALL_BUTTON, REPLENISH_BUTTON_RECHECK);
        if (!isFound(replenishAllButton)) {
            return true;
        }

        logWarning(routineLogResearchLine("Still insufficient after replenish. Rechecking in "
                + INSUFFICIENT_RESOURCES_RETRY_MINUTES + " min."));
        pressBack();
        this.reschedule(LocalDateTime.now().plusMinutes(INSUFFICIENT_RESOURCES_RETRY_MINUTES));
        return false;
    }

private boolean ensureResearchIsRunningAfterHelp() {
        ImageSearchResultData helpButton = templateSearchHelper.locatePattern(
                TemplatesEnum.RESEARCH_HELP_BUTTON, RESEARCH_ACTION_RESILIENT);

        if (isFound(helpButton)) {
            logInfo(routineLogResearchLine("Research Help button detected. Pressing it."));
            tapInside(helpButton);
            sleepTask(300);
        } else {
            ImageSearchResultData speedupButton = templateSearchHelper.locatePattern(
                    TemplatesEnum.RESEARCH_SPEEDUP_BUTTON, RESEARCH_ACTION_RECHECK);
            if (isFound(speedupButton)) {
                logInfo(routineLogResearchLine("Research is already running; Speedup button detected."));
                return true;
            }

            logWarning(routineLogResearchLine(
                    "Neither Help nor Speedup button appeared after starting research. Retrying shortly."));
            rescheduleShortResearchRetry();
            return false;
        }

        ImageSearchResultData speedupButton = templateSearchHelper.locatePattern(
                TemplatesEnum.RESEARCH_SPEEDUP_BUTTON, RESEARCH_ACTION_RESILIENT);
        if (isFound(speedupButton)) {
            logInfo(routineLogResearchLine("Speedup button detected after Help; research is still running."));
            return true;
        }

        // A delayed or missed Help tap leaves the same button visible. Retry once before
        // treating the vanished bottom panel as an alliance-help instant completion.
        ImageSearchResultData remainingHelpButton = templateSearchHelper.locatePattern(
                TemplatesEnum.RESEARCH_HELP_BUTTON, RESEARCH_ACTION_RECHECK);
        if (isFound(remainingHelpButton)) {
            logInfo(routineLogResearchLine("Help button remained visible. Retrying the tap once."));
            tapInside(remainingHelpButton);
            sleepTask(300);

            speedupButton = templateSearchHelper.locatePattern(
                    TemplatesEnum.RESEARCH_SPEEDUP_BUTTON, RESEARCH_ACTION_RESILIENT);
            if (isFound(speedupButton)) {
                logInfo(routineLogResearchLine("Speedup button detected after Help retry; research is still running."));
                return true;
            }

            remainingHelpButton = templateSearchHelper.locatePattern(
                    TemplatesEnum.RESEARCH_HELP_BUTTON, RESEARCH_ACTION_RECHECK);
            if (isFound(remainingHelpButton)) {
                logWarning(routineLogResearchLine("Help button still visible after retry. Retrying research shortly."));
                rescheduleShortResearchRetry();
                return false;
            }
        }

        logInfo(routineLogResearchLine(
                "Neither Help nor Speedup remains; alliance help completed the research instantly."));
        rescheduleShortResearchRetry();
        return false;
    }

private boolean isFound(ImageSearchResultData result) {
        return result != null && result.isFound();
    }

private void rescheduleShortResearchRetry() {
        LocalDateTime retryAt = LocalDateTime.now().plusSeconds(COMPLETED_RESEARCH_RETRY_SECONDS);
        markWorkIncomplete("Research launch unconfirmed after Help/Speedup checks");
        logWarning(routineLogResearchLine("Research start outcome unknown. Check again at " + retryAt));
        this.reschedule(retryAt);
    }

private static String formatDuration(Duration duration) {
        return String.format("%02d:%02d:%02d",
                duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }

static AreaData researchQueueStatusArea(boolean currentLayout) {
        // Two City sidebar layouts have different queue rows. On the classic
        // layout observed live Nov 17, the older (164,811)..(303,841) OCR
        // dropped the first colon in 01:09:02. A slightly wider/taller crop
        // reads the full countdown. The shifted layout stays unchanged.
        return currentLayout ? AreaData.of(165, 842, 305, 892)
                : AreaData.of(165, 818, 326, 853);
    }

private boolean openResearchTree() {
        for (int attempt = 1; attempt <= RESEARCH_ENTRY_ATTEMPTS; attempt++) {
            if (attempt > 1) {
                pressBack();
                sleepTask(500);
                navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME);
            }

            if (!navigationHelper.navigateToSidebarDestination(SidebarDestination.RESEARCH_CENTER)) {
                logWarning(routineLogResearchLine("Research Center destination was not reached during entry attempt "
                        + attempt + "/" + RESEARCH_ENTRY_ATTEMPTS + "."));
                continue;
            }

            ImageSearchResultData researchButton = templateSearchHelper.locatePattern(
                    TemplatesEnum.BUILDING_BUTTON_RESEARCH,
                    SearchConfigConstants.DEFAULT_SINGLE);
            if (isFound(researchButton)) {
                logInfo(routineLogResearchLine("Research building button detected. Entering Research tree."));
                tapInside(researchButton);
            } else {
                PointData handTarget = findResearchEntryHandTarget();
                if (handTarget == null) {
                    logWarning(routineLogResearchLine("Neither Research building button nor entry hand was detected "
                            + "on attempt " + attempt + "/" + RESEARCH_ENTRY_ATTEMPTS + "."));
                    continue;
                }

                logInfo(routineLogResearchLine(
                        "Research entry hand detected over the button. Entering at its relative target."));
                tapNear(handTarget);
            }
            sleepTask(500);

            if (isResearchTreeVisible()) {
                logInfo(routineLogResearchLine("Research tree screen verified."));
                return true;
            }

            logWarning(routineLogResearchLine("Research tree title was not detected on attempt "
                    + attempt + "/" + RESEARCH_ENTRY_ATTEMPTS + "."));
        }
        return false;
    }

private boolean isResearchTreeVisible() {
        if (templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_RESEARCH_TITLE,
                SearchConfig.builder().withMaxAttempts(1)
                        .withThreshold(90).withArea(AreaData.of(75, 0, 380, 100)).build()).isFound())
            return true;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String title = emuManager.readText(
                        EMULATOR_NUMBER,
                        RESEARCH_TITLE_TOP_LEFT,
                        RESEARCH_TITLE_BOTTOM_RIGHT,
                        RESEARCH_TITLE_OCR).trim();
                logDebug(routineLogResearchLine("Research tree title OCR: '" + title + "'."));
                if (title.toLowerCase().contains("research")) {
                    return true;
                }
            } catch (IOException | OcrException | RuntimeException e) {
                logDebug(routineLogResearchLine("Research tree title OCR attempt "
                        + attempt + " failed: " + e.getMessage()));
            }
            if (attempt < 3) {
                sleepTask(300);
            }
        }
        return false;
    }

private void returnHomeAfterResearchIfVisible() {
        var frame=emuManager.captureScreen(EMULATOR_NUMBER);
        if (!ResearchPageFrame.titleVisible(frame)) {
            // The tree may have closed after an instant completion or error.
            // Do not silently leave the task on some other page.
            try {
                navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME,this::checkPreemption);
            } catch (dev.frostguard.engine.error.HomeNotFoundException missing) {
                markWorkIncomplete("Research: Home not verified after leaving Research page");
                logWarning(routineLogResearchLine("Research exit not verified: " + missing.getMessage()));
            }
            return;
        }
        var first=ResearchPageFrame.safeBack(frame);
        if (!first.isFound()) {
            markWorkIncomplete("Research completed but header Back not verified");
            logWarning(routineLogResearchLine("Tech Research Back missing; no blind tap"));
            return;
        }
        sleepTask(220);
        var second=ResearchPageFrame.safeBack(emuManager.captureScreen(EMULATOR_NUMBER));
        if (!dev.frostguard.engine.helper.RecognitionConsensus.agrees(first,second,15)) {
            markWorkIncomplete("Research Back moved or changed");
            logWarning(routineLogResearchLine("Tech Research changed before exit; no tap"));
            return;
        }
        tapNear(second.getPoint());
        sleepTask(580);
        if (ResearchPageFrame.titleVisible(emuManager.captureScreen(EMULATOR_NUMBER))) {
            markWorkIncomplete("Research tree still visible after one Back");
            logWarning(routineLogResearchLine("Research header still visible; refusing repeated blind Back"));
            return;
        }
        try {
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME,this::checkPreemption);
            logInfo(routineLogResearchLine("Returned Home after verified Tech Research header exit"));
        } catch(dev.frostguard.engine.error.HomeNotFoundException missing) {
            markWorkIncomplete("Research: Home not verified after exiting Tech Research");
            logWarning(routineLogResearchLine("Research ran but Home not verified: "+missing.getMessage()));
        }
    }

private PointData findResearchEntryHandTarget() {
        RawImageData screenshot = emuManager.captureScreen(EMULATOR_NUMBER);
        if (screenshot == null) {
            return null;
        }

        ImageSearchResultData hand = emuManager.locatePattern(EMULATOR_NUMBER, screenshot,
                TemplatesEnum.SKIP_TUTORIAL_HAND, 80.0);
        if (isFound(hand)) {
            PointData point = hand.getPoint();
            return new PointData(point.getX() + HAND_CLICK_OFFSET_X_VALUE,
                    point.getY() + HAND_CLICK_OFFSET_Y_VALUE);
        }

        ImageSearchResultData mirror = emuManager.locatePattern(EMULATOR_NUMBER, screenshot,
                TemplatesEnum.SKIP_TUTORIAL_HAND_MIRROR, 80.0);
        if (isFound(mirror)) {
            PointData point = mirror.getPoint();
            return new PointData(point.getX() - HAND_CLICK_OFFSET_X_VALUE,
                    point.getY() + HAND_CLICK_OFFSET_Y_VALUE);
        }
        return null;
    }

private ImageSearchResultData findResearchInPriorityCategories() {
        List<PriorityItemData> priorities = PriorityConfigResolver.activeRankings(
                profile, ConfigurationKeyEnum.RESEARCH_PRIORITIES_STRING);
        if (priorities.isEmpty()) {
            logWarning(routineLogResearchLine("No research categories enabled."));
            return null;
        }

        for (PriorityItemData priority : priorities) {
            ResearchCategoryEnum category = ResearchCategoryEnum.fromKey(priority.getIdentifier());
            if (category == null) {
                logWarning(routineLogResearchLine("Ignoring unknown research category priority: " + priority.getIdentifier()));
                continue;
            }

            logInfo(routineLogResearchLine("Trying category '" + category.label()
                    + "' (priority " + priority.getPriority() + ")."));
            tapCategoryTab(category);
            sleepTask(500);

            ImageSearchResultData candidate = findStartableResearchNode(category);
            if (!isFound(candidate)) {
                logInfo(routineLogResearchLine("No available tech node in '" + category.label()
                        + "'. Trying next category."));
                continue;
            }

            logInfo(routineLogResearchLine("'" + category.label() + "' has an available research."));
            return candidate;
        }
        return null;
    }

private void tapCategoryTab(ResearchCategoryEnum category) {
        switch (category) {
            case GROWTH -> tapInside(new PointData(58, 88), new PointData(211, 137));
            case ECONOMY -> tapInside(new PointData(274, 84), new PointData(445, 142));
            case BATTLE -> tapInside(new PointData(499, 99), new PointData(671, 139));
        }
    }

private ImageSearchResultData findStartableResearchNode(ResearchCategoryEnum category) {
        logDebug(routineLogResearchLine("Resetting research menu to the top with fast momentum swipes..."));
        for (int i = 0; i < RESEARCH_TOP_RESET_SWIPE_COUNT; i++) {
            swipe(RESEARCH_TOP_RESET_FROM, RESEARCH_TOP_RESET_TO,
                    RESEARCH_TOP_RESET_DURATION_MILLIS);
            sleepTask(RESEARCH_TOP_RESET_GAP_MILLIS);
        }

        boolean topRowRepositioned = false;
        int maximumDownSwipes = maximumDownSwipes(category);
        for (int scrollPosition = 0; scrollPosition <= maximumDownSwipes; scrollPosition++) {
            checkPreemption();

            RawImageData researchScreenshot = emuManager.captureScreen(EMULATOR_NUMBER);
            if (researchScreenshot == null) {
                logWarning(routineLogResearchLine("Could not capture screenshot for research template search."));
                continue;
            }

            List<ResearchRow> rows = ResearchNodeSelectionPolicy.rows(
                    detectResearchNodes(researchScreenshot));
            if (!rows.isEmpty()) {
                logInfo(routineLogResearchLine("Detected " + rows.size()
                        + " visible incomplete research row(s)."));
            }
            if (!rows.isEmpty() && rows.get(0).candidates().get(0).tapTarget().getY() < RESEARCH_SAFE_TAP_Y) {
                if (topRowRepositioned) {
                    logWarning(routineLogResearchLine(
                            "Top incomplete row remains hidden behind the category header; refusing an unsafe tap."));
                    return null;
                }
                logInfo(routineLogResearchLine(
                        "Top incomplete row is partially hidden; repositioning it below the category header."));
                swipe(new PointData(489, 430), new PointData(489, 650), RESEARCH_DRAG_DURATION_MILLIS);
                sleepTask(RESEARCH_SCROLL_SETTLE_MILLIS);
                topRowRepositioned = true;
                continue;
            }
            topRowRepositioned = false;

            for (ResearchRow row : rows) {
                boolean centerCapped = false;
                for (ResearchNode node : row.candidates()) {
                    PointData tap = node.tapTarget();
                    logInfo(routineLogResearchLine("Trying row candidate " + node.currentLevel() + "/"
                            + node.maximumLevel() + " at badge (" + node.badgePoint().getX() + ", "
                            + node.badgePoint().getY() + "), tap (" + tap.getX() + ", " + tap.getY() + ")."));
                    tapNear(tap);
                    sleepTask(1000);

                    ResearchDialogInspection inspection = inspectResearchDialog();
                    logInfo(routineLogResearchLine("Candidate result: " + inspection.state()
                            + ", goButtons=" + inspection.goButtonCount()
                            + ", requirements='" + inspection.requirementText() + "'."));
                    if (inspection.state() == ResearchDialogState.STARTABLE) {
                        return inspection.researchButton();
                    }

                    pressBack();
                    sleepTask(500);

                    if (inspection.state() == ResearchDialogState.CENTER_CAPPED) {
                        centerCapped = true;
                        break;
                    }
                    if (inspection.state() == ResearchDialogState.UNKNOWN) {
                        logWarning(routineLogResearchLine(
                                "Research detail state was unknown; refusing to infer availability below it."));
                        return null;
                    }
                }

                if (!centerCapped) {
                    logInfo(routineLogResearchLine(
                            "No candidate in the current row had satisfied prerequisites; not skipping the row."));
                    return null;
                }
                if (row.minimumLevel() == 0) {
                    logInfo(routineLogResearchLine(
                            "The 0/x frontier is Research-Center-capped; deeper rows cannot be available."));
                    return null;
                }
            }

            if (scrollPosition == maximumDownSwipes) {
                break;
            }

            logDebug(routineLogResearchLine("No startable research in the visible frontier, scrolling down (swipe "
                    + (scrollPosition + 1) + "/" + maximumDownSwipes + ")"));
            swipe(RESEARCH_SCROLL_DOWN_FROM, RESEARCH_SCROLL_DOWN_TO, RESEARCH_DRAG_DURATION_MILLIS);
            sleepTask(RESEARCH_SCROLL_SETTLE_MILLIS);
        }
        return null;
    }

static int maximumDownSwipes(ResearchCategoryEnum category) {
        return switch (category) {
            case GROWTH -> 9;
            case ECONOMY -> 8;
            case BATTLE -> 15;
        };
    }

private List<ResearchNode> detectResearchNodes(RawImageData screenshot) {
        List<ResearchNode> nodes = new ArrayList<>();
        try {
            List<ResearchBadgeData> badges = ResearchBadgeReader.read(
                    screenshot, RESEARCH_TREE_TOP_LEFT, RESEARCH_TREE_BOTTOM_RIGHT);
            for (ResearchBadgeData badge : badges) {
                PointData point = badge.center();
                nodes.add(new ResearchNode(badge.currentLevel(), badge.maximumLevel(), point));
                logInfo(routineLogResearchLine("Pattern detected research badge "
                        + badge.currentLevel() + "/" + badge.maximumLevel() + " at ("
                        + point.getX() + ", " + point.getY() + "), confidence="
                        + String.format("%.1f", badge.confidence()) + "."));
            }
        } catch (RuntimeException exception) {
            logWarning(routineLogResearchLine(
                    "Research badge pattern classification failed: " + exception.getMessage()));
        }
        return nodes;
    }

private ResearchDialogInspection inspectResearchDialog() {
        RawImageData screenshot = emuManager.captureScreen(EMULATOR_NUMBER);
        if (screenshot == null) {
            return new ResearchDialogInspection(ResearchDialogState.UNKNOWN, null, 0, "");
        }

        ImageSearchResultData researchButton = emuManager.locatePattern(
                EMULATOR_NUMBER, screenshot, TemplatesEnum.RESEARCH_TEXT, 80.0);
        int goButtonCount = emuManager.locateAllPatterns(
                EMULATOR_NUMBER, screenshot, TemplatesEnum.GAME_HOME_SHORTCUTS_GO,
                RESEARCH_GO_TOP_LEFT, RESEARCH_GO_BOTTOM_RIGHT, 85.0, 4).size();
        String requirements = "";
        try {
            requirements = OcrEngine.recognizeText(
                    screenshot, RESEARCH_REQUIREMENTS_TOP_LEFT, RESEARCH_REQUIREMENTS_BOTTOM_RIGHT,
                    OcrSettingsData.forTextBlock());
        } catch (OcrException | RuntimeException exception) {
            logWarning(routineLogResearchLine("Research requirement OCR failed: " + exception.getMessage()));
        }
        ResearchDialogState state = ResearchDialogClassifier.classify(
                isFound(researchButton), goButtonCount, requirements);
        return new ResearchDialogInspection(state, researchButton, goButtonCount, requirements);
    }

private record ResearchDialogInspection(ResearchDialogState state,
                                        ImageSearchResultData researchButton,
                                        int goButtonCount,
                                        String requirementText) {}

private String routineLogResearchLine(String note) {
        return "ResearchRoutine | " + note;
    }
}
