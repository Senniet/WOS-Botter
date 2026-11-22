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
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.engine.error.TaskOutcomeUnknownException;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.engine.helper.TemplateSearchHelper;
import dev.frostguard.engine.helper.RecognitionConsensus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    // With owned-key recruitment enabled, check reasonably often for newly
    // earned keys, but not every 5 minutes through a 48-hour Epic cooldown.
    private static final int OWNED_KEY_RECHECK_MINUTES = 10;
    private static final int MAX_KEY_CLAIMS_PER_CATEGORY = 5;

    /** Maximum OCR retry attempts for time extraction */
    private static final int MAX_OCR_RETRIES = 3;

    /** Delay between OCR retry attempts (milliseconds) */
    private static final long OCR_RETRY_DELAY_MS = 200L;
    private static final Pattern MODERN_COOLDOWN = Pattern.compile(
            "(?<![0-9])([0-9]{1,2}):([0-9]{2}):([0-9]{2})(?![0-9])");
    private static final Pattern MODERN_DAYS = Pattern.compile(
            "(?<![0-9])([0-3])d([0-9]{1,2}):([0-9]{2}):([0-9]{2})(?![0-9])",
            Pattern.CASE_INSENSITIVE);
    // Current 19-Nov OCR with numeric-only whitelist sometimes erases 'd':
    // screenshot 1d23:26:18 was read ':123:26:18'. The leading colon and
    // single day + exactly two hour digits make this distinct from HH:MM:SS.
    private static final Pattern MODERN_DAYS_OCR_FUSED = Pattern.compile(
            "(?<![0-9]):([0-3])([0-9]{2}):([0-9]{2}):([0-9]{2})(?![0-9])");

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
    // An unreadable owned-key counter is NOT an unverified purchase.
    // Track it separately so the routine can safely exit City and defer,
    // rather than throwing before cleanup and creating five-minute loops.
    private boolean ownedKeyEvidenceMissing;
    private boolean recruitmentClaimTransitionUnknown;

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
        ownedKeyEvidenceMissing = false;
        recruitmentClaimTransitionUnknown = false;
        if (currentHeroRecruitmentVisible()) {
            processCurrentFreeRecruitment();
            return;
        }
        // If Java restarted or a previous attempt timed out on the already
        // awarded hero card / Rewards overlay, resume the verified dismissal
        // before trying navigation. There is no Recruit click in this path.
        // The two-frame exit checks are the same ones used after a fresh Free.
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU
                && (HeroRecruitmentRevealFrame.alreadyOwned(
                        emuManager.captureScreen(EMULATOR_NUMBER))
                    || locateFreeRewardExit().isFound())) {
            logInfo("Hero Recruitment found unfinished awarded reward overlay; "
                    + "resuming safe exit without recruiting again");
            if (!waitForFreeRewardsAndReturn()) {
                reschedule(LocalDateTime.now().plusMinutes(OCR_FAILURE_RETRY_MINUTES));
                throw new TaskOutcomeUnknownException(
                        "Previous free reward exit unverified; no extra claim attempted");
            }
            confirmedDailyMissionProgress = true;
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
        // Hero Recruitment remains open throughout Advanced, Epic and Points
        // processing. On the remote 720x1280 Serve-EMU, read ONE screenshot
        // for both the modern and legacy title variants instead of issuing
        // two expensive ADB captures for every stage/status observation.
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            RawImageData frame=emuManager.captureScreen(EMULATOR_NUMBER);
            if (frame==null || !frame.isValid() || frame.getWidth()!=720 || frame.getHeight()!=1280)
                return false;
            if (OpenCvPatternLocator.locatePattern(frame,
                    TemplatesEnum.SERVE_EMU_HERO_MODERN_RECRUIT_TITLE.getTemplate(),
                    new PointData(390,70),new PointData(715,330),90).isFound()) return true;
            return OpenCvPatternLocator.locatePattern(frame,
                    TemplatesEnum.SERVE_EMU_HERO_RECRUIT_TITLE.getTemplate(),
                    new PointData(390,130),new PointData(710,315),90).isFound();
        }
        // The 2026 Hero page moved the heading higher. The old title template
        // fails on the 23:58 frame, so confirm either known title in its own
        // header ROI before deciding whether to navigate or click.
        if (templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_HERO_MODERN_RECRUIT_TITLE,
                TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(1).withThreshold(90)
                        .withArea(AreaData.of(390, 70, 715, 330)).build()).isFound()) return true;
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
        // Each category has a separate Free, owned-key Recruit Once and
        // cooldown state. A failed stage can only block later actions when
        // the recruitment screen itself is no longer confirmed.
        boolean complete = true;
        if (advancedFree || advancedKeys) {
            boolean advancedOk = recruitModernCategory("Advanced",
                    AreaData.of(40, 801, 350, 904),
                    TemplatesEnum.SERVE_EMU_HERO_ADVANCED_KEY_ONCE,
                    AreaData.of(45, 792, 350, 903),
                    new PointData(238, 24), new PointData(279, 64),
                    advancedFree, advancedKeys);
            complete &= advancedOk;
            if (!advancedOk && recruitmentClaimTransitionUnknown) {
                reschedule(LocalDateTime.now().plusMinutes(OCR_FAILURE_RETRY_MINUTES));
                throw new TaskOutcomeUnknownException(
                        "Advanced claim or reward transition unverified; later recruitment blocked");
            }
            if (!advancedOk && !currentHeroRecruitmentVisible()) {
                logWarning("Advanced transition unknown; later recruitment taps blocked");
                reschedule(LocalDateTime.now().plusMinutes(OCR_FAILURE_RETRY_MINUTES));
                throw new TaskOutcomeUnknownException(
                        "Advanced claim transition not safely on Hero Recruitment");
            }
        }
        if (epicFree || epicKeys) {
            boolean epicOk = recruitModernCategory("Epic",
                    AreaData.of(40, 1150, 350, 1262),
                    TemplatesEnum.SERVE_EMU_HERO_EPIC_KEY_ONCE,
                    AreaData.of(45, 1147, 350, 1270),
                    new PointData(422, 24), new PointData(464, 64),
                    epicFree, epicKeys);
            complete &= epicOk;
            if (!epicOk && recruitmentClaimTransitionUnknown) {
                reschedule(LocalDateTime.now().plusMinutes(OCR_FAILURE_RETRY_MINUTES));
                throw new TaskOutcomeUnknownException(
                        "Epic claim or reward transition unverified; later recruitment blocked");
            }
            if (!epicOk && !currentHeroRecruitmentVisible()) {
                logWarning("Epic transition unknown; Points Chest tap blocked");
                reschedule(LocalDateTime.now().plusMinutes(OCR_FAILURE_RETRY_MINUTES));
                throw new TaskOutcomeUnknownException(
                        "Epic claim transition not safely on Hero Recruitment");
            }
        }
        if (points && currentHeroRecruitmentVisible()) {
            claimReadyPointsChest();
        }
        if (!complete && ownedKeyEvidenceMissing && !recruitmentClaimTransitionUnknown) {
            // No recruitment button was tapped: this is simply unreadable
            // inventory, not an uncertain claim. A bounded retry must not
            // leave the next task stuck on the Hero Recruitment screen.
            Duration advancedWait = advancedFree ? readModernCooldown(
                    "Advanced", ADVANCED_OCR_TOP_LEFT, ADVANCED_OCR_BOTTOM_RIGHT) : null;
            Duration epicWait = epicFree ? readModernCooldown(
                    "Epic", EPIC_OCR_TOP_LEFT, EPIC_OCR_BOTTOM_RIGHT) : null;
            LocalDateTime next = nextUnknownKeyCheck(LocalDateTime.now(),
                    advancedWait, epicWait, advancedFree, epicFree);
            markWorkIncomplete("Hero Recruitment: owned-key inventory/action not verified; no keys spent");
            reschedule(next);
            logWarning("Hero Recruitment HUD unreadable; returning to City before "
                    + "next safe check at " + next.format(DATETIME_FORMATTER));
            returnFromRecruitmentToCity();
            return;
        }
        if (!complete) {
            reschedule(LocalDateTime.now().plusMinutes(OCR_FAILURE_RETRY_MINUTES));
            throw new TaskOutcomeUnknownException("Hero Recruitment claim transition uncertain; "
                    + "no second tap or later stage; retry scheduled");
        }
        // A missing green Free button with a visible Next Free timer is a
        // normal cooldown, not a recognition failure. Recheck at the actual
        // countdown instead of revisiting this screen every five minutes.
        Duration advancedWait = advancedFree ? readModernCooldown(
                "Advanced", ADVANCED_OCR_TOP_LEFT, ADVANCED_OCR_BOTTOM_RIGHT) : null;
        Duration epicWait = epicFree ? readModernCooldown(
                "Epic", EPIC_OCR_TOP_LEFT, EPIC_OCR_BOTTOM_RIGHT) : null;
        LocalDateTime next = nextModernCheck(LocalDateTime.now(), advancedWait,
                epicWait, advancedFree, epicFree, advancedKeys || epicKeys);
        reschedule(next);
        logInfo("Hero Recruitment next check scheduled for " + next.format(DATETIME_FORMATTER));
        // DelayedTask only restores Home automatically if it was requested.
        // This routine starts at ANY (so it can resume an opened Hero page),
        // and without this verified exit the next task can inherit Hero UI.
        // NavigationHelper requires two agreeing Back-arrow frames and a
        // verified Home/World anchor; it never blindly presses Android Back.
        returnFromRecruitmentToCity();
    }

    static LocalDateTime nextUnknownKeyCheck(LocalDateTime now,
            Duration advanced, Duration epic, boolean advancedEnabled, boolean epicEnabled) {
        // An unknown counter must not produce 5-minute error retries. Do not
        // delay a verified Free timer beyond when its claim becomes available.
        LocalDateTime next = now.plusMinutes(20);
        if (advancedEnabled && advanced != null && !advanced.isNegative()) {
            LocalDateTime free = now.plus(advanced).plusSeconds(12);
            if (free.isBefore(next)) next = free;
        }
        if (epicEnabled && epic != null && !epic.isNegative()) {
            LocalDateTime free = now.plus(epic).plusSeconds(12);
            if (free.isBefore(next)) next = free;
        }
        return next;
    }

    private void returnFromRecruitmentToCity() {
        // Two direct, known pages: Recruitment -> Heroes -> City.
        // Previously this used the generic ANY/Home recovery for each arrow,
        // spending 60+ seconds probing unrelated screens while the task blocked
        // all other automation. Each click is still verified on TWO frames.
        for(int step=0;step<3;step++) {
            RawImageData first=emuManager.captureScreen(EMULATOR_NUMBER);
            if(cityHudVisible(first)) {
                logInfo("Hero Recruitment returned to City using verified header navigation");
                return;
            }
            var arrow=HeroRecruitmentExitFrame.back(first);
            if(!arrow.isFound())break;
            sleepTask(130);
            var check=HeroRecruitmentExitFrame.back(
                    emuManager.captureScreen(EMULATOR_NUMBER));
            if(!RecognitionConsensus.agrees(arrow,check,12))break;
            tapInside(check);
            sleepTask(500);
            logInfo("Hero Recruitment verified direct Back arrow step " + (step+1));
        }
        if(cityHudVisible(emuManager.captureScreen(EMULATOR_NUMBER))) {
            logInfo("Hero Recruitment City confirmed after final direct Back");
            return;
        }
        // Unrecognized screens must never receive guessed taps.
        logWarning("Hero Recruitment direct City return not confirmed; "
                + "using bounded normal Home navigator");
        navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME);
    }

    private boolean heroPageVisible(RawImageData frame) {
        return frame!=null && frame.isValid()
                && OpenCvPatternLocator.locatePattern(frame,
                    TemplatesEnum.SERVE_EMU_HEROES_PAGE_TITLE.getTemplate(),
                    new PointData(65,0),new PointData(435,190),90).isFound();
    }

    private boolean cityHudVisible(RawImageData frame) {
        return frame!=null && frame.isValid()
                && OpenCvPatternLocator.locatePattern(frame,
                    TemplatesEnum.SERVE_EMU_CITY_ANCHOR.getTemplate(),
                    new PointData(590,1145),new PointData(715,1275),90).isFound();
    }

    static Duration parseModernCooldown(String raw) {
        if (raw == null) return null;
        // Free Epic recruitment may be more than 24 hours away. The game
        // displays e.g. '1d 23:26:18', and numeric-only OCR sometimes returned
        // ':123:26:18'. Parse these before plain HH:MM:SS, never truncate the
        // day or treat 123 as a 123-hour digit sequence.
        String normalized = raw.replaceAll("\\s+", "").trim();
        for(Pattern pattern:new Pattern[]{MODERN_DAYS,MODERN_DAYS_OCR_FUSED}){
            Matcher matcher=pattern.matcher(normalized);
            while(matcher.find()){
                int days=Integer.parseInt(matcher.group(1));
                int hours=Integer.parseInt(matcher.group(2));
                int minutes=Integer.parseInt(matcher.group(3));
                int seconds=Integer.parseInt(matcher.group(4));
                if(hours<24 && minutes<60 && seconds<60){
                    Duration duration=Duration.ofDays(days).plusHours(hours)
                            .plusMinutes(minutes).plusSeconds(seconds);
                    if(!duration.isZero())return duration;
                }
            }
        }
        Matcher matcher = MODERN_COOLDOWN.matcher(normalized);
        while (matcher.find()) {
            String timer = matcher.group();
            if (GameTimeUtils.isAcceptedFormat(timer)) {
                Duration duration = GameTimeUtils.parseDuration(timer);
                if (!duration.isZero() && !duration.isNegative()
                        && duration.compareTo(Duration.ofDays(1)) <= 0) return duration;
            }
        }
        return null;
    }

    static LocalDateTime nextModernCheck(LocalDateTime now, Duration advanced,
            Duration epic, boolean advancedEnabled, boolean epicEnabled,
            boolean savedKeysEnabled) {
        LocalDateTime fallback = now.plusMinutes(
                savedKeysEnabled?OWNED_KEY_RECHECK_MINUTES:OCR_FAILURE_RETRY_MINUTES);
        // Owned keys can arrive independently from free-cooldown clocks.
        // Check at a bounded 20-minute cadence instead of every five minutes,
        // but NEVER delay the next known Free timer beyond its due date.
        if (!savedKeysEnabled && ((advancedEnabled && advanced == null)
                || (epicEnabled && epic == null))) return fallback;
        LocalDateTime next = null;
        if (advancedEnabled && advanced != null) next = now.plus(advanced).plusSeconds(12);
        if (epicEnabled && epic != null) {
            LocalDateTime candidate = now.plus(epic).plusSeconds(12);
            if (next == null || candidate.isBefore(next)) next = candidate;
        }
        if (next == null) return fallback;
        if(savedKeysEnabled && fallback.isBefore(next))return fallback;
        return next.isBefore(now.plusSeconds(30)) ? now.plusSeconds(30) : next;
    }

    private Duration readModernCooldown(String category, PointData topLeft, PointData bottomRight) {
        try {
            String ocr = emuManager.readText(EMULATOR_NUMBER, topLeft, bottomRight,
                    OcrSettingsData.configurator()
                            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
                            .allowedGlyphs("0123456789:dD").build(), false);
            Duration countdown = parseModernCooldown(ocr);
            if (countdown != null) {
                logInfo(category + " free recruitment cooldown: " + countdown);
                return countdown;
            }
            logWarning(category + " recruitment countdown OCR unconfirmed: " + ocr);
        } catch (Exception error) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException("Recruitment cooldown OCR interrupted");
            }
            logWarning(category + " recruitment countdown OCR failed: "
                    + error.getClass().getSimpleName());
        }
        return null;
    }

    /** Handles one category; spends at most one already-owned key per scheduled run. */
    private boolean recruitModernCategory(String type, AreaData freeArea,
            TemplatesEnum keyButton, AreaData keyArea,
            PointData keyCountTopLeft, PointData keyCountBottomRight,
            boolean freeEnabled, boolean keysEnabled) {
        if (!currentHeroRecruitmentVisible()) return false;
        if (freeEnabled) {
            var freeConfig = TemplateSearchHelper.SearchConfig.builder()
                    .withMaxAttempts(2).withDelay(180).withThreshold(89)
                    .withArea(freeArea).build();
            boolean freeMayBeReady = true;
            if (emuManager.getSelectedEmulatorType()
                    == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
                // This is an expected "not present" condition while the Free
                // cooldown runs. One passive image probe replaces repeated
                // retries/fallbacks, but when the button might be ready the
                // existing TWO-FRAME locateAction still authorizes any tap.
                boolean visibleOnFirstFrame = templateSearchHelper.locatePatternFast(
                        TemplatesEnum.SERVE_EMU_HERO_FREE_BUTTON, freeConfig).isFound();
                if (!visibleOnFirstFrame) {
                    Duration countdown = readModernCooldown(type,
                            type.equals("Advanced") ? ADVANCED_OCR_TOP_LEFT : EPIC_OCR_TOP_LEFT,
                            type.equals("Advanced") ? ADVANCED_OCR_BOTTOM_RIGHT : EPIC_OCR_BOTTOM_RIGHT);
                    freeMayBeReady = shouldProbeFreeButtonAfterPassiveMiss(countdown);
                    if (!freeMayBeReady) {
                        logDebug(type + ": cooldown positively read as " + countdown
                                + "; green Free is correctly absent, skipping redundant template retries");
                    }
                }
            }
            var free = freeMayBeReady
                    ? templateSearchHelper.locateAction(
                            TemplatesEnum.SERVE_EMU_HERO_FREE_BUTTON, freeConfig)
                    : ImageSearchResultData.miss();
            if (free.isFound()) {
                logInfo(type + ": free button verified; claiming free recruitment once");
                tapInside(free);
                if (!waitForFreeRewardsAndReturn()) {
                    recruitmentClaimTransitionUnknown = true;
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
        for (int claim = 0; claim < MAX_KEY_CLAIMS_PER_CATEGORY; claim++) {
            int before = readRecruitmentKeyCount(keyCountTopLeft, keyCountBottomRight);
            if (before < 0) {
                ownedKeyEvidenceMissing = true;
                logWarning(type + ": owned-key counter unreadable on the first HUD frame; "
                        + "skipping repeat OCR and refusing all key taps");
                return false;
            }
            sleepTask(190);
            int confirmedBefore = readRecruitmentKeyCount(keyCountTopLeft, keyCountBottomRight);
            if (before != confirmedBefore) {
                ownedKeyEvidenceMissing = true;
                logWarning(type + ": owned-key counter unknown or unstable ("
                        + before + "/" + confirmedBefore
                        + "); no key spent; schedule a bounded retry after returning Home");
                return false;
            }
            if (before == 0) {
                logInfo(type + ": verified no owned keys remaining");
                return true;
            }
            // Only the exact x1 key artwork is authorized. Never press x10,
            // diamonds, or a generic yellow purchase control.
            var keyOnce = templateSearchHelper.locateAction(keyButton,
                    TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(2)
                            .withDelay(160).withThreshold(96).withArea(keyArea).build());
            if (!HeroRecruitmentKeyPolicy.mayRecruitWithKey(keysEnabled, before,
                    confirmedBefore, keyOnce.isFound())) {
                ownedKeyEvidenceMissing = true;
                logWarning(type + ": positive owned-key count but verified x1 button absent; "
                        + "refusing to spend; retry after leaving the Hero screen");
                return false;
            }
            logInfo(type + ": spending one already-owned key ("
                    + (claim + 1) + "/" + MAX_KEY_CLAIMS_PER_CATEGORY
                    + ", confirmed remaining=" + before + ")");
            tapInside(keyOnce);
            if (!waitForModernRewardsAndReturn()) {
                recruitmentClaimTransitionUnknown = true;
                logWarning(type + ": key recruitment Rewards/exit unverified; stopping further key taps");
                return false;
            }
            int after = readRecruitmentKeyCount(keyCountTopLeft, keyCountBottomRight);
            sleepTask(140);
            int confirmedAfter = readRecruitmentKeyCount(keyCountTopLeft, keyCountBottomRight);
            if (after != confirmedAfter
                    || !HeroRecruitmentKeyPolicy.verifiedSingleKeySpent(before, after)) {
                recruitmentClaimTransitionUnknown = true;
                logWarning(type + ": spent key did not have a verified one-key decrement: "
                        + before + " -> " + after + "/" + confirmedAfter);
                return false;
            }
            confirmedDailyMissionProgress = true;
            logInfo(type + ": key claim verified; remaining=" + after);
            if (after == 0) return true;
        }
        logInfo(type + ": per-visit safety limit reached; other tasks may execute before remaining keys");
        return true;
    }

    static boolean shouldProbeFreeButtonAfterPassiveMiss(Duration cooldown) {
        // A missing or near-expiring timer must NEVER suppress a possible
        // free claim. Only an independently readable, stable future timer
        // can justify skipping the expected-absent Free template.
        return cooldown == null || cooldown.isNegative()
                || cooldown.compareTo(Duration.ofSeconds(60)) <= 0;
    }

    /** Preserve the established green-Free reward flow independently of key rewards. */
    private boolean waitForFreeRewardsAndReturn() {
        boolean chestAdvanced = false;
        boolean duplicateRevealAdvanced = false;
        for (int attempt = 0; attempt < 20; attempt++) {
            sleepTask(250);
            // The November 21 Epic free claim produced an already-owned
            // Charlie hero card (converted to 10 shards), not the Rewards
            // panel. Wait for two confirmed live frames, then tap a safe
            // empty area once. The next screen is the normal Rewards panel,
            // whose independently verified exit is handled below.
            if (!duplicateRevealAdvanced
                    && HeroRecruitmentRevealFrame.alreadyOwned(
                            emuManager.captureScreen(EMULATOR_NUMBER))) {
                sleepTask(160);
                if (HeroRecruitmentRevealFrame.alreadyOwned(
                        emuManager.captureScreen(EMULATOR_NUMBER))) {
                    logInfo("Free recruitment: duplicate hero converted into shards; "
                            + "advancing the verified hero reveal once");
                    tapInside(new PointData(63, 885), new PointData(83, 907));
                    duplicateRevealAdvanced = true;
                    sleepTask(400);
                    continue;
                }
            }
            // The 14:05 diagnostic captured a full-screen blue chest in the
            // snowy Hero Recruitment cutscene. This is a post-Free-claim
            // animation, not the Rewards exit overlay. Advance it once only
            // after a two-frame visual match; never repeat a Recruit tap.
            if (!chestAdvanced && attempt >= 2 && confirmFreeChestCutscene()) {
                logInfo("Advanced free recruitment chest animation confirmed; "
                        + "opening the already-awarded chest once, no keys or gems used");
                tapInside(AreaData.of(335, 705, 410, 810));
                chestAdvanced = true;
                sleepTask(600);
                continue;
            }
            var exit = locateFreeRewardExit();
            if (!exit.isFound()) continue;
            sleepTask(150);
            var confirmed = locateFreeRewardExit();
            if (!RecognitionConsensus.agrees(exit, confirmed, 12)) continue;
            tapInside(confirmed);
            // Rendering may lag after the verified exit tap on gfxstream.
            // Poll without tapping again, so an uncertain reward never causes
            // a second claim or a blind navigation action.
            for (int settle = 0; settle < 5; settle++) {
                sleepTask(350);
                if (currentHeroRecruitmentVisible()) return true;
            }
            return false;
        }
        return false;
    }

    private dev.frostguard.api.domain.ImageSearchResultData locateFreeRewardExit() {
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            RawImageData frame=emuManager.captureScreen(EMULATOR_NUMBER);
            if (frame==null || !frame.isValid() || frame.getWidth()!=720 || frame.getHeight()!=1280)
                return ImageSearchResultData.miss();
            PointData start=new PointData(190,1135),end=new PointData(530,1240);
            // Newer reward screens use the distinct November "Tap anywhere
            // to exit" artwork. A positive footer match, not the orange
            // Recruit once/key purchase button, authorizes dismissal.
            for (TemplatesEnum template : new TemplatesEnum[] {
                    TemplatesEnum.SERVE_EMU_HERO_REWARD_EXIT_LIVE_2026,
                    TemplatesEnum.SERVE_EMU_HERO_REWARDS_EXIT_LIVE_NOV_2026,
                    TemplatesEnum.SERVE_EMU_HERO_REWARDS_EXIT,
                    TemplatesEnum.SERVE_EMU_HERO_REWARD_EXIT }) {
                ImageSearchResultData hit = OpenCvPatternLocator.locatePattern(frame,
                        template.getTemplate(), start, end, 89);
                if (hit.isFound()) return hit;
            }
            return ImageSearchResultData.miss();
        }
        var config = TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(1)
                .withThreshold(89).withArea(AreaData.of(190, 1135, 530, 1240)).build();
        for (TemplatesEnum template : new TemplatesEnum[] {
                TemplatesEnum.SERVE_EMU_HERO_REWARD_EXIT_LIVE_2026,
                TemplatesEnum.SERVE_EMU_HERO_REWARDS_EXIT_LIVE_NOV_2026,
                TemplatesEnum.SERVE_EMU_HERO_REWARDS_EXIT,
                TemplatesEnum.SERVE_EMU_HERO_REWARD_EXIT }) {
            var hit = templateSearchHelper.locatePattern(template, config);
            if (hit.isFound()) return hit;
        }
        return ImageSearchResultData.miss();
    }

    private boolean confirmFreeChestCutscene() {
        var config = TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(1)
                .withThreshold(92).withArea(AreaData.of(250, 620, 510, 860)).build();
        var first = templateSearchHelper.locatePattern(
                TemplatesEnum.SERVE_EMU_HERO_FREE_CHEST_CUTSCENE_2026, config);
        if (!first.isFound()) return false;
        sleepTask(180);
        var second = templateSearchHelper.locatePattern(
                TemplatesEnum.SERVE_EMU_HERO_FREE_CHEST_CUTSCENE_2026, config);
        return RecognitionConsensus.agrees(first, second, 12);
    }

    /** Read only the numeric top-HUD slot; ambiguous OCR must never permit a key purchase. */
    private int readRecruitmentKeyCount(PointData topLeft, PointData bottomRight) {
        try {
            // Use the same full-frame OCR path as the redacted real-frame tests;
            // fall back to a slightly larger crop if a small on-device HUD
            // counter renders blurred or on a moving background.
            RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
            if (frame == null || !frame.isValid()) return -1;
            // A strict white-ring zero on the native key slot is more reliable
            // than Tesseract, which returns "" for both zero counters on the
            // user's 2026-11-19 frame. No positive count is inferred here.
            if (HeroRecruitmentZeroHud.isZero(frame, topLeft, bottomRight)) return 0;
            OcrSettingsData numbers = OcrSettingsData.configurator()
                    .textLayout(OcrSettingsData.TextLayout.SINGLE_WORD)
                    .allowedGlyphs("0123456789").build();
            int count = HeroRecruitmentKeyPolicy.countFromOcr(
                    dev.frostguard.vision.ocr.OcrEngine.recognizeText(
                            frame, topLeft, bottomRight, numbers));
            if (count >= 0) return count;
            PointData expandedTopLeft = new PointData(
                    Math.max(0, topLeft.getX() - 8), Math.max(0, topLeft.getY() - 6));
            PointData expandedBottomRight = new PointData(
                    Math.min(frame.getWidth(), bottomRight.getX() + 8),
                    Math.min(frame.getHeight(), bottomRight.getY() + 6));
            return HeroRecruitmentKeyPolicy.countFromOcr(
                    dev.frostguard.vision.ocr.OcrEngine.recognizeText(
                            frame, expandedTopLeft, expandedBottomRight,
                            OcrSettingsData.configurator()
                                    .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
                                    .allowedGlyphs("0123456789").build()));
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
            var exit = locateModernRewardExit();
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
        logInfo("Opening Hero Recruitment using short verified Heroes route");
        RawImageData before=emuManager.captureScreen(EMULATOR_NUMBER);
        if (currentHeroRecruitmentVisible()) return;
        if (!heroPageVisible(before)) {
            // Bottom Heroes tab is available directly on the City HUD.
            // Do not tap an unknown page just because a fixed position exists.
            if (!cityHudVisible(before)) {
                navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME);
                before=emuManager.captureScreen(EMULATOR_NUMBER);
                if (!cityHudVisible(before)) return;
            }
            tapInside(NAV_TAP_1_MIN, NAV_TAP_1_MAX);
            for(int i=0;i<5;i++) {
                sleepTask(180);
                if (heroPageVisible(emuManager.captureScreen(EMULATOR_NUMBER))) break;
            }
        }
        if (currentHeroRecruitmentVisible()) return;
        if (!heroPageVisible(emuManager.captureScreen(EMULATOR_NUMBER))) {
            logWarning("Heroes page not confirmed; refusing second navigation tap");
            return;
        }
        tapInside(NAV_TAP_2_MIN, NAV_TAP_2_MAX);
        for(int i=0;i<6;i++) {
            sleepTask(180);
            if (currentHeroRecruitmentVisible()) return;
        }
        logWarning("Hero Recruitment title did not appear after Heroes entry");
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
    private ImageSearchResultData locateModernRewardExit(){
        var config=TemplateSearchHelper.SearchConfig.builder()
                .withMaxAttempts(1).withDelay(0).withThreshold(89)
                .withArea(AreaData.of(190,1125,525,1240)).build();
        // New live footer plus old 2026 fallback: each is a unique textual
        // exit anchor, never the orange key-consuming Recruit once button.
        return templateSearchHelper.locateAction(
                TemplatesEnum.SERVE_EMU_HERO_REWARDS_EXIT_LIVE_NOV_2026,
                config,TemplatesEnum.SERVE_EMU_HERO_REWARDS_EXIT);
    }

    private boolean dismissVerifiedRecruitmentRewards() {
        ImageSearchResultData exitHint = ImageSearchResultData.miss();
        for (int attempt = 0; attempt < 4; attempt++) {
            exitHint = locateModernRewardExit();
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
        if (locateModernRewardExit().isFound()) {
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
