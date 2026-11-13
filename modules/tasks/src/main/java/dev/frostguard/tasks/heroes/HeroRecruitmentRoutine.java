package dev.frostguard.tasks.heroes;

import dev.frostguard.vision.convert.GameTimeUtils;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.vision.ocr.ResilientOcrExecutor;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.engine.error.TaskOutcomeUnknownException;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.engine.helper.TemplateSearchHelper;
import dev.frostguard.engine.helper.RecognitionConsensus;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Task responsible for managing hero recruitment rewards.
 * 
 * <p>
 * This task handles two types of hero recruitment:
 * <ul>
 * <li><b>Advanced Recruitment:</b> Resets daily at game reset time</li>
 * <li><b>Epic Recruitment:</b> Resets after a certain duration from last
 * claim</li>
 * </ul>
 * 
 * <p>
 * <b>Task Flow:</b>
 * <ol>
 * <li>Navigate to hero recruitment interface</li>
 * <li>Check and claim advanced recruitment if available</li>
 * <li>Extract next advanced recruitment time (capped at game reset)</li>
 * <li>Check and claim epic recruitment if available</li>
 * <li>Extract next epic recruitment time</li>
 * <li>Reschedule to earliest of the two times</li>
 * </ol>
 * 
 * <p>
 * <b>Scheduling Strategy:</b>
 * Advanced recruitment is capped at game reset (won't schedule beyond daily
 * reset),
 * while epic recruitment can schedule beyond reset based on its own timer.
 * The task runs at whichever time comes first.
 */
public class HeroRecruitmentRoutine extends DelayedTask {

    // ===============================
    // CONSTANTS
    // ===============================

    /** Retry delay when OCR or navigation fails (minutes) */
    private static final int OCR_FAILURE_RETRY_MINUTES = 5;

    /** Maximum OCR retry attempts for time extraction */
    private static final int MAX_OCR_RETRIES = 3;

    /** Delay between OCR retry attempts (milliseconds) */
    private static final long OCR_RETRY_DELAY_MS = 200L;

    // Navigation coordinates
    private static final PointData NAV_TAP_1_MIN = new PointData(160, 1190);
    private static final PointData NAV_TAP_1_MAX = new PointData(217, 1250);
    private static final PointData NAV_TAP_2_MIN = new PointData(400, 1190);
    private static final PointData NAV_TAP_2_MAX = new PointData(660, 1250);

    // Advanced recruitment coordinates
    private static final PointData ADVANCED_SEARCH_MIN = new PointData(40, 800);
    private static final PointData ADVANCED_SEARCH_MAX = new PointData(340, 1100);
    private static final PointData ADVANCED_CLAIM_MIN = new PointData(80, 827);
    private static final PointData ADVANCED_CLAIM_MAX = new PointData(315, 875);
    private static final PointData ADVANCED_OCR_TOP_LEFT = new PointData(40, 770);
    private static final PointData ADVANCED_OCR_BOTTOM_RIGHT = new PointData(350, 810);

    // Epic recruitment coordinates
    private static final PointData EPIC_SEARCH_MIN = new PointData(40, 1160);
    private static final PointData EPIC_SEARCH_MAX = new PointData(340, 1255);
    private static final PointData EPIC_CLAIM_MIN = new PointData(70, 1180);
    private static final PointData EPIC_CLAIM_MAX = new PointData(315, 1230);
    private static final PointData EPIC_OCR_TOP_LEFT = new PointData(53, 1130);
    private static final PointData EPIC_OCR_BOTTOM_RIGHT = new PointData(330, 1160);

    // ===============================
    // FIELDS
    // ===============================

    /** Helper for flexible OCR-based time recognition */
    private final ResilientOcrExecutor<Duration> timeHelper;

    private boolean confirmedDailyMissionProgress;
    private boolean unknownClaimOutcome;

    // ===============================
    // CONSTRUCTOR
    // ===============================

    /**
     * Constructs a new HeroRecruitmentRoutine.
     * 
     * @param profile     The game profile this task operates on
     * @param tpDailyTask The task type enum from the daily task registry
     */
    public HeroRecruitmentRoutine(AccountDescriptor profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
        this.timeHelper = new ResilientOcrExecutor<>(provider);
    }

    // ===============================
    // MAIN EXECUTION
    // ===============================

    /**
     * Main execution method for the hero recruitment task.
     * 
     * <p>
     * <b>Execution Flow:</b>
     * <ol>
     * <li>Navigate to hero recruitment interface</li>
     * <li>Process advanced recruitment (claim + extract time)</li>
     * <li>Process epic recruitment (claim + extract time)</li>
     * <li>Calculate next execution time (earliest of both)</li>
     * <li>Reschedule task</li>
     * </ol>
     * 
     * <p>
     * OCR failures trigger retry in {@value #OCR_FAILURE_RETRY_MINUTES} minutes.
     * Navigation is handled automatically by DelayedTask framework after execution.
     */
    @Override
    protected void execute() {

        confirmedDailyMissionProgress = false;
        unknownClaimOutcome = false;
        if (currentHeroRecruitmentVisible()) {
            processCurrentFreeRecruitment();
            return;
        }
        navigateToHeroRecruitment();
        if (currentHeroRecruitmentVisible()) {
            processCurrentFreeRecruitment();
            return;
        }
        // Legacy interface can still be used after its existing navigation.
        // Do not run blind recruitment taps if the caller did not verify a
        // recognized classic or modern reward screen.
        if (!templateSearchHelper.locatePattern(TemplatesEnum.HERO_RECRUIT_CLAIM,
                TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(1)
                        .withThreshold(95).build()).isFound()) {
            logWarning("Hero Recruitment screen unrecognized; not pressing Free or paid keys");
            reschedule(LocalDateTime.now().plusMinutes(OCR_FAILURE_RETRY_MINUTES));
            return;
        }
        // The same Advanced/Epic free toggles also apply on classic emulators.
        // Saved-key recruitment is intentionally limited to the visually
        // verified modern screen; never use legacy fixed coordinates for keys.
        LocalDateTime advancedTime = configEnabled(
                ConfigurationKeyEnum.HERO_RECRUIT_ADVANCED_FREE_BOOL, true)
                ? processAdvancedRecruitment() : null;
        LocalDateTime epicTime = configEnabled(
                ConfigurationKeyEnum.HERO_RECRUIT_EPIC_FREE_BOOL, true)
                ? processEpicRecruitment() : null;

        LocalDateTime nextExecution = calculateNextExecution(advancedTime, epicTime);

        logInfo("Next hero recruitment check scheduled for: " + nextExecution.format(DATETIME_FORMATTER));
        reschedule(nextExecution);
        if (shouldFailOnUnknownOutcome(unknownClaimOutcome, confirmedDailyMissionProgress)) {
            throw new TaskOutcomeUnknownException(
                    "Hero recruitment claim could not be confirmed; bounded retry scheduled without recording completion.");
        }
    }

    private boolean currentHeroRecruitmentVisible() {
        return templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_HERO_RECRUIT_TITLE,
                TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(1).withThreshold(90)
                        .withArea(AreaData.of(390, 130, 710, 315)).build()).isFound();
    }

    /** A stable, per-profile stage plan: Advanced first, Epic second, Points Chest last. */
    static boolean runModernStages(boolean advanced, boolean epic, boolean points,
                                   RecruitmentStage advancedStage, RecruitmentStage epicStage,
                                   RecruitmentStage pointsStage) {
        return (!advanced || advancedStage.run())
                && (!epic || epicStage.run())
                && (!points || pointsStage.run());
    }

    @FunctionalInterface
    interface RecruitmentStage {
        boolean run();
    }

    private boolean configEnabled(ConfigurationKeyEnum key, boolean defaultValue) {
        Boolean configured = profile.getConfig(key, Boolean.class);
        return configured == null ? defaultValue : configured;
    }

    private void processCurrentFreeRecruitment() {
        if (!currentHeroRecruitmentVisible()) {
            logWarning("Hero Recruitment page vanished; refusing recruitment actions");
            reschedule(LocalDateTime.now().plusMinutes(OCR_FAILURE_RETRY_MINUTES));
            return;
        }
        boolean advancedFree = configEnabled(ConfigurationKeyEnum.HERO_RECRUIT_ADVANCED_FREE_BOOL, true);
        boolean epicFree = configEnabled(ConfigurationKeyEnum.HERO_RECRUIT_EPIC_FREE_BOOL, true);
        boolean advancedKeys = configEnabled(ConfigurationKeyEnum.HERO_RECRUIT_ADVANCED_KEYS_BOOL, false);
        boolean epicKeys = configEnabled(ConfigurationKeyEnum.HERO_RECRUIT_EPIC_KEYS_BOOL, false);
        boolean points = configEnabled(ConfigurationKeyEnum.HERO_RECRUIT_POINTS_CHEST_BOOL, true);
        boolean complete = runModernStages(advancedFree || advancedKeys, epicFree || epicKeys, points,
                () -> recruitModernCategory("Advanced", AreaData.of(40, 801, 350, 904),
                        TemplatesEnum.SERVE_EMU_HERO_ADVANCED_KEY_ONCE,
                        AreaData.of(45, 792, 350, 903),
                        new PointData(224, 89), new PointData(267, 127), advancedFree, advancedKeys),
                () -> recruitModernCategory("Epic", AreaData.of(40, 1150, 350, 1262),
                        TemplatesEnum.SERVE_EMU_HERO_EPIC_KEY_ONCE,
                        AreaData.of(45, 1147, 350, 1270),
                        new PointData(418, 89), new PointData(461, 127), epicFree, epicKeys),
                () -> {
                    claimReadyPointsChest();
                    return true;
                });
        reschedule(LocalDateTime.now().plusMinutes(OCR_FAILURE_RETRY_MINUTES));
        if (!complete) {
            throw new TaskOutcomeUnknownException("Hero Recruitment outcome uncertain; "
                    + "no second tap or later stage; retry scheduled");
        }
    }

    /** Handles one category; spends at most one already-owned key per scheduled run. */
    private boolean recruitModernCategory(String type, AreaData freeArea,
            TemplatesEnum keyButton, AreaData keyArea,
            PointData keyCountTopLeft, PointData keyCountBottomRight,
            boolean freeEnabled, boolean keysEnabled) {
        if (!currentHeroRecruitmentVisible()) return false;
        if (freeEnabled) {
            var free = templateSearchHelper.locateAction(TemplatesEnum.SERVE_EMU_HERO_FREE_BUTTON,
                    TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(2)
                            .withDelay(180).withThreshold(89).withArea(freeArea).build());
            if (free.isFound()) {
                logInfo(type + ": free button verified; claiming free recruitment once");
                tapInside(free);
                if (!waitForFreeRewardsAndReturn()) {
                    logWarning(type + ": free claim was not confirmed; no duplicate action");
                    return false;
                }
                confirmedDailyMissionProgress = true;
            } else {
                logInfo(type + ": free recruitment unavailable (timer still running)");
            }
        }
        if (!keysEnabled) {
            logInfo(type + ": saved-key recruitment disabled in profile settings");
            return true;
        }
        if (!currentHeroRecruitmentVisible()) return false;
        int before = readRecruitmentKeyCount(keyCountTopLeft, keyCountBottomRight);
        sleepTask(190);
        int confirmedBefore = readRecruitmentKeyCount(keyCountTopLeft, keyCountBottomRight);
        if (before < 1 || before != confirmedBefore) {
            logInfo(type + ": no two-frame confirmation of a positive owned-key count ("
                    + before + "/" + confirmedBefore + "); no key spent");
            return true;
        }
        // Only the exact x1 key artwork is authorized, never the right-side
        // multi-recruit button, red x10 cost, gems or a generic yellow button.
        var keyOnce = templateSearchHelper.locateAction(keyButton,
                TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(2)
                        .withDelay(160).withThreshold(96).withArea(keyArea).build());
        if (!HeroRecruitmentKeyPolicy.mayRecruitWithKey(keysEnabled, before,
                confirmedBefore, keyOnce.isFound())) {
            logWarning(type + ": verified x1 key button absent; refusing to spend");
            return true;
        }
        logInfo(type + ": spending one owned key (confirmed count " + before + ")");
        tapInside(keyOnce);
        if (!waitForModernRewardsAndReturn()) {
            logWarning(type + ": key recruitment Rewards/exit unverified; never repeating key tap");
            return false;
        }
        int after = readRecruitmentKeyCount(keyCountTopLeft, keyCountBottomRight);
        sleepTask(140);
        int confirmedAfter = readRecruitmentKeyCount(keyCountTopLeft, keyCountBottomRight);
        if (after != confirmedAfter || !HeroRecruitmentKeyPolicy.verifiedSingleKeySpent(before, after)) {
            logWarning(type + ": claimed key did not have verified one-key decrement: "
                    + before + " -> " + after + "/" + confirmedAfter);
            return false;
        }
        confirmedDailyMissionProgress = true;
        logInfo(type + ": one saved-key recruitment confirmed; remaining keys=" + after);
        return true;
    }

    /** Preserve the established green-Free reward flow independently of key rewards. */
    private boolean waitForFreeRewardsAndReturn() {
        for (int attempt = 0; attempt < 18; attempt++) {
            sleepTask(300);
            var exit = templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_HERO_REWARD_EXIT,
                    TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(1)
                            .withThreshold(89).withArea(AreaData.of(190, 1140, 530, 1240)).build());
            if (!exit.isFound()) continue;
            sleepTask(150);
            var confirmed = templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_HERO_REWARD_EXIT,
                    TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(1)
                            .withThreshold(89).withArea(AreaData.of(190, 1140, 530, 1240)).build());
            if (!RecognitionConsensus.agrees(exit, confirmed, 12)) continue;
            tapInside(confirmed);
            sleepTask(550);
            return currentHeroRecruitmentVisible();
        }
        return false;
    }

    /** Read only the numeric top-HUD slot; ambiguous OCR must never permit a key purchase. */
    private int readRecruitmentKeyCount(PointData topLeft, PointData bottomRight) {
        try {
            String text = emuManager.readText(EMULATOR_NUMBER, topLeft, bottomRight,
                    OcrSettingsData.configurator()
                            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
                            .allowedGlyphs("0123456789")
                            .build(), false);
            return HeroRecruitmentKeyPolicy.countFromOcr(text);
        } catch (Exception error) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException("Recruitment counter OCR interrupted");
            }
            logWarning("Recruitment key OCR unavailable: " + error.getClass().getSimpleName());
            return -1;
        }
    }

    private boolean waitForModernRewardsAndReturn() {
        // Wait for both independent overlay anchors. The exit is attempted
        // once only; never trigger repeated UI actions while frames are moving.
        boolean ready = false;
        for (int attempt = 0; attempt < 20; attempt++) {
            sleepTask(350);
            var title = templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_HERO_KEY_REWARDS_TITLE,
                    TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(1)
                            .withThreshold(92).withArea(AreaData.of(230,215,500,355)).build());
            if (!title.isFound()) continue;
            var exit = templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_HERO_REWARDS_EXIT,
                    TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(1)
                            .withThreshold(90).withArea(AreaData.of(190,1150,525,1240)).build());
            if (exit.isFound()) {ready = true;break;}
        }
        if (!ready || !dismissVerifiedRecruitmentRewards()) return false;
        sleepTask(450);
        return currentHeroRecruitmentVisible();
    }

    /** One bounded free Points Chest claim; never press the paid recruit buttons. */
    private void claimReadyPointsChest() {
        if (!currentHeroRecruitmentVisible() || !pointsChestBadgeVisible()) return;
        sleepTask(170);
        if (!currentHeroRecruitmentVisible() || !pointsChestBadgeVisible()) {
            logWarning("Recruitment Points Chest badge was not stable across frames");
            return;
        }
        // Confirmed live on WOS_Sennie121: chest icon is in the right header.
        tapInside(new PointData(645, 437), new PointData(670, 465));
        boolean rewardsVisible = false;
        for (int attempt = 0; attempt < 14; attempt++) {
            sleepTask(250);
            rewardsVisible = templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_REWARDS_BANNER,
                    TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(1)
                            .withThreshold(88).withArea(AreaData.of(220, 280, 505, 440)).build()).isFound();
            if (rewardsVisible) break;
        }
        if (!rewardsVisible) {
            logWarning("Recruitment Points Chest was tapped, but Rewards overlay was not verified");
            return;
        }
        for (int attempt = 0; attempt < 14; attempt++) {
            sleepTask(250);
            var exit = templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_HERO_POINTS_REWARD_EXIT,
                    TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(1)
                            .withThreshold(89).withArea(AreaData.of(190, 1140, 530, 1240)).build());
            if (!exit.isFound()) continue;
            sleepTask(150);
            var confirmed = templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_HERO_POINTS_REWARD_EXIT,
                    TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(1)
                            .withThreshold(89).withArea(AreaData.of(190, 1140, 530, 1240)).build());
            if (!RecognitionConsensus.agrees(exit, confirmed, 12)) continue;
            tapInside(confirmed);
            sleepTask(450);
            if (currentHeroRecruitmentVisible() && !pointsChestBadgeVisible()) {
                confirmedDailyMissionProgress = true;
                logInfo("Recruitment Points Chest claimed and red badge disappeared (no keys or gems spent)");
            } else {
                logWarning("Recruitment Points Chest result unverified; not retrying the tap");
            }
            return;
        }
        logWarning("Recruitment Points Chest Rewards shown but exit label not verified");
    }

    private boolean pointsChestBadgeVisible() {
        return HeroPointsChestDetector.isReady(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)));
    }

    // ===============================
    // NAVIGATION
    // ===============================

    /**
     * Navigates to the hero recruitment interface.
     * 
     * <p>
     * Performs two sequential taps to open the hero recruitment menu.
     * The framework will handle returning to home screen after task completion.
     */
    private void navigateToHeroRecruitment() {
        logInfo("Navigating to hero recruitment interface");

        tapInside(NAV_TAP_1_MIN, NAV_TAP_1_MAX);
        sleepTask(500); // Wait for first menu to open

        tapInside(NAV_TAP_2_MIN, NAV_TAP_2_MAX);
        sleepTask(500); // Wait for hero recruitment interface to load
    }

    // ===============================
    // RECRUITMENT PROCESSING
    // ===============================

    /**
     * Processes advanced recruitment rewards and extracts next availability time.
     * 
     * <p>
     * Advanced recruitment resets daily at game reset time, so the extracted
     * time is capped at the next game reset to avoid scheduling beyond it.
     * 
     * @return Next advanced recruitment time (capped at game reset), or game reset
     *         if extraction fails
     */
    private LocalDateTime processAdvancedRecruitment() {
        logInfo("Processing advanced recruitment");

        RecruitmentResult result = processRecruitmentType(
                "Advanced",
                ADVANCED_SEARCH_MIN,
                ADVANCED_SEARCH_MAX,
                ADVANCED_CLAIM_MIN,
                ADVANCED_CLAIM_MAX,
                ADVANCED_OCR_TOP_LEFT,
                ADVANCED_OCR_BOTTOM_RIGHT);

        return capAtGameReset(result.nextTime());
    }

    /**
     * Processes epic recruitment rewards and extracts next availability time.
     * 
     * <p>
     * Epic recruitment resets after a certain duration from last claim,
     * so it's not capped at game reset and can schedule into the next day.
     * 
     * @return Next epic recruitment time, or retry time if extraction fails
     */
    private LocalDateTime processEpicRecruitment() {
        logInfo("Processing epic recruitment");

        RecruitmentResult result = processRecruitmentType(
                "Epic",
                EPIC_SEARCH_MIN,
                EPIC_SEARCH_MAX,
                EPIC_CLAIM_MIN,
                EPIC_CLAIM_MAX,
                EPIC_OCR_TOP_LEFT,
                EPIC_OCR_BOTTOM_RIGHT);

        return result.nextTime();
    }

    /**
     * Processes a recruitment type (generic method for both advanced and epic).
     * 
     * <p>
     * <b>Process Flow:</b>
     * <ol>
     * <li>Search for claim button in specified region</li>
     * <li>If found, claim the reward</li>
     * <li>Extract next availability time via OCR</li>
     * <li>Return result with next time or fallback</li>
     * </ol>
     * 
     * @param type           Recruitment type name for logging
     * @param searchMin      Top-left corner of claim button search region
     * @param searchMax      Bottom-right corner of claim button search region
     * @param claimMin       Top-left corner of claim button tap region
     * @param claimMax       Bottom-right corner of claim button tap region
     * @param ocrTopLeft     Top-left corner of time OCR region
     * @param ocrBottomRight Bottom-right corner of time OCR region
     * @return RecruitmentResult containing next time or fallback
     */
    private RecruitmentResult processRecruitmentType(
            String type,
            PointData searchMin,
            PointData searchMax,
            PointData claimMin,
            PointData claimMax,
            PointData ocrTopLeft,
            PointData ocrBottomRight) {

        ClaimOutcome claimOutcome = checkAndClaimRecruitment(type, searchMin, searchMax, claimMin, claimMax);
        if (countsAsProgress(claimOutcome)) {
            confirmedDailyMissionProgress = true;
        }
        if (shouldUseUnknownRetry(claimOutcome)) {
            unknownClaimOutcome = true;
            logWarning(type + " recruitment claim remains available after the tap. "
                    + "The result is unknown; no second tap will be sent in this run.");
            return new RecruitmentResult(getFallbackTime(), false);
        }

        LocalDateTime nextTime = extractNextRecruitmentTime(type, ocrTopLeft, ocrBottomRight);

        if (nextTime != null) {
            return new RecruitmentResult(nextTime, true);
        } else {
            logWarning(type + " recruitment time extraction failed. Using fallback.");
            return new RecruitmentResult(getFallbackTime(), false);
        }
    }

    /**
     * Checks for and claims recruitment reward if available.
     * 
     * @param type      Recruitment type name for logging
     * @param searchMin Top-left corner of search region
     * @param searchMax Bottom-right corner of search region
     * @param claimMin  Top-left corner of claim tap region
     * @param claimMax  Bottom-right corner of claim tap region
     * @return confirmed, unavailable, or unknown claim outcome
     */
    private ClaimOutcome checkAndClaimRecruitment(
            String type, PointData searchMin, PointData searchMax,
            PointData claimMin, PointData claimMax) {
        ImageSearchResultData free = locateRecruitmentClaimButton(searchMin, searchMax);
        if (!free.isFound()) {
            logInfo(type + " recruitment reward not available yet.");
            return ClaimOutcome.NOT_AVAILABLE;
        }
        // Never tap a fixed coordinate that could become a paid Recruit x1.
        logInfo(type + " verified free recruitment at " + free.getPoint());
        tapInside(free);
        sleepTask(450);
        boolean rewardsDismissed = dismissVerifiedRecruitmentRewards();
        if (!rewardsDismissed) {
            logWarning(type + " Rewards transition unverified after one free tap; not repeating");
            return ClaimOutcome.UNKNOWN;
        }
        ImageSearchResultData remaining = locateRecruitmentClaimButton(searchMin, searchMax);
        if (!remaining.isFound()) {
            logInfo(type + " free recruitment confirmed after verified Rewards exit");
            return ClaimOutcome.CONFIRMED;
        }
        logWarning(type + " free recruitment remains available after Rewards exit");
        return ClaimOutcome.UNKNOWN;
    }

    private ImageSearchResultData locateRecruitmentClaimButton(PointData searchMin, PointData searchMax) {
        return templateSearchHelper.locateAction(TemplatesEnum.HERO_RECRUIT_CLAIM,
                TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(2).withThreshold(92).withDelay(150L)
                        .withCoordinates(searchMin, searchMax).build());
    }

    /** A successful free recruitment displays a Rewards page with a second
     * orange "Recruit once" button that consumes a key. Only dismiss the
     * verified Rewards overlay by tapping the empty area, never that button. */
    private boolean dismissVerifiedRecruitmentRewards() {
        var exitArea = TemplateSearchHelper.SearchConfig.builder()
                .withMaxAttempts(2).withDelay(120).withThreshold(90)
                .withCoordinates(new PointData(190, 1150), new PointData(525, 1240)).build();
        ImageSearchResultData exitHint = ImageSearchResultData.miss();
        for (int attempt = 0; attempt < 4; attempt++) {
            exitHint = templateSearchHelper.locateAction(
                    TemplatesEnum.SERVE_EMU_HERO_REWARDS_EXIT, exitArea);
            if (exitHint.isFound()) break;
            if (attempt < 3) sleepTask(250);
        }
        if (!exitHint.isFound()) return false;
        var title = templateSearchHelper.locatePattern(
                TemplatesEnum.SERVE_EMU_HERO_KEY_REWARDS_TITLE,
                TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(2).withDelay(120).withThreshold(92)
                        .withCoordinates(new PointData(230, 215), new PointData(500, 355)).build());
        if (!title.isFound()) title = templateSearchHelper.locatePattern(
                TemplatesEnum.SERVE_EMU_HERO_REWARDS_TITLE,
                TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(1).withThreshold(88)
                        .withCoordinates(new PointData(230, 215), new PointData(500, 355)).build());
        if (!title.isFound()) {
            logWarning("Hero recruitment shows exit text without a verified Rewards header; no blind tap");
            return false;
        }
        tapInside(new PointData(320, 912), new PointData(422, 1000));
        sleepTask(300);
        if (templateSearchHelper.locatePattern(
                TemplatesEnum.SERVE_EMU_HERO_REWARDS_EXIT, exitArea).isFound()) {
            logWarning("Hero recruitment Rewards overlay did not close after one safe tap; no second action");
            return false;
        }
        return true;
    }

    /**
     * Extracts the next recruitment time from the UI via OCR.
     * 
     * <p>
     * Uses {@link ResilientOcrExecutor} for robust OCR with validation
     * and retry logic. Attempts up to {@value #MAX_OCR_RETRIES} times.
     * 
     * @param type        Recruitment type name for logging
     * @param topLeft     Top-left corner of OCR region
     * @param bottomRight Bottom-right corner of OCR region
     * @return Next recruitment time, or null if extraction failed
     */
    private LocalDateTime extractNextRecruitmentTime(
            String type,
            PointData topLeft,
            PointData bottomRight) {

        logInfo("Extracting " + type + " recruitment next availability time");

        Duration nextTime = timeHelper.attemptRecognition(
                topLeft,
                bottomRight,
                MAX_OCR_RETRIES,
                OCR_RETRY_DELAY_MS,
                OcrSettingsData.assembler()
                        .charWhitelist("0123456789d")
                        .build(),
                GameTimeUtils::isAcceptedFormat,
                GameTimeUtils::parseDuration);

        if (nextTime != null) {
            LocalDateTime nextClaimTime = LocalDateTime.now().plus(nextTime);
            logInfo(type + " recruitment next time: " + nextClaimTime.format(DATETIME_FORMATTER));
            return nextClaimTime;
        } else {
            logWarning("Failed to extract " + type + " recruitment time after " +
                    MAX_OCR_RETRIES + " attempts");
            return null;
        }
    }

    // ===============================
    // SCHEDULING LOGIC
    // ===============================

    /**
     * Calculates the next execution time based on both recruitment times.
     * 
     * <p>
     * Returns the earliest of the two recruitment times to ensure
     * we don't miss either opportunity.
     * 
     * @param advancedTime Next advanced recruitment time
     * @param epicTime     Next epic recruitment time
     * @return Earliest time between the two
     */
    private LocalDateTime calculateNextExecution(LocalDateTime advancedTime, LocalDateTime epicTime) {
        return getEarliest(advancedTime, epicTime);
    }

    /**
     * Caps a time at the next game reset.
     * 
     * <p>
     * Used for advanced recruitment since it resets daily and shouldn't
     * be scheduled beyond the game reset time.
     * 
     * @param time Time to cap
     * @return Original time if before reset, otherwise game reset time
     */
    private LocalDateTime capAtGameReset(LocalDateTime time) {
        LocalDateTime gameReset = GameTimeUtils.dailyResetTime();
        return getEarliest(time, gameReset);
    }

    /**
     * Returns the earliest of two LocalDateTime values, handling nulls safely.
     * 
     * <p>
     * Null handling:
     * <ul>
     * <li>If both null: returns fallback time (retry in 5 minutes)</li>
     * <li>If one null: returns the non-null value</li>
     * <li>If neither null: returns the earlier time</li>
     * </ul>
     * 
     * @param dt1 First datetime
     * @param dt2 Second datetime
     * @return Earliest datetime, or fallback if both null
     */
    private LocalDateTime getEarliest(LocalDateTime dt1, LocalDateTime dt2) {
        if (dt1 == null && dt2 == null) {
            return getFallbackTime();
        }
        if (dt1 == null) {
            return dt2;
        }
        if (dt2 == null) {
            return dt1;
        }
        return dt1.isBefore(dt2) ? dt1 : dt2;
    }

    /**
     * Gets the fallback time for when OCR extraction fails.
     * 
     * @return Current time plus {@value #OCR_FAILURE_RETRY_MINUTES} minutes
     */
    private LocalDateTime getFallbackTime() {
        return LocalDateTime.now().plusMinutes(OCR_FAILURE_RETRY_MINUTES);
    }

    // ===============================
    // TASK FRAMEWORK OVERRIDES
    // ===============================

    /**
     * Indicates that this task provides progress toward daily missions.
     * 
     * <p>
     * Hero recruitment typically counts toward "Recruit heroes" daily missions.
     * 
     * @return true to trigger daily mission progress checks
     */
    @Override
    public boolean provideDailyMissionProgress() {
        return confirmedDailyMissionProgress;
    }

    /**
     * Specifies the required starting screen location for this task.
     * 
     * <p>
     * This task can start from any screen as it performs its own navigation.
     * 
     * @return ANY as the required start location
     */
    @Override
    protected LaunchPoint getRequiredStartLocation() {
        return LaunchPoint.ANY;
    }

    // ===============================
    // INNER CLASSES
    // ===============================

    /**
     * Record containing recruitment processing result.
     * 
     * @param nextTime Next availability time (or fallback if extraction failed)
     * @param success  Whether OCR extraction was successful
     */
    static boolean countsAsProgress(ClaimOutcome outcome) {
        return outcome == ClaimOutcome.CONFIRMED;
    }

    static boolean shouldUseUnknownRetry(ClaimOutcome outcome) {
        return outcome == ClaimOutcome.UNKNOWN;
    }

    static boolean shouldFailOnUnknownOutcome(boolean unknownClaimOutcome, boolean confirmedProgress) {
        return unknownClaimOutcome && !confirmedProgress;
    }

    enum ClaimOutcome {
        CONFIRMED,
        NOT_AVAILABLE,
        UNKNOWN
    }

    private record RecruitmentResult(LocalDateTime nextTime, boolean success) {
    }
}
