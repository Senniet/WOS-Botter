package dev.frostguard.tasks.combat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.ConfigData;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.engine.nav.SidebarDestination;
import dev.frostguard.engine.service.StatisticsService;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import dev.frostguard.vision.color.GameColors;
import dev.frostguard.vision.color.PixelStats;
import dev.frostguard.vision.convert.GameTimeUtils;
import dev.frostguard.vision.ocr.OcrEngine;

/**
 * Task responsible for managing arena challenges.
 *
 * <p>The routine prefers weaker opponents and tries all free refreshes
 * before using a clearly identified stronger opponent as a last resort to
 * finish free daily challenges. Optional profile-based filters
 * protect the profile alliance and apply a server preference using the
 * Character Information stored on the selected profile.</p>
 */
public class ArenaRoutine extends DelayedTask {

    // ========== Configuration Defaults ==========
    private static final String DEFAULT_ACTIVATION_TIME = "23:55"; // UTC
    private static final int DEFAULT_EXTRA_ATTEMPTS = 0;
    private static final boolean DEFAULT_REFRESH_WITH_GEMS = false;
    private static final AlliancePolicy DEFAULT_ALLIANCE_POLICY = AlliancePolicy.AVOID_PROFILE_ALLIANCE;

    // ========== Arena Coordinates ==========
    private static final PointData ARENA_SCORE_TOP_LEFT = new PointData(548, 1064);
    private static final PointData ARENA_SCORE_BOTTOM_RIGHT = new PointData(650, 1100);

    private static final PointData CHALLENGES_LEFT_TOP_LEFT = new PointData(405, 948);
    private static final PointData CHALLENGES_LEFT_BOTTOM_RIGHT = new PointData(438, 994);
    private static final PointData EXTRA_ATTEMPTS_BUTTON = new PointData(467, 965);

    private static final int OPPONENT_BASE_Y_FIRST_RUN = 380;
    private static final int OPPONENT_BASE_Y_NORMAL = 376;
    private static final int OPPONENT_Y_SPACING = 128;
    private static final int OPPONENT_CHALLENGE_BUTTON_X = 624;
    private static final int OPPONENT_CHALLENGE_BUTTON_HALF_WIDTH = 38;
    private static final int OPPONENT_CHALLENGE_BUTTON_HALF_HEIGHT = 38;

    private static final int OPPONENT_NAME_X = 150;
    private static final int OPPONENT_NAME_WIDTH = 260;
    private static final int OPPONENT_NAME_RELATIVE_Y = -70;
    private static final int OPPONENT_NAME_HEIGHT = 32;

    private static final int OPPONENT_SERVER_X = 145;
    private static final int OPPONENT_SERVER_WIDTH = 140;
    private static final int OPPONENT_SERVER_RELATIVE_Y = 12;
    private static final int OPPONENT_SERVER_HEIGHT = 30;

    private static final int POWER_TEXT_RELATIVE_X = 180;
    private static final int POWER_TEXT_WIDTH = 130;
    private static final int POWER_TEXT_SERVER_LAYOUT_Y_OFFSET = -32;
    private static final int POWER_TEXT_SERVER_LAYOUT_HEIGHT = 30;
    private static final int POWER_TEXT_COMPACT_LAYOUT_Y_OFFSET = 0;
    private static final int POWER_TEXT_COMPACT_LAYOUT_HEIGHT = 22;
    private static final Color ARENA_POWER_GREEN_TEXT = new Color(105, 230, 50);
    private static final Color ARENA_POWER_RED_TEXT = new Color(240, 85, 65);

    private static final int OPPONENT_SCORE_STAR_X = 296;
    private static final int OPPONENT_SCORE_STAR_WIDTH = 42;
    private static final int OPPONENT_SCORE_STAR_HEIGHT = 34;
    private static final int OPPONENT_SCORE_STAR_SERVER_LAYOUT_Y_OFFSET = -34;
    private static final int OPPONENT_SCORE_STAR_COMPACT_LAYOUT_Y_OFFSET = -4;
    private static final int MIN_STAR_YELLOW_PIXELS = 20;

    private static final PointData QUICK_DEPLOY_BUTTON = new PointData(180, 1200);
    private static final PointData BATTLE_START_BUTTON = new PointData(530, 1200);
    private static final PointData BATTLE_PAUSE_BUTTON = new PointData(60, 962);
    private static final PointData BATTLE_RETREAT_BUTTON = new PointData(252, 635);

    private static final AreaData BATTLE_RESULT_AREA = new AreaData(
            new PointData(150, 350),
            new PointData(570, 520));

    private static final PointData PURCHASE_PRICE_TOP_LEFT = new PointData(328, 840);
    private static final PointData PURCHASE_PRICE_BOTTOM_RIGHT = new PointData(433, 883);
    private static final PointData PURCHASE_COUNTER_SWIPE_START = new PointData(420, 733);
    private static final PointData PURCHASE_COUNTER_SWIPE_END = new PointData(40, 733);
    private static final PointData PURCHASE_CONFIRM_BUTTON = new PointData(360, 860);
    private static final PointData PURCHASE_COUNTER_INCREMENT_TOP_LEFT = new PointData(457, 713);
    private static final PointData PURCHASE_COUNTER_INCREMENT_BOTTOM_RIGHT = new PointData(499, 752);

    // ========== Arena Constants ==========
    private static final int INITIAL_ARENA_SCORE = 1000;
    private static final int MAX_REASONABLE_CHALLENGE_ATTEMPTS = 10;
    // The current Serve-Emu layout renders "Daily challenges: N". The former
    // 33-pixel digit-only crop misread 1 as 7 and a digit as 75. Verify the
    // labelled line on two live frames before authorizing a battle.
    private static final PointData CHALLENGES_LABEL_TOP_LEFT = new PointData(170, 933);
    private static final PointData CHALLENGES_LABEL_BOTTOM_RIGHT = new PointData(455, 1005);
    private static final Pattern DAILY_CHALLENGE_LABEL = Pattern.compile(
            "(?i)\\bchallenges\\b[\\s]*[:;|.][\\s]*([0-9])\\b");

    private static final int MAX_FREE_REFRESHES = 3;
    private static final int MAX_GEM_REFRESHES = 5;
    static final SearchConfig TRANSIENT_BUTTON_SEARCH = SearchConfig.builder()
            .withMaxAttempts(3)
            .withDelay(500)
            .build();
    private static final int[] ATTEMPT_PRICES = { 100, 200, 400, 600, 800 };
    // Rewards panel after a Daily Challenges chest claim.
    private static final int REWARDS_BANNER_THRESHOLD = 85;
    private static final int REWARDS_PANEL_TIMEOUT_MS = 6_000;
    private static final int REWARDS_PANEL_POLL_MS = 400;
    private static final int REWARDS_PANEL_SETTLE_MS = 1_200;
    private static final PointData REWARDS_DISMISS_POINT = new PointData(360, 1150);

    // ========== Daily Challenges chest ==========
    // The Challenge List carries its own chest, with a count under it that runs
    // 0/5 to 5/5. Only the leading digit of a finished count is drawn green;
    // measured on the live screen it gives 98 green pixels at 5/5 and none at
    // 0/5 through 4/5, while the chest art above it pulses and cannot be matched.
    private static final PointData DAILY_CHEST_POINT = new PointData(115, 985);
    private static final AreaData DAILY_CHEST_COUNT_LEADING_DIGIT = new AreaData(
            new PointData(99, 1071),
            new PointData(115, 1093));
    private static final int MIN_DAILY_CHEST_GREEN_PIXELS = 40;
    private static final int DAILY_CHEST_GREEN_MARGIN = 30;
    // The list is still fading back in when the last battle's result closes.
    private static final int DAILY_CHEST_CHECK_ATTEMPTS = 4;
    private static final int DAILY_CHEST_CHECK_DELAY_MS = 700;
    private static final int MIN_COLORED_PIXELS_THRESHOLD = 10;
    private static final double GREEN_DOMINANCE_RATIO = 1.5;
    private static final double RED_DOMINANCE_RATIO = 1.5;
    private static final int COLOR_ANALYSIS_STEP_SIZE = 2;
    private static final int MAX_OPPONENTS = 5;
    private static final Pattern ALLIANCE_TAG_PATTERN = Pattern.compile("\\[([A-Za-z0-9]{1,4})]");
    private static final Pattern MALFORMED_ALLIANCE_TAG_PATTERN = Pattern.compile("\\[([A-Za-z0-9]{1,4})");
    private static final Pattern BRACKETLESS_ALLIANCE_TAG_PATTERN = Pattern.compile("^[\\[\\|Il1t]([A-Z0-9]{2,4})(?=[a-z\\]|Il1~\\-])");
    private static final Pattern BRACKETLESS_TRAILING_BRACKET_PATTERN = Pattern.compile("^[\\[\\|Il1t]?([A-Z0-9]{2,4})[^\\]]*]");
    private static final Pattern SERVER_PATTERN = Pattern.compile("(\\d{3,5})");
    private static final Pattern POWER_VALUE_PATTERN = Pattern.compile("([0-9]+[.,][0-9]+)([KMBkmb])");
    private static final int OCR_RETRY_DELAY_MS = 250;

    // ========== Configuration ==========
    private String activationTime;
    private int extraAttempts;
    private boolean refreshWithGems;
    private AlliancePolicy alliancePolicy;
    private ServerPolicy serverPolicy;
    private String profileAllianceTag;
    private String profileServer;

    // ========== Execution State ==========
    private int attempts;
    private boolean firstRun;
    private int freeRefreshCount;
    private int gemRefreshCount;
    private boolean extraAttemptsPurchased;
    private boolean noDailyChallengesDetected;
    private boolean unknownBattleResult;
    private final Set<Integer> attemptedOpponentSlots = new HashSet<>();

    public ArenaRoutine(AccountDescriptor profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected boolean acceptsInjections() {
        return false;
    }

    private void loadConfiguration() {
        String configuredTime = profile.getConfig(
                ConfigurationKeyEnum.ARENA_TASK_ACTIVATION_TIME_STRING, String.class);
        this.activationTime = configuredTime != null ? configuredTime : DEFAULT_ACTIVATION_TIME;

        Integer configuredAttempts = profile.getConfig(
                ConfigurationKeyEnum.ARENA_TASK_EXTRA_ATTEMPTS_INT, Integer.class);
        this.extraAttempts = configuredAttempts != null ? configuredAttempts : DEFAULT_EXTRA_ATTEMPTS;

        Boolean configuredRefresh = profile.getConfig(
                ConfigurationKeyEnum.ARENA_TASK_REFRESH_WITH_GEMS_BOOL, Boolean.class);
        this.refreshWithGems = configuredRefresh != null ? configuredRefresh : DEFAULT_REFRESH_WITH_GEMS;
        if (!dev.frostguard.tasks.security.TaskGemSpendPolicy.allowed(
                profile, TpDailyTaskEnum.ARENA)) {
            extraAttempts = 0;
            refreshWithGems = false;
            logInfo("Arena diamond-spending permission OFF; free challenges and free refreshes remain available");
        }
        // Retained profile settings from another emulator must never spend
        // diamonds on the live Serve-EMU free-challenges-only account.
        if (!arenaPaidActionsAllowed(emuManager.getSelectedEmulatorType())) {
            if (extraAttempts > 0 || refreshWithGems)
                logInfo("Serve-EMU Arena: ignoring stored gem refresh/extra attempt settings");
            extraAttempts = 0;
            refreshWithGems = false;
        }

        this.alliancePolicy = resolveAlliancePolicy();

        String configuredServerPolicy = profile.getConfig(
                ConfigurationKeyEnum.ARENA_TASK_SERVER_POLICY_STRING, String.class);
        this.serverPolicy = ServerPolicy.fromConfig(configuredServerPolicy);

        this.profileAllianceTag = normalizeAllianceTag(profile.getCharacterAllianceCode());
        this.profileServer = normalizeServer(profile.getCharacterServer());

        if (profileAllianceTag == null && alliancePolicy != AlliancePolicy.ANY) {
            logWarning("Arena alliance policy requires profile Alliance, but it is not set. Using Any alliance.");
            alliancePolicy = AlliancePolicy.ANY;
        }
        if (profileServer == null && serverPolicy != ServerPolicy.ANY) {
            logWarning("Arena server policy requires profile Server, but it is not set. Using Any server.");
            serverPolicy = ServerPolicy.ANY;
        }

        logDebug(String.format(
                "Configuration loaded - Time: %s, Extra attempts: %d, Refresh list with gems: %s, Alliance policy: %s, Profile alliance: %s, Server policy: %s, Profile server: %s",
                activationTime, extraAttempts, refreshWithGems, alliancePolicy.displayName,
                profileAllianceTag != null ? profileAllianceTag : "not set",
                serverPolicy.displayName,
                profileServer != null ? profileServer : "not set"));
    }

    static boolean arenaPaidActionsAllowed(dev.frostguard.engine.emulator.EmulatorType backend) {
        return backend != dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU;
    }

    private void resetExecutionState() {
        this.attempts = 0;
        this.firstRun = false;
        this.freeRefreshCount = 0;
        this.gemRefreshCount = 0;
        this.extraAttemptsPurchased = false;
        this.noDailyChallengesDetected = false;
        this.unknownBattleResult = false;
        this.attemptedOpponentSlots.clear();
        logDebug("Execution state reset");
    }

    private boolean hasConfig(ConfigurationKeyEnum key) {
        return profile.getEntries().stream()
                .map(ConfigData::getSettingKey)
                .anyMatch(settingKey -> key.equals(settingKey) || key.name().equalsIgnoreCase(String.valueOf(settingKey)));
    }

    private AlliancePolicy resolveAlliancePolicy() {
        boolean hasPolicyConfig = hasConfig(ConfigurationKeyEnum.ARENA_TASK_ALLIANCE_POLICY_STRING);
        boolean hasLegacyConfig = hasConfig(ConfigurationKeyEnum.ARENA_TASK_PROTECT_ALLIANCE_BOOL);
        String configuredPolicy = hasPolicyConfig
                ? profile.getConfig(ConfigurationKeyEnum.ARENA_TASK_ALLIANCE_POLICY_STRING, String.class)
                : null;

        if (hasLegacyConfig) {
            Boolean legacyProtectAlliance = profile.getConfig(
                    ConfigurationKeyEnum.ARENA_TASK_PROTECT_ALLIANCE_BOOL, Boolean.class);
            if (Boolean.FALSE.equals(legacyProtectAlliance) && AlliancePolicy.isDefaultConfig(configuredPolicy)) {
                return AlliancePolicy.ANY;
            }
            if (!hasPolicyConfig) {
                return legacyProtectAlliance == null || legacyProtectAlliance
                        ? DEFAULT_ALLIANCE_POLICY
                        : AlliancePolicy.ANY;
            }
        }

        return hasPolicyConfig
                ? AlliancePolicy.fromConfig(configuredPolicy)
                : DEFAULT_ALLIANCE_POLICY;
    }

    @Override
    protected void execute() {
        loadConfiguration();
        resetExecutionState();

        if (!isValidTimeFormat(activationTime)) {
            logWarning("Invalid activation time format: " + activationTime +
                    ". Scheduling to 5 min before reset.");
            reschedule(GameTimeUtils.dailyResetTime().minusMinutes(5));
            return;
        }

        if (!isWithinExecutionWindow()) {
            return;
        }

        if (!navigateToArena()) {
            logWarning("Failed to navigate to arena; " + captureArenaDiagnostic("navigation-failed"));
            markWorkIncomplete("Arena: page not reached or visible; no challenge attempted");
            reschedule(java.time.LocalDateTime.now().plusMinutes(10));
            return;
        }

        // Arena now has a Daily Challenges chest near the bottom-left of the
        // challenge entry. Save this panel even if the chest was already claimed,
        // so its eligible/claimed states can be distinguished before clicks.
        logInfo("Arena Daily Challenges chest layout evidence: "
                + captureArenaDiagnostic("daily-chest-entry-layout"));
        firstRun = detectFirstRun();

        if (!openChallengeList()) {
            logWarning("Failed to open challenge list; " + captureArenaDiagnostic("challenge-list-not-open"));
            markWorkIncomplete("Arena: Challenge button/list not verified, no battle performed");
            reschedule(java.time.LocalDateTime.now().plusMinutes(10));
            returnHomeAfterArena();
            return;
        }

        if (!readInitialAttempts()) {
            markWorkIncomplete("Arena: remaining attempts unknown; challenge battle not performed");
            logWarning("Failed to read initial attempts; " + captureArenaDiagnostic("attempts-unreadable"));
            // A broken attempt-counter OCR must not block a chest that is
            // already available after the five free challenges. The chest
            // checker itself requires two 5/5 green-count observations.
            claimDailyChallengesChest();
            reschedule(java.time.LocalDateTime.now().plusMinutes(10));
            returnHomeAfterArena();
            return;
        }

        processChallenges();
        if (unknownBattleResult) {
            // An unknown Arena battle must never fall through to a chest tap:
            // the UI might still be on Formation, an animated result or
            // another unexpected panel. Record incompletion and stop safely.
            markWorkIncomplete("Arena: battle state/exit could not be confirmed");
            reschedule(java.time.LocalDateTime.now().plusMinutes(10));
            returnHomeAfterArena();
            return;
        }
        // Retain the state after free challenges too: the daily chest may only
        // become claimable once the daily attempts have been exhausted.
        logInfo("Arena Daily Challenges chest after challenge attempts: "
                + captureArenaDiagnostic("daily-chest-after-challenges"));
        claimDailyChallengesChest();

        rescheduleWithActivationHour();
        returnHomeAfterArena();
    }

    /** Leave Arena cleanly, including its separate floating Challenge List. */
    private void returnHomeAfterArena() {
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            if (returnFromLiveArena())return;
            markWorkIncomplete("Arena: verified City/World return failed");
            logWarning("Arena return Home: unexpected page or back action, no blind navigation");
            return;
        }
        try {
            if (challengeListVisible()) {
                var config = SearchConfig.builder().withMaxAttempts(1).withThreshold(90)
                        .withArea(AreaData.of(625, 145, 712, 235)).build();
                var first = templateSearchHelper.locatePattern(
                        TemplatesEnum.SERVE_EMU_ARENA_LIST_CLOSE_2026, config);
                if (!first.isFound()) {
                    logWarning("Arena Challenge List close X missing; refusing blind tap");
                    return;
                }
                sleepTask(170);
                var confirmed = templateSearchHelper.locatePattern(
                        TemplatesEnum.SERVE_EMU_ARENA_LIST_CLOSE_2026, config);
                if (!dev.frostguard.engine.helper.RecognitionConsensus.agrees(first, confirmed, 12)) {
                    logWarning("Arena Challenge List close X moved; leaving page unchanged");
                    return;
                }
                tapInside(confirmed);
                sleepTask(450);
                if (challengeListVisible()) {
                    logWarning("Arena Challenge List remained open after verified close; no repeat");
                    return;
                }
            }
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME, this::checkPreemption);
            logInfo("Arena closed and Home verified after Daily Challenges pass");
        } catch (dev.frostguard.engine.error.HomeNotFoundException missing) {
            markWorkIncomplete("Arena: Home not verified after arena task");
            logWarning("Arena finished but Home recovery is not yet verified: " + missing.getMessage());
        }
    }

    /** Direct Arena→City route observed on emulator-5554, 17-Nov-2026.
     * Close the Challenge List, press the Arena header Back once, verify City.
     * No generic 20+ popup probes or Android Back on an unknown screen. */
    private boolean returnFromLiveArena() {
        RawImageData frame=emuManager.captureScreen(EMULATOR_NUMBER);
        if (ArenaLiveFrame.cityOrWorld(frame))return true;
        if (ArenaLiveFrame.challengeList(frame)) {
            var close=ArenaLiveFrame.listClose(frame);
            if(!close.isFound())return false;
            sleepTask(120);
            var second=ArenaLiveFrame.listClose(emuManager.captureScreen(EMULATOR_NUMBER));
            if(!dev.frostguard.engine.helper.RecognitionConsensus.agrees(close,second,10))return false;
            tapNear(second.getPoint());
            boolean arenaVisible=false;
            for(int i=0;i<10;i++){
                sleepTask(150);
                frame=emuManager.captureScreen(EMULATOR_NUMBER);
                if(ArenaLiveFrame.arenaHome(frame)){arenaVisible=true;break;}
            }
            if(!arenaVisible)return false;
        } else if (!ArenaLiveFrame.arenaHome(frame)) {
            return false;
        }
        // In the verified current Arena Home screenshot this Back arrow is
        // at (40,35); only use that coordinate after the unique Arena title.
        tapNear(new PointData(40,35));
        for(int i=0;i<12;i++){
            sleepTask(140);
            if(ArenaLiveFrame.cityOrWorld(emuManager.captureScreen(EMULATOR_NUMBER))){
                logInfo("Arena Challenge List closed and City/World Home verified via direct back route");
                return true;
            }
        }
        return false;
    }

    private boolean isWithinExecutionWindow() {
        if (!isValidTimeFormat(activationTime)) {
            return true;
        }

        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
        String[] timeParts = activationTime.split(":");
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);

        ZonedDateTime scheduledTimeUtc = nowUtc.toLocalDate()
                .atTime(hour, minute)
                .atZone(ZoneId.of("UTC"));
        ZonedDateTime cutoffTimeUtc = nowUtc.toLocalDate()
                .atTime(23, 56)
                .atZone(ZoneId.of("UTC"));

        if (nowUtc.isBefore(scheduledTimeUtc)) {
            logDebug(String.format("Task triggered too early (current: %s UTC, scheduled: %s UTC). Rescheduling for scheduled time.",
                    nowUtc.format(DateTimeFormatter.ofPattern("HH:mm")), activationTime));
            markWorkIncomplete("Arena: configured start time not yet reached");
            rescheduleForToday();
            return false;
        }

        if (nowUtc.isAfter(cutoffTimeUtc)) {
            logDebug(String.format("Task triggered too late (current: %s UTC, cutoff: 23:55 UTC). Scheduling for tomorrow.",
                    nowUtc.format(DateTimeFormatter.ofPattern("HH:mm"))));
            markWorkIncomplete("Arena: configured execution window closed before challenges were attempted");
            rescheduleWithActivationHour();
            return false;
        }

        logDebug(String.format("Task running within window (current: %s UTC, window: %s - 23:55 UTC)",
                nowUtc.format(DateTimeFormatter.ofPattern("HH:mm")), activationTime));
        return true;
    }

    private String captureArenaDiagnostic(String kind) {
        return TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER, "arena", kind);
    }

    private boolean navigateToArena() {
        if (arenaHomeVisible()) return true;
        if (!navigationHelper.navigateToSidebarDestination(SidebarDestination.ARENA)) return false;
        return arenaHomeVisible();
    }

    private boolean arenaHomeVisible() {
        // On Serve-Emu the title is an optional PAGE PRESENCE probe, not
        // an actionable miss. The same capture is inspected locally and the
        // proven 2026 exact-title ROI is retained. The Challenge tap is still
        // independently confirmed by locateAction on two frames.
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            return ArenaLiveFrame.arenaHome(emuManager.captureScreen(EMULATOR_NUMBER));
        }
        // Live 720x1280 frame: Arena of Exploration header is at y~40.
        // Previous y70..150 search scored only 31.93%; corrected ROI 99.28%.
        if (templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_ARENA_TITLE,
                SearchConfig.builder().withMaxAttempts(1)
                        .withThreshold(90).withArea(AreaData.of(70, 0, 470, 105)).build()).isFound())
            return true;
        // Classic BlueStacks art has no 2026 title template; keep its
        // proven Challenge control as the home-screen fallback.
        return templateSearchHelper.locatePattern(TemplatesEnum.ARENA_CHALLENGE_BUTTON_CURRENT,
                    TRANSIENT_BUTTON_SEARCH).isFound()
                || templateSearchHelper.locatePattern(TemplatesEnum.ARENA_CHALLENGE_BUTTON,
                    TRANSIENT_BUTTON_SEARCH).isFound();
    }

    private boolean challengeListVisible() {
        // A closed Challenge List is normal on the Arena home/formation.
        // Do not run two retries + implicit fallbacks or classify absence
        // as an error. Actual tapping still requires separate verification.
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            return ArenaLiveFrame.challengeList(emuManager.captureScreen(EMULATOR_NUMBER));
        }
        return templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_ARENA_LIST_TITLE,
                SearchConfig.builder().withMaxAttempts(2).withDelay(150)
                        .withThreshold(90).withArea(AreaData.of(230, 170, 510, 280)).build()).isFound();
    }

    private boolean detectFirstRun() {
        logDebug("Checking if this is first arena run");

        OcrSettingsData configMap = OcrSettingsData.assembler()
                .stripBackground(true)
                .setTextColor(new Color(255, 255, 255))
                .charWhitelist("0123456789")
                .build();

        Integer arenaScore = readNumberValue(
                ARENA_SCORE_TOP_LEFT,
                ARENA_SCORE_BOTTOM_RIGHT,
                configMap);

        if (arenaScore == null) {
            logWarning("Failed to read arena score, assuming not first run");
            return false;
        }

        logInfo("Arena score: " + arenaScore);
        boolean isFirstRun = arenaScore == INITIAL_ARENA_SCORE;

        if (isFirstRun) {
            logInfo("First run detected (score = 1000)");
        }

        return isFirstRun;
    }

    private boolean openChallengeList() {
        if (challengeListVisible()) return true;
        if (!arenaHomeVisible()) {
            logWarning("Arena home header unverified; refusing Challenge tap");
            return false;
        }
        ImageSearchResultData challenge = templateSearchHelper.locateAction(
                TemplatesEnum.SERVE_EMU_ARENA_CHALLENGE,
                SearchConfig.builder().withMaxAttempts(2).withDelay(150)
                        .withThreshold(90).withArea(AreaData.of(190, 1150, 535, 1270)).build(),
                TemplatesEnum.ARENA_CHALLENGE_BUTTON_CURRENT,
                TemplatesEnum.ARENA_CHALLENGE_BUTTON);
        if (!challenge.isFound()) {
            logWarning("Challenge not active: first-run defensive lineup may require setup; "
                    + captureArenaDiagnostic("challenge-unavailable-or-onboarding"));
            return false;
        }
        tapInside(challenge);
        for (int i = 0; i < 5; i++) {
            sleepTask(180);
            if (challengeListVisible()) return true;
        }
        logWarning("Challenge tap did not open list; " + captureArenaDiagnostic("challenge-transition-unverified"));
        return false;
    }

    private boolean readInitialAttempts() {
        logDebug("Reading initial challenge attempts");

        OcrSettingsData strictSettings = OcrSettingsData.assembler()
                .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)

                .stripBackground(true)
                .setTextColor(new Color(91, 112, 147))
                .charWhitelist("0123456789")
                .build();

        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            return readVerifiedLiveChallengeCount();
        }

        Integer attemptsRead = readNumberValue(
                CHALLENGES_LEFT_TOP_LEFT,
                CHALLENGES_LEFT_BOTTOM_RIGHT,
                strictSettings);

        if (attemptsRead == null) {
            logDebug("Attempt count strict OCR did not produce a value. Retrying with relaxed settings.");

            OcrSettingsData relaxedSettings = OcrSettingsData.assembler()
                    .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)

                    .stripBackground(true)
                    .charWhitelist("0123456789")
                    .build();

            attemptsRead = readNumberValue(
                    CHALLENGES_LEFT_TOP_LEFT,
                    CHALLENGES_LEFT_BOTTOM_RIGHT,
                    relaxedSettings);
        }

        if (attemptsRead == null) {
            logWarning("Attempt count OCR failed. Remaining attempts are unknown; declining to attack.");
            return false;
        }

        if (attemptsRead < 0 || attemptsRead > MAX_REASONABLE_CHALLENGE_ATTEMPTS) {
            logWarning(String.format(
                    "Attempt count OCR produced implausible value %d. Declining to attack.",
                    attemptsRead));
            return false;
        }

        this.attempts = attemptsRead;
        logInfo("Initial attempts available: " + attempts);
        return true;
    }

    static int parseDailyChallengeLabel(String text) {
        if (text == null) return -1;
        Matcher matcher = DAILY_CHALLENGE_LABEL.matcher(text.replace('\n', ' '));
        if (!matcher.find()) return -1;
        int count = Integer.parseInt(matcher.group(1));
        return count <= MAX_REASONABLE_CHALLENGE_ATTEMPTS ? count : -1;
    }

    private int readLiveChallengeLabel() {
        try {
            String label = emuManager.readText(EMULATOR_NUMBER,
                    CHALLENGES_LABEL_TOP_LEFT, CHALLENGES_LABEL_BOTTOM_RIGHT,
                    OcrSettingsData.assembler()
                            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
                            .build(), false);
            return parseDailyChallengeLabel(label);
        } catch (Exception error) {
            if (Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Arena label OCR interrupted");
            logWarning("Arena labelled attempt count OCR unavailable: "
                    + error.getClass().getSimpleName());
            return -1;
        }
    }

    private boolean readVerifiedLiveChallengeCount() {
        int first = readLiveChallengeLabel();
        sleepTask(170);
        int second = readLiveChallengeLabel();
        if (first < 0 || first != second) {
            logWarning("Arena labelled Daily challenges count is unverified ("
                    + first + "/" + second + "); refusing battles");
            return false;
        }
        attempts = first;
        logInfo("Verified Arena Daily challenges remaining: " + attempts);
        return true;
    }

    private void processChallenges() {
        logInfo(String.format("Processing %d challenge attempts", attempts));

        while (true) {
            while (attempts > 0) {
                OpponentCandidate opponent = findEligibleOpponent();
                if (opponent == null) {
                    // Exhaust FREE refreshes before risking a stronger red
                    // opponent. Losing still consumes one free daily challenge,
                    // so the player can reach 5/5 and collect the daily chest.
                    if (tryRefreshOpponentList()) {
                        logInfo("No weaker opponent; free list refreshed before red fallback.");
                        continue;
                    }
                    opponent = findLastResortOpponent();
                    if (opponent == null) {
                        logWarning(String.format("No verified Arena opponent after free refreshes. %d attempts unused; %s",
                                attempts, captureArenaDiagnostic("no-verified-opponents")));
                        return;
                    }
                    logInfo("Free daily challenges remain with no weaker target: using "
                            + (opponent.power().relation() == PowerRelation.STRONGER ? "RED" : "GREEN")
                            + " opponent " + opponent.number() + " after verified challenge-button check.");
                }
                if (opponent != null) {
                    ChallengeOutcome outcome = challengeOpponent(opponent);
                    if (outcome == ChallengeOutcome.NO_ATTEMPTS) {
                        break;
                    }
                    if (outcome == ChallengeOutcome.UNKNOWN) {
                        logWarning("Arena result not verified; stop rather than spending another attempt or tapping unknown UI");
                        unknownBattleResult=true;
                        return;
                    }
                    if (outcome == ChallengeOutcome.DEFEAT) {
                        if (opponent.power().relation() == PowerRelation.STRONGER) {
                            // Expected last-resort loss. It still counts as
                            // one daily challenge. Try another verified red
                            // opponent until all free attempts are consumed;
                            // never buy more attempts or paid refreshes.
                            logInfo("Arena red fallback defeat confirmed; "
                                    + attempts + " free daily challenge(s) remain.");
                            continue;
                        }
                        if (!shouldRefreshAfterDefeat(attempts)) {
                            logInfo(String.format(
                                    "Our attack lost to opponent %d. No attempts remain; skipping list refresh.",
                                    opponent.number()));
                            continue;
                        }
                        logInfo(String.format(
                                "Our attack lost to opponent %d. Refreshing list before using another attempt.",
                                opponent.number()));
                        if (tryRefreshOpponentList()) {
                            logInfo("List refreshed after defeat. Rescanning opponents.");
                            continue;
                        }
                        logInfo(String.format(
                                "No list refresh available after defeat. Continuing current list with tried opponent %d excluded. Attempts unused: %d",
                                opponent.number(),
                                attempts));
                        continue;
                    }
                    continue;
                }

            }

            if (extraAttemptsPurchased || extraAttempts <= 0) {
                break;
            }

            extraAttemptsPurchased = true;
            OpponentCandidate availableTarget = findEligibleOpponent();
            if (availableTarget == null) {
                logInfo("Extra attempts configured, but no eligible target is visible. Skipping extra attempt purchase.");
                break;
            }

            int attemptsBought = buyExtraAttempts();
            if (attemptsBought <= 0) {
                break;
            }

            attempts += attemptsBought;
            logInfo(String.format("Purchased %d extra attempts after confirming an eligible target. Total attempts: %d",
                    attemptsBought, attempts));
        }

        if (noDailyChallengesDetected && extraAttempts <= 0) {
            logInfo("No daily arena challenges are available and extra attempts are disabled.");
        } else {
            logInfo("Arena run finished after using available eligible attempts.");
        }
    }

    /**
     * Claims the Daily Challenges chest from the Challenge List, where the run
     * is still standing when its last attempt ends.
     *
     * <p>This is the dependable route to the day's reward. The same chest also
     * floats over the Arena building, but the city camera pans freely and a
     * building scrolled off screen reads exactly like a chest already taken.</p>
     */
    private void claimDailyChallengesChest() {
        if (!isDailyChestReady()) {
            logInfo("Daily Challenges chest is not ready in the Challenge List.");
            return;
        }

        logInfo("Daily Challenges chest is ready. Opening it.");
        tapNear(DAILY_CHEST_POINT);

        if (!waitForRewardsPanel()) {
            logWarning("Daily Challenges chest tapped but no Rewards panel appeared. "
                    + "No blind Android Back: recovery will inspect the known Arena page.");
            return;
        }

        tapNear(REWARDS_DISMISS_POINT);
        sleepTask(REWARDS_PANEL_SETTLE_MS);
        if (rewardsPanelVisible()) {
            markWorkIncomplete("Arena: daily chest Rewards did not close");
            logWarning("Daily Challenges chest reward was displayed, but the panel did not close; "
                    + captureArenaDiagnostic("daily-chest-reward-overlay-stuck"));
            return;
        }

        StatisticsService.obtain().addToCounter(profile, "Arena Chests", 1);
        logInfo("Daily Challenges chest claimed.");
    }

    private boolean isDailyChestReady() {
        for (int attempt = 1; attempt <= DAILY_CHEST_CHECK_ATTEMPTS; attempt++) {
            BufferedImage image = captureFrameForPixelScan("arena daily chest");
            if (image != null && showsFinishedDailyChestCount(image)) {
                sleepTask(250);
                BufferedImage second = captureFrameForPixelScan("arena daily chest confirmation");
                if (second != null && showsFinishedDailyChestCount(second)) return true;
            }
            if (attempt < DAILY_CHEST_CHECK_ATTEMPTS) {
                sleepTask(DAILY_CHEST_CHECK_DELAY_MS);
            }
        }
        return false;
    }

    static boolean showsFinishedDailyChestCount(BufferedImage image) {
        int green = PixelStats.count(image, DAILY_CHEST_COUNT_LEADING_DIGIT, ArenaRoutine::isDailyChestCountGreen);
        return green >= MIN_DAILY_CHEST_GREEN_PIXELS;
    }

    private static boolean isDailyChestCountGreen(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return green > red + DAILY_CHEST_GREEN_MARGIN && green > blue + DAILY_CHEST_GREEN_MARGIN;
    }

    private boolean waitForRewardsPanel() {
        int waited = 0;
        while (waited < REWARDS_PANEL_TIMEOUT_MS) {
            if (rewardsPanelVisible()) {
                return true;
            }
            sleepTask(REWARDS_PANEL_POLL_MS);
            waited += REWARDS_PANEL_POLL_MS;
        }
        return false;
    }

    private boolean rewardsPanelVisible() {
        return templateSearchHelper.locatePattern(TemplatesEnum.ARENA_REWARDS_BANNER,
                SearchConfig.builder().withMaxAttempts(1)
                        .withThreshold(REWARDS_BANNER_THRESHOLD)
                        .withArea(AreaData.of(30, 245, 690, 400)).build()).isFound();
    }

    static boolean shouldRefreshAfterDefeat(int remainingAttempts) {
        return remainingAttempts > 0;
    }

    /**
      * Gem price of each successive extra attempt, cheapest first.
      *
      * <p>Exposed so the settings screen can price the option it offers instead
      * of restating the figures and drifting from them.</p>
      */
    public static int[] extraAttemptGemPrices() {
        return ATTEMPT_PRICES.clone();
    }

    /** List refreshes granted per run at no cost, always spent before any paid one. */
    public static int maxFreeListRefreshes() {
        return MAX_FREE_REFRESHES;
    }

    /** Further list refreshes available per run once the free ones are gone, each paid for in gems. */
    public static int maxPaidListRefreshes() {
        return MAX_GEM_REFRESHES;
    }

    private OpponentCandidate findEligibleOpponent() {
        List<OpponentCandidate> opponents = scanOpponents();
        List<OpponentCandidate> eligible = opponents.stream()
                .filter(OpponentCandidate::eligible)
                .filter(candidate -> !attemptedOpponentSlots.contains(candidate.number()))
                .sorted(alliancePolicy.comparator(profileAllianceTag)
                        .thenComparing(serverPolicy.comparator(profileServer))
                        .thenComparing(candidate -> candidate.power().value(), ArenaRoutine::comparePowerValues)
                        .thenComparingInt(OpponentCandidate::number))
                .toList();

        if (eligible.isEmpty()) {
            long alreadyTried = opponents.stream()
                    .filter(OpponentCandidate::eligible)
                    .filter(candidate -> attemptedOpponentSlots.contains(candidate.number()))
                    .count();
            if (alreadyTried > 0) {
                logInfo(String.format(
                        "No eligible opponent found in current arena list. %d eligible opponent(s) were already tried in this list.",
                        alreadyTried));
            } else {
                logInfo("No eligible opponent found in current arena list.");
            }
            return null;
        }

        OpponentCandidate selected = eligible.get(0);
        logInfo(String.format("Selected opponent %d (%s)", selected.number(), selected.selectionSummary()));
        return selected;
    }

    /**
     * Only called after the routine has failed to find green and exhausted
     * permitted free refreshes. Re-scan on a fresh Arena List and keep all
     * profile-alliance/server hard exclusions.
     */
    private OpponentCandidate findLastResortOpponent() {
        List<OpponentCandidate> allowed = scanOpponents(true).stream()
                .filter(OpponentCandidate::eligible)
                .filter(candidate -> !attemptedOpponentSlots.contains(candidate.number()))
                .toList();
        // Always prefer a newly appeared green opponent, even in fallback.
        Comparator<OpponentCandidate> preference = alliancePolicy.comparator(profileAllianceTag)
                .thenComparing(serverPolicy.comparator(profileServer))
                .thenComparing(candidate -> candidate.power().value(), ArenaRoutine::comparePowerValues)
                .thenComparingInt(OpponentCandidate::number);
        List<OpponentCandidate> ranked = ArenaFallbackPolicy.preferGreenBeforeRed(
                allowed, candidate -> candidate.power().relation() == PowerRelation.WEAKER,
                preference);
        if (ranked.isEmpty()) return null;
        OpponentCandidate selected = ranked.get(0);
        logInfo("Arena last-resort selection: opponent " + selected.number() + " " + selected.selectionSummary());
        return selected;
    }

    private List<OpponentCandidate> scanOpponents() {
        return scanOpponents(false);
    }

    private List<OpponentCandidate> scanOpponents(boolean allowStronger) {
        int baseY = firstRun ? OPPONENT_BASE_Y_FIRST_RUN : OPPONENT_BASE_Y_NORMAL;
        List<OpponentCandidate> opponents = new ArrayList<>(MAX_OPPONENTS);

        for (int i = 0; i < MAX_OPPONENTS; i++) {
            int opponentY = baseY + (i * OPPONENT_Y_SPACING);
            int opponentNumber = i + 1;
            OpponentLayout layout = detectOpponentLayout(opponentY, opponentNumber);
            PowerRead power = readPower(opponentY, opponentNumber, layout, allowStronger);
            AllianceRead alliance = AllianceRead.notChecked();
            ServerRead server = ServerRead.notChecked();
            if (power.relation() == PowerRelation.WEAKER
                    || allowStronger && power.relation() == PowerRelation.STRONGER) {
                if (alliancePolicy != AlliancePolicy.ANY && profileAllianceTag != null) {
                    alliance = readAllianceTag(opponentY, opponentNumber);
                }
                if (serverPolicy != ServerPolicy.ANY && profileServer != null) {
                    server = readServer(opponentY, opponentNumber, layout);
                }
            }
            OpponentCandidate candidate = classifyOpponent(
                    opponentNumber, opponentY, power, alliance, server, allowStronger);
            opponents.add(candidate);
            logInfo(String.format("Opponent %d scan: layout=%s power=%s alliance=%s server=%s decision=%s",
                    opponentNumber, layout.label, power.logValue(), alliance.logValue(), server.logValue(), candidate.decision()));
            sleepTask(150);
        }

        return opponents;
    }

    private OpponentCandidate classifyOpponent(int number, int opponentY, PowerRead power,
                                               AllianceRead alliance, ServerRead server,
                                               boolean allowStronger) {
        if (!ArenaFallbackPolicy.selectablePower(
                power.relation() == PowerRelation.WEAKER,
                power.relation() == PowerRelation.STRONGER,
                allowStronger)) {
            return OpponentCandidate.skipped(number, opponentY, power, alliance, server, power.relation().skipReason);
        }

        if (alliancePolicy == AlliancePolicy.NEVER_PROFILE_ALLIANCE && profileAllianceTag != null) {
            if (alliance.status == AllianceStatus.UNREADABLE) {
                return OpponentCandidate.skipped(number, opponentY, power, alliance, server, "alliance unreadable");
            }
            if (profileAllianceTag.equals(alliance.tag)) {
                return OpponentCandidate.skipped(number, opponentY, power, alliance, server, "profile alliance");
            }
        }

        if (serverPolicy == ServerPolicy.NEVER_PROFILE_SERVER && profileServer != null) {
            if (server.status != ServerStatus.READ) {
                return OpponentCandidate.skipped(number, opponentY, power, alliance, server, "server not visible/readable");
            }
            if (profileServer.equals(server.value)) {
                return OpponentCandidate.skipped(number, opponentY, power, alliance, server, "profile server");
            }
        }

        return OpponentCandidate.eligible(number, opponentY, power, alliance, server);
    }

    private OpponentLayout detectOpponentLayout(int opponentY, int opponentNumber) {
        int serverStarTopY = opponentY + OPPONENT_SCORE_STAR_SERVER_LAYOUT_Y_OFFSET;
        int compactStarTopY = opponentY + OPPONENT_SCORE_STAR_COMPACT_LAYOUT_Y_OFFSET;
        BufferedImage image = captureFrameForPixelScan("arena layout probe");
        if (image == null) {
            logWarning(String.format("Opponent %d layout probe failed; assuming server layout.", opponentNumber));
            return OpponentLayout.UNKNOWN;
        }

        int serverLayoutYellow = countPixels(
                image,
                new PointData(OPPONENT_SCORE_STAR_X, serverStarTopY),
                new PointData(
                        OPPONENT_SCORE_STAR_X + OPPONENT_SCORE_STAR_WIDTH,
                        serverStarTopY + OPPONENT_SCORE_STAR_HEIGHT),
                GameColors::isArenaStarYellow);
        int compactLayoutYellow = countPixels(
                image,
                new PointData(OPPONENT_SCORE_STAR_X, compactStarTopY),
                new PointData(
                        OPPONENT_SCORE_STAR_X + OPPONENT_SCORE_STAR_WIDTH,
                        compactStarTopY + OPPONENT_SCORE_STAR_HEIGHT),
                GameColors::isArenaStarYellow);
        int minStarYellowPixels = scaleSampledThreshold(MIN_STAR_YELLOW_PIXELS);

        OpponentLayout layout;
        if (serverLayoutYellow >= minStarYellowPixels
                && serverLayoutYellow >= compactLayoutYellow) {
            layout = OpponentLayout.SERVER_ROW;
        } else if (compactLayoutYellow >= minStarYellowPixels) {
            layout = OpponentLayout.COMPACT;
        } else {
            layout = OpponentLayout.UNKNOWN;
        }

        logDebug(String.format("Opponent %d layout probe: %s (upper star=%d, lower star=%d)",
                opponentNumber, layout.label, serverLayoutYellow, compactLayoutYellow));
        return layout;
    }

    private PowerRead readPower(int opponentY, int opponentNumber,
                                OpponentLayout layout, boolean allowStronger) {
        PowerRead primary = readPowerRelationAt(
                opponentY,
                opponentNumber,
                layout.powerYOffset(),
                layout.powerHeight(),
                layout.label);
        if (primary.relation() != PowerRelation.UNKNOWN) {
            if (primary.relation() == PowerRelation.STRONGER && !allowStronger) return primary;
            return enrichPowerWithOcr(
                    opponentY, opponentNumber, layout.powerYOffset(), layout.powerHeight(), layout.label, primary);
        }

        OpponentLayout fallbackLayout = layout == OpponentLayout.COMPACT
                ? OpponentLayout.SERVER_ROW
                : OpponentLayout.COMPACT;
        PowerRead fallback = readPowerRelationAt(
                opponentY,
                opponentNumber,
                fallbackLayout.powerYOffset(),
                fallbackLayout.powerHeight(),
                fallbackLayout.label + " fallback");
        if (fallback.relation() != PowerRelation.UNKNOWN) {
            if (fallback.relation() == PowerRelation.STRONGER && !allowStronger) return fallback;
            return enrichPowerWithOcr(
                    opponentY, opponentNumber, fallbackLayout.powerYOffset(), fallbackLayout.powerHeight(),
                    fallbackLayout.label + " fallback", fallback);
        }
        return fallback;
    }

    private PowerRead readPowerRelationAt(int opponentY, int opponentNumber, int yOffset, int height, String label) {
        int powerY = opponentY + yOffset;
        logDebug(String.format("Analyzing opponent %d power (%s, y=%d)",
                opponentNumber, label, powerY));

        AreaData powerArea = powerTextArea(opponentY, yOffset, height);
        PointData topLeft = powerArea.topLeft();
        PointData bottomRight = powerArea.bottomRight();

        BufferedImage image = captureFrameForPixelScan("arena power read");
        if (image == null) {
            return PowerRead.withoutValue(PowerRelation.UNKNOWN);
        }

        int backgroundPixels = countPixels(image, topLeft, bottomRight, GameColors::isArenaBlueGrey);
        int greenPixels = countPixels(image, topLeft, bottomRight, GameColors::isArenaPowerGreen);
        int redPixels = countPixels(image, topLeft, bottomRight, GameColors::isArenaPowerRed);
        int totalColoredPixels = greenPixels + redPixels;
        int minColoredPixels = scaleSampledThreshold(MIN_COLORED_PIXELS_THRESHOLD);

        logDebug(String.format(
                "Opponent %d power colors (%s) - Background: %d, Green: %d, Red: %d, TotalColor: %d, Threshold: %d",
                opponentNumber, label, backgroundPixels, greenPixels, redPixels,
                totalColoredPixels, minColoredPixels));

        if (totalColoredPixels <= minColoredPixels) {
            return PowerRead.withoutValue(PowerRelation.UNKNOWN);
        }
        if (greenPixels > redPixels * GREEN_DOMINANCE_RATIO) {
            return PowerRead.withoutValue(PowerRelation.WEAKER);
        }
        if (redPixels > greenPixels * RED_DOMINANCE_RATIO) {
            return PowerRead.withoutValue(PowerRelation.STRONGER);
        }
        return PowerRead.withoutValue(PowerRelation.UNKNOWN);
    }

    private PowerRead enrichPowerWithOcr(int opponentY, int opponentNumber, int yOffset, int height,
                                         String label, PowerRead colorRead) {
        if (colorRead.relation() == PowerRelation.UNKNOWN) return colorRead;
        AreaData powerArea = powerTextArea(opponentY, yOffset, height);
        PointData topLeft = powerArea.topLeft();
        PointData bottomRight = powerArea.bottomRight();

        OcrSettingsData powerSettings = colorRead.relation() == PowerRelation.STRONGER
                ? powerOcrSettings(ARENA_POWER_RED_TEXT) : powerOcrSettings();
        // For a last-resort red target, one OCR attempt is sufficient to
        // choose a lower-power foe if readable; when unreadable the button
        // and RED color remain the authorization, not speculative OCR.
        String text = stringHelper.attemptRecognition(
                topLeft,
                bottomRight,
                colorRead.relation() == PowerRelation.STRONGER ? 1 : 3,
                150L,
                powerSettings,
                value -> parsePowerValue(value) != null,
                value -> value);
        Double powerValue = parsePowerValue(text);
        if (powerValue == null) {
            logDebug(String.format("Opponent %d power OCR (%s) did not produce a sortable value.", opponentNumber, label));
            return colorRead;
        }

        logDebug(String.format("Opponent %d power OCR (%s): '%s' -> %.0f",
                opponentNumber, label, text, powerValue));
        return new PowerRead(colorRead.relation(), powerValue, text);
    }

    static OcrSettingsData powerOcrSettings() {
        return powerOcrSettings(ARENA_POWER_GREEN_TEXT);
    }

    private static OcrSettingsData powerOcrSettings(Color textColor) {
        return OcrSettingsData.assembler()
                .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
                .stripBackground(true)
                .setTextColor(textColor)
                .charWhitelist("0123456789.,KMBkmb")
                .build();
    }

    static AreaData serverPowerTextArea(int opponentY) {
        return powerTextArea(opponentY, POWER_TEXT_SERVER_LAYOUT_Y_OFFSET, POWER_TEXT_SERVER_LAYOUT_HEIGHT);
    }

    private static AreaData powerTextArea(int opponentY, int yOffset, int height) {
        int powerY = opponentY + yOffset;
        return AreaData.of(
                POWER_TEXT_RELATIVE_X,
                powerY,
                POWER_TEXT_RELATIVE_X + POWER_TEXT_WIDTH,
                powerY + height);
    }

    static Double parsePowerValue(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim().replace(" ", "");
        Matcher matcher = POWER_VALUE_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }

        String number = matcher.group(1);
        String unit = matcher.group(2).toUpperCase(Locale.ROOT);
        if (number.contains(",") && !number.contains(".")) {
            number = number.replace(",", ".");
        } else {
            number = number.replace(",", "");
        }

        try {
            double value = Double.parseDouble(number);
            return switch (unit) {
                case "K" -> value * 1_000D;
                case "M" -> value * 1_000_000D;
                case "B" -> value * 1_000_000_000D;
                default -> throw new IllegalStateException("Unsupported arena power unit: " + unit);
            };
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static int comparePowerValues(Double left, Double right) {
        if (left == null) {
            return right == null ? 0 : 1;
        }
        if (right == null) {
            return -1;
        }
        return Double.compare(left, right);
    }

    private AllianceRead readAllianceTag(int opponentY, int opponentNumber) {
        String text = readOpponentText(
                new PointData(OPPONENT_NAME_X, opponentY + OPPONENT_NAME_RELATIVE_Y),
                new PointData(OPPONENT_NAME_X + OPPONENT_NAME_WIDTH, opponentY + OPPONENT_NAME_RELATIVE_Y + OPPONENT_NAME_HEIGHT),
                "[]ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789",
                "alliance/name",
                opponentNumber);

        AllianceRead read = classifyAllianceText(text);
        if (read.status == AllianceStatus.UNREADABLE) {
            logDebug(String.format("Opponent %d alliance/name OCR inconclusive (%s). Retrying once.",
                    opponentNumber, read.detail()));
            sleepTask(OCR_RETRY_DELAY_MS);
            String retryText = readOpponentText(
                    new PointData(OPPONENT_NAME_X, opponentY + OPPONENT_NAME_RELATIVE_Y),
                    new PointData(OPPONENT_NAME_X + OPPONENT_NAME_WIDTH, opponentY + OPPONENT_NAME_RELATIVE_Y + OPPONENT_NAME_HEIGHT),
                    "[]ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789",
                    "alliance/name retry",
                    opponentNumber);
            read = classifyAllianceText(retryText);
            if (read.status == AllianceStatus.UNREADABLE) {
                logWarning(String.format("Opponent %d alliance/name OCR failed after retry (%s).",
                        opponentNumber, read.detail()));
            }
        }

        return read;
    }

    private AllianceRead classifyAllianceText(String text) {
        if (text == null || text.isBlank()) {
            return AllianceRead.unreadable("empty");
        }
        Matcher matcher = ALLIANCE_TAG_PATTERN.matcher(text);
        if (matcher.find()) {
            String tag = normalizeAllianceTag(matcher.group(1));
            return AllianceRead.read(resolveProfileAllianceIfClose(tag));
        }
        if (looksLikeProfileAlliance(text)) {
            return AllianceRead.read(profileAllianceTag);
        }
        Matcher malformedMatcher = MALFORMED_ALLIANCE_TAG_PATTERN.matcher(text);
        if (malformedMatcher.find()) {
            String tag = normalizeAllianceTag(malformedMatcher.group(1));
            if (matchesProfileAllianceFuzzy(tag)) {
                return AllianceRead.read(profileAllianceTag);
            }
            return AllianceRead.malformed(tag, "missing closing bracket");
        }
        String bracketlessTag = extractBracketlessAllianceTag(text);
        if (bracketlessTag != null) {
            if (matchesProfileAllianceFuzzy(bracketlessTag)) {
                return AllianceRead.read(profileAllianceTag);
            }
            return AllianceRead.malformed(bracketlessTag, "bracket-like prefix");
        }
        String trailingBracketTag = extractTrailingBracketAllianceTag(text);
        if (trailingBracketTag != null) {
            if (matchesProfileAllianceFuzzy(trailingBracketTag)) {
                return AllianceRead.read(profileAllianceTag);
            }
            return AllianceRead.malformed(trailingBracketTag, "bracket-like closing marker");
        }
        if (looksLikeGuard(text)) {
            return AllianceRead.guard();
        }
        if (text.contains("[") || text.contains("]")) {
            return AllianceRead.unreadable("bracket without tag");
        }

        return AllianceRead.noAlliance();
    }

    private ServerRead readServer(int opponentY, int opponentNumber, OpponentLayout layout) {
        if (!layout.serverVisible()) {
            return new ServerRead(null, ServerStatus.NOT_SHOWN);
        }

        String text = readOpponentText(
                new PointData(OPPONENT_SERVER_X, opponentY + OPPONENT_SERVER_RELATIVE_Y),
                new PointData(OPPONENT_SERVER_X + OPPONENT_SERVER_WIDTH, opponentY + OPPONENT_SERVER_RELATIVE_Y + OPPONENT_SERVER_HEIGHT),
                "#0123456789",
                new Color(255, 255, 255),
                "server",
                opponentNumber);

        if (text == null || text.isBlank()) {
            return new ServerRead(null, ServerStatus.NOT_SHOWN);
        }

        Matcher matcher = SERVER_PATTERN.matcher(text);
        if (matcher.find()) {
            return new ServerRead(normalizeServer(matcher.group(1)), ServerStatus.READ);
        }

        logDebug(String.format("Opponent %d server OCR failed. Retrying once.", opponentNumber));
        sleepTask(OCR_RETRY_DELAY_MS);
        String retryText = readOpponentText(
                new PointData(OPPONENT_SERVER_X, opponentY + OPPONENT_SERVER_RELATIVE_Y),
                new PointData(OPPONENT_SERVER_X + OPPONENT_SERVER_WIDTH, opponentY + OPPONENT_SERVER_RELATIVE_Y + OPPONENT_SERVER_HEIGHT),
                "#0123456789",
                new Color(255, 255, 255),
                "server retry",
                opponentNumber);
        Matcher retryMatcher = SERVER_PATTERN.matcher(retryText == null ? "" : retryText);
        if (retryMatcher.find()) {
            return new ServerRead(normalizeServer(retryMatcher.group(1)), ServerStatus.READ);
        }
        logWarning(String.format("Opponent %d server OCR failed after retry: '%s'", opponentNumber, retryText));
        return new ServerRead(null, ServerStatus.UNREADABLE);
    }

    private String readOpponentText(PointData topLeft, PointData bottomRight, String whitelist,
                                    String label, int opponentNumber) {
        return readOpponentText(topLeft, bottomRight, whitelist, null, label, opponentNumber);
    }

    private String readOpponentText(PointData topLeft, PointData bottomRight, String whitelist, Color textColor,
                                    String label, int opponentNumber) {
        OcrSettingsData settings = OcrSettingsData.assembler()
                .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)

                .stripBackground(true)
                .setTextColor(textColor)
                .charWhitelist(whitelist)
                .build();

        String text = readStringValue(topLeft, bottomRight, settings);
        logDebug(String.format("Opponent %d %s OCR result: %s",
                opponentNumber, label, text == null ? "null" : "'" + text + "'"));
        return text == null ? null : text.trim();
    }

    private BufferedImage captureFrameForPixelScan(String context) {
        try {
            RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
            return dev.frostguard.vision.convert.ImageConverter.toBufferedImage(frame);
        } catch (Exception ex) {
            logError(String.format("%s color analysis failed: %s", context, ex.getMessage()));
            return null;
        }
    }

    private int countPixels(BufferedImage image, PointData topLeft, PointData bottomRight, IntPredicate matcher) {
        return PixelStats.count(image, new AreaData(topLeft, bottomRight), matcher);
    }

    private int scaleSampledThreshold(int sampledThreshold) {
        return sampledThreshold * COLOR_ANALYSIS_STEP_SIZE * COLOR_ANALYSIS_STEP_SIZE;
    }

    private boolean looksLikeProfileAlliance(String text) {
        if (profileAllianceTag == null || text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalizeAllianceTag(text.replaceAll("[^A-Za-z0-9]", ""));
        if (normalized == null) {
            return false;
        }
        int prefixLength = Math.min(normalized.length(), Math.max(6, profileAllianceTag.length()));
        return normalized.substring(0, prefixLength).contains(profileAllianceTag);
    }

    private String resolveProfileAllianceIfClose(String tag) {
        return matchesProfileAllianceFuzzy(tag) ? profileAllianceTag : tag;
    }

    private boolean matchesProfileAllianceFuzzy(String tag) {
        if (profileAllianceTag == null || tag == null || tag.length() != profileAllianceTag.length()) {
            return false;
        }
        if (profileAllianceTag.equals(tag)) {
            return true;
        }
        int differences = 0;
        for (int i = 0; i < tag.length(); i++) {
            if (tag.charAt(i) != profileAllianceTag.charAt(i)) {
                differences++;
            }
        }
        return differences <= 1;
    }

    private String extractBracketlessAllianceTag(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String compact = text.replaceAll("\\s", "");
        Matcher matcher = BRACKETLESS_ALLIANCE_TAG_PATTERN.matcher(compact);
        if (!matcher.find()) {
            return null;
        }
        String tag = normalizeAllianceTag(matcher.group(1));
        return tag == null || tag.isBlank() ? null : tag;
    }

    private String extractTrailingBracketAllianceTag(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String compact = text.replaceAll("\\s", "");
        Matcher matcher = BRACKETLESS_TRAILING_BRACKET_PATTERN.matcher(compact);
        if (!matcher.find()) {
            return null;
        }
        String tag = normalizeAllianceTag(matcher.group(1));
        return tag == null || tag.isBlank() ? null : tag;
    }

    private boolean looksLikeGuard(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        return normalized.contains("COMMONGUARD")
                || normalized.contains("VETERANGUARD")
                || normalized.equals("GUARD");
    }

    private ChallengeOutcome challengeOpponent(OpponentCandidate opponent) {
        logInfo(String.format("Challenging opponent %d (%s)", opponent.number(), opponent.selectionSummary()));

        if (!isChallengeButtonEnabled(opponent)) {
            logInfo(String.format(
                    "Opponent %d challenge button is disabled. No free attempts available; checking extra attempts.",
                    opponent.number()));
            attempts = 0;
            noDailyChallengesDetected = true;
            return ChallengeOutcome.NO_ATTEMPTS;
        }

        attemptedOpponentSlots.add(opponent.number());
        tapNear(new PointData(OPPONENT_CHALLENGE_BUTTON_X, opponent.opponentY()));
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            ArenaLiveFrame.Result outcome=executeServeEmuBattleSequence();
            if (outcome != ArenaLiveFrame.Result.VICTORY
                    && outcome != ArenaLiveFrame.Result.DEFEAT) return ChallengeOutcome.UNKNOWN;
            attempts--;
            firstRun=false;
            if(outcome == ArenaLiveFrame.Result.VICTORY){
                StatisticsService.obtain().addToCounter(profile,"Arena Battles Won",1);
                attemptedOpponentSlots.clear();
                return ChallengeOutcome.VICTORY;
            }
            StatisticsService.obtain().addToCounter(profile,"Arena Battles Lost",1);
            return ChallengeOutcome.DEFEAT;
        }
        sleepTask(2000);
        executeBattleSequence();
        attempts--;

        boolean victory = checkBattleResult();
        firstRun = false;
        if (victory) {
            StatisticsService.obtain().addToCounter(profile, "Arena Battles Won", 1);
            attemptedOpponentSlots.clear();
        } else {
            StatisticsService.obtain().addToCounter(profile, "Arena Battles Lost", 1);
        }
        sleepTask(1000);
        return victory ? ChallengeOutcome.VICTORY : ChallengeOutcome.DEFEAT;
    }

    private boolean isChallengeButtonEnabled(OpponentCandidate opponent) {
        PointData topLeft = new PointData(
                OPPONENT_CHALLENGE_BUTTON_X - OPPONENT_CHALLENGE_BUTTON_HALF_WIDTH,
                opponent.opponentY() - OPPONENT_CHALLENGE_BUTTON_HALF_HEIGHT);
        PointData bottomRight = new PointData(
                OPPONENT_CHALLENGE_BUTTON_X + OPPONENT_CHALLENGE_BUTTON_HALF_WIDTH,
                opponent.opponentY() + OPPONENT_CHALLENGE_BUTTON_HALF_HEIGHT);

        BufferedImage image = captureFrameForPixelScan("arena challenge button");
        if (image == null) {
            logWarning(String.format(
                    "Opponent %d challenge button color check failed. Treating button as disabled.",
                    opponent.number()));
            return false;
        }

        int saturatedBluePixels = countPixels(image, topLeft, bottomRight, GameColors::isArenaChallengeBlue);
        int greyPixels = countPixels(image, topLeft, bottomRight, GameColors::isArenaChallengeGrey);
        int darkPixels = countPixels(image, topLeft, bottomRight, GameColors::isArenaChallengeDark);
        logDebug(String.format(
                "Opponent %d challenge button colors - saturatedBlue: %d, grey: %d, dark: %d",
                opponent.number(), saturatedBluePixels, greyPixels, darkPixels));

        return saturatedBluePixels > scaleSampledThreshold(250) && saturatedBluePixels > greyPixels;
    }

    /** Execute actual 2026 Arena controls on live Serve-EMU, no blind Pause
     * or Retreat. A completed battle result returns to Challenge List by the
     * verified on-screen 'Tap anywhere to exit' footer. */
    private ArenaLiveFrame.Result executeServeEmuBattleSequence() {
        RawImageData formation=null;
        for(int check=0;check<14;check++){
            checkPreemption();
            RawImageData current=emuManager.captureScreen(EMULATOR_NUMBER);
            if(ArenaLiveFrame.formation(current)){formation=current;break;}
            sleepTask(150);
        }
        if(formation==null){
            logWarning("Arena challenge did not open the verified Offensive Formation Setup");
            return ArenaLiveFrame.Result.NONE;
        }
        boolean equalize=Boolean.TRUE.equals(profile.getConfig(
                ConfigurationKeyEnum.ARENA_TASK_ATTACK_QUICK_DEPLOY_BOOL,Boolean.class));
        if (equalize && ArenaLiveFrame.balanced(formation)) {
            // The current game may already show 33%/33%/33% on all three
            // green bars. Pressing Equalize again is redundant, and with the
            // new Balance control can be an unintended UI action.
            logInfo("Arena formation already balanced on all three troop bars; Equalize not required");
        } else if(equalize) {
            var button = ArenaLiveFrame.equalize(formation);
            if(!button.isFound()) {
                logWarning("Arena Equalize not recognized on verified formation; no blind action");
                return ArenaLiveFrame.Result.NONE;
            }
            sleepTask(170);
            var fresh=emuManager.captureScreen(EMULATOR_NUMBER);
            var confirm=ArenaLiveFrame.equalize(fresh);
            if (!dev.frostguard.engine.helper.RecognitionConsensus.agrees(button,confirm,12)) {
                logWarning("Arena Equalize shifted/unconfirmed between two fresh frames; no tap");
                return ArenaLiveFrame.Result.NONE;
            }
            tapNear(confirm.getPoint());
            sleepTask(260);
            formation=emuManager.captureScreen(EMULATOR_NUMBER);
            if(!ArenaLiveFrame.balanced(formation)){
                logWarning("Arena Equalize tapped but three troop allocation bars not confirmed");
                return ArenaLiveFrame.Result.UNKNOWN;
            }
        }
        var fight=ArenaLiveFrame.fight(formation);
        if(!fight.isFound()){
            logWarning("Arena Fight action not found on current offensive formation");
            return ArenaLiveFrame.Result.NONE;
        }
        tapNear(fight.getPoint());
        logInfo("Arena Fight tapped after verified formation; awaiting actual result instead of blind Pause/Retreat");
        for(int attempt=0;attempt<75;attempt++){
            checkPreemption();
            RawImageData current=emuManager.captureScreen(EMULATOR_NUMBER);
            var state=ArenaLiveFrame.outcome(current);
            if(state==ArenaLiveFrame.Result.VICTORY || state==ArenaLiveFrame.Result.DEFEAT){
                sleepTask(140);
                RawImageData second=emuManager.captureScreen(EMULATOR_NUMBER);
                if(ArenaLiveFrame.outcome(second)!=state)continue;
                tapNear(new PointData(365,1216)); // actual blank exit footer
                for(int settle=0;settle<12;settle++){
                    sleepTask(160);
                    if(ArenaLiveFrame.challengeList(emuManager.captureScreen(EMULATOR_NUMBER))){
                        logInfo("Arena "+state+" confirmed and Challenge List returned");
                        return state;
                    }
                }
                logWarning("Arena "+state+" displayed but Challenge List did not return after exit");
                return ArenaLiveFrame.Result.UNKNOWN;
            }
            sleepTask(200);
        }
        logWarning("Arena battle result never appeared; no blind Back/Retreat");
        return ArenaLiveFrame.Result.NONE;
    }

    void executeBattleSequence() {
        logInfo("Executing battle sequence (with animation skip)");

        boolean useQuickDeploy = profile.getConfig(
                ConfigurationKeyEnum.ARENA_TASK_ATTACK_QUICK_DEPLOY_BOOL, Boolean.class);
        if (useQuickDeploy) {
            logInfo("Tapping Quick Deploy for the Arena attack.");
            tapNear(QUICK_DEPLOY_BUTTON);
            sleepTask(500);
        } else {
            logInfo("Arena attack Quick Deploy is disabled. Keeping the saved attack formation.");
        }

        tapNear(BATTLE_START_BUTTON);
        sleepTask(3000);

        tapNear(BATTLE_PAUSE_BUTTON);
        sleepTask(500);

        tapNear(BATTLE_RETREAT_BUTTON);
        sleepTask(1000);
    }

    private boolean checkBattleResult() {
        ImageSearchResultData victoryResult = templateSearchHelper.locatePattern(
                TemplatesEnum.ARENA_VICTORY,
                SearchConfig.builder()
                        .withArea(BATTLE_RESULT_AREA)
                        .withMaxAttempts(3)
                        .withDelay(250L)
                        .withThreshold(85)
                        .build());

        if (victoryResult.isFound()) {
            logInfo("Battle result: Victory");
            sleepTask(1000);
            pressBack();
            return true;
        }

        logInfo("Battle result: Defeat");
        sleepTask(1000);
        pressBack();
        return false;
    }

    private int buyExtraAttempts() {
        if (!dev.frostguard.tasks.security.TaskGemSpendPolicy.allowed(
                profile, TpDailyTaskEnum.ARENA)
                || !arenaPaidActionsAllowed(emuManager.getSelectedEmulatorType())) {
            logInfo("Arena paid attempts denied by per-task permission or emulator policy");
            return 0;
        }
        logDebug("Opening extra attempts purchase dialog");
        tapNear(EXTRA_ATTEMPTS_BUTTON);
        sleepTask(1000);

        ImageSearchResultData confirmResult = templateSearchHelper.locatePattern(
                TemplatesEnum.ARENA_GEMS_EXTRA_ATTEMPTS_BUTTON,
                SearchConfig.builder().build());

        if (!confirmResult.isFound()) {
            logInfo("No more extra attempts available for purchase");
            return 0;
        }

        logDebug("Resetting purchase counter to zero");
        swipe(PURCHASE_COUNTER_SWIPE_START, PURCHASE_COUNTER_SWIPE_END);
        sleepTask(300);

        int previousAttempts = detectCurrentAttemptPosition();
        if (previousAttempts < 0) {
            pressBack();
            return 0;
        }

        int remainingAttempts = calculateRemainingAttemptsToBuy(previousAttempts);
        if (remainingAttempts <= 0) {
            pressBack();
            return 0;
        }

        int expectedPrice = calculateTotalPrice(previousAttempts, remainingAttempts);
        logInfo(String.format("Buying %d attempts for %d gems (already have %d)",
                remainingAttempts, expectedPrice, previousAttempts));

        if (remainingAttempts > 1) {
            tapInside(
                    PURCHASE_COUNTER_INCREMENT_TOP_LEFT,
                    PURCHASE_COUNTER_INCREMENT_BOTTOM_RIGHT,
                    remainingAttempts - 1,
                    400);
            sleepTask(300);
        }

        if (!dev.frostguard.tasks.security.TaskGemSpendPolicy.allowed(
                profile, TpDailyTaskEnum.ARENA)) {
            logWarning("Arena diamond permission switched off before purchase confirmation; aborting");
            pressBack();
            return 0;
        }
        tapNear(PURCHASE_CONFIRM_BUTTON);
        sleepTask(500);

        StatisticsService.obtain().addToCounter(profile, "Arena Gems Spent", expectedPrice);
        return remainingAttempts;
    }

    private int detectCurrentAttemptPosition() {
        logDebug("Detecting current attempt position via price");

        OcrSettingsData configMap = OcrSettingsData.assembler()
                .stripBackground(true)
                .setTextColor(new Color(255, 255, 255))
                .charWhitelist("0123456789")
                .build();

        Integer singleAttemptPrice = readNumberValue(
                PURCHASE_PRICE_TOP_LEFT,
                PURCHASE_PRICE_BOTTOM_RIGHT,
                configMap);

        if (singleAttemptPrice == null) {
            logWarning("Failed to read single attempt price");
            return -1;
        }

        for (int i = 0; i < ATTEMPT_PRICES.length; i++) {
            if (ATTEMPT_PRICES[i] == singleAttemptPrice) {
                logDebug(String.format("Detected position %d (price: %d gems)", i, singleAttemptPrice));
                return i;
            }
        }

        logWarning(String.format("Unexpected attempt price: %d gems", singleAttemptPrice));
        return -1;
    }

    private int calculateRemainingAttemptsToBuy(int previousAttempts) {
        if (previousAttempts >= extraAttempts) {
            logInfo(String.format("Already have %d attempts (wanted %d), no need to buy more",
                    previousAttempts, extraAttempts));
            return 0;
        }

        int canBuy = ATTEMPT_PRICES.length - previousAttempts;
        int wantToBuy = extraAttempts - previousAttempts;
        int toBuy = Math.min(canBuy, wantToBuy);

        if (toBuy <= 0) {
            logWarning("Cannot purchase any more attempts (max limit reached)");
            return 0;
        }

        return toBuy;
    }

    private int calculateTotalPrice(int startPosition, int count) {
        int totalPrice = 0;
        for (int i = startPosition; i < startPosition + count; i++) {
            totalPrice += ATTEMPT_PRICES[i];
        }
        return totalPrice;
    }

    private boolean tryRefreshOpponentList() {
        if (freeRefreshCount >= MAX_FREE_REFRESHES) {
            logInfo(String.format("Free list refresh limit reached (%d/%d)",
                    freeRefreshCount, MAX_FREE_REFRESHES));
        } else {
            ImageSearchResultData freeRefreshResult = templateSearchHelper.locatePattern(
                    TemplatesEnum.ARENA_FREE_REFRESH_BUTTON,
                    TRANSIENT_BUTTON_SEARCH);

            if (freeRefreshResult.isFound()) {
                freeRefreshCount++;
                logInfo(String.format("Using free refresh (%d/%d)", freeRefreshCount, MAX_FREE_REFRESHES));
                tapInside(freeRefreshResult);
                sleepTask(1000);
                attemptedOpponentSlots.clear();
                StatisticsService.obtain().addToCounter(profile, "Arena Refreshes", 1);
                return true;
            }
        }

        if (!refreshWithGems || !dev.frostguard.tasks.security.TaskGemSpendPolicy.allowed(
                profile, TpDailyTaskEnum.ARENA)
                || !arenaPaidActionsAllowed(emuManager.getSelectedEmulatorType())) {
            logDebug("Paid arena list refresh disabled by per-task diamond permission or emulator policy");
            return false;
        }

        if (gemRefreshCount >= MAX_GEM_REFRESHES) {
            logInfo(String.format("Gem list refresh limit reached (%d/%d)",
                    gemRefreshCount, MAX_GEM_REFRESHES));
            return false;
        }

        ImageSearchResultData gemsRefreshResult = templateSearchHelper.locatePattern(
                TemplatesEnum.ARENA_GEMS_REFRESH_BUTTON,
                TRANSIENT_BUTTON_SEARCH);

        if (!gemsRefreshResult.isFound()) {
            logDebug("Gem list refresh button not found");
            return false;
        }

        logInfo(String.format("Opening gem list refresh (%d/%d)", gemRefreshCount + 1, MAX_GEM_REFRESHES));

        tapInside(gemsRefreshResult);
        sleepTask(500);

        ImageSearchResultData confirmResult = templateSearchHelper.locatePattern(
                TemplatesEnum.ARENA_GEMS_REFRESH_CONFIRM_BUTTON,
                SearchConfig.builder().build());

        if (confirmResult.isFound()) {
            gemRefreshCount++;
            logInfo(String.format("Confirming gem list refresh (%d/%d)", gemRefreshCount, MAX_GEM_REFRESHES));
            if (!dev.frostguard.tasks.security.TaskGemSpendPolicy.allowed(
                    profile, TpDailyTaskEnum.ARENA)) {
                logWarning("Arena diamond permission OFF before refresh confirmation; canceling");
                pressBack();
                return false;
            }
            tapInside(confirmResult);
            sleepTask(1000);
            attemptedOpponentSlots.clear();
            StatisticsService.obtain().addToCounter(profile, "Arena Refreshes", 1);
            return true;
        }

        gemRefreshCount++;
        logInfo(String.format(
                "Gem list refresh confirmation not shown; assuming refresh was accepted (%d/%d)",
                gemRefreshCount, MAX_GEM_REFRESHES));
        sleepTask(1000);
        attemptedOpponentSlots.clear();
        StatisticsService.obtain().addToCounter(profile, "Arena Refreshes", 1);
        return true;
    }

    private boolean isValidTimeFormat(String time) {
        if (time == null || time.trim().isEmpty()) {
            return false;
        }

        try {
            String[] parts = time.split(":");
            if (parts.length != 2) {
                return false;
            }

            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);

            return hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 55;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void rescheduleWithActivationHour() {
        if (!isValidTimeFormat(activationTime)) {
            logInfo("Rescheduling Arena task for 5 min before game reset time (invalid activation time)");
            reschedule(GameTimeUtils.dailyResetTime().minusMinutes(5));
            return;
        }

        try {
            String[] timeParts = activationTime.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);

            ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
            ZonedDateTime tomorrowActivationUtc = nowUtc.toLocalDate()
                    .plusDays(1)
                    .atTime(hour, minute)
                    .atZone(ZoneId.of("UTC"));

            ZonedDateTime localActivationTime = tomorrowActivationUtc.withZoneSameInstant(
                    ZoneId.systemDefault());

            logInfo(String.format("Rescheduling Arena task for %s UTC tomorrow (%s local)",
                    activationTime,
                    localActivationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

            reschedule(localActivationTime.toLocalDateTime());

        } catch (Exception e) {
            logError("Failed to reschedule with activation time: " + e.getMessage());
            logInfo("Falling back to 5 min before game reset time");
            reschedule(GameTimeUtils.dailyResetTime().minusMinutes(5));
        }
    }

    private void rescheduleForToday() {
        if (!isValidTimeFormat(activationTime)) {
            logInfo("Rescheduling Arena task for 5 min before game reset time (invalid activation time)");
            reschedule(GameTimeUtils.dailyResetTime().minusMinutes(5));
            return;
        }

        try {
            String[] timeParts = activationTime.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);

            ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
            ZonedDateTime todayActivationUtc = nowUtc.toLocalDate()
                    .atTime(hour, minute)
                    .atZone(ZoneId.of("UTC"));

            ZonedDateTime localActivationTime = todayActivationUtc.withZoneSameInstant(
                    ZoneId.systemDefault());

            logInfo(String.format("Rescheduling Arena task for %s UTC today (%s local)",
                    activationTime,
                    localActivationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

            reschedule(localActivationTime.toLocalDateTime());

        } catch (Exception e) {
            logError("Failed to reschedule for today's activation time: " + e.getMessage());
            logInfo("Falling back to 5 min before game reset time");
            reschedule(GameTimeUtils.dailyResetTime().minusMinutes(5));
        }
    }

    private String normalizeAllianceTag(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeServer(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

    @Override
    protected LaunchPoint getRequiredStartLocation() {
        return LaunchPoint.HOME;
    }

    @Override
    public boolean provideDailyMissionProgress() {
        return true;
    }

    private enum PowerRelation {
        WEAKER("WEAKER", ""),
        STRONGER("STRONGER", "stronger power"),
        UNKNOWN("UNKNOWN", "power unreadable");

        private final String label;
        private final String skipReason;

        PowerRelation(String label, String skipReason) {
            this.label = label;
            this.skipReason = skipReason;
        }
    }

    private enum ChallengeOutcome {
        VICTORY,
        DEFEAT,
        UNKNOWN,
        NO_ATTEMPTS
    }

    private enum OpponentLayout {
        SERVER_ROW("server", POWER_TEXT_SERVER_LAYOUT_Y_OFFSET, POWER_TEXT_SERVER_LAYOUT_HEIGHT, true),
        COMPACT("compact", POWER_TEXT_COMPACT_LAYOUT_Y_OFFSET, POWER_TEXT_COMPACT_LAYOUT_HEIGHT, false),
        UNKNOWN("unknown", POWER_TEXT_SERVER_LAYOUT_Y_OFFSET, POWER_TEXT_SERVER_LAYOUT_HEIGHT, true);

        private final String label;
        private final int powerYOffset;
        private final int powerHeight;
        private final boolean serverVisible;

        OpponentLayout(String label, int powerYOffset, int powerHeight, boolean serverVisible) {
            this.label = label;
            this.powerYOffset = powerYOffset;
            this.powerHeight = powerHeight;
            this.serverVisible = serverVisible;
        }

        private int powerYOffset() {
            return powerYOffset;
        }

        private int powerHeight() {
            return powerHeight;
        }

        private boolean serverVisible() {
            return serverVisible;
        }
    }

    private record PowerRead(PowerRelation relation, Double value, String rawText) {
        private static PowerRead withoutValue(PowerRelation relation) {
            return new PowerRead(relation, null, null);
        }

        private String logValue() {
            if (value == null) {
                return relation.label;
            }
            return String.format(Locale.ROOT, "%s(%.0f)", relation.label, value);
        }
    }

    private enum AllianceStatus {
        READ,
        MALFORMED,
        NO_ALLIANCE,
        GUARD,
        UNREADABLE,
        NOT_CHECKED
    }

    private enum ServerStatus {
        READ,
        NOT_SHOWN,
        UNREADABLE,
        NOT_CHECKED
    }

    private enum AlliancePolicy {
        ANY("Any alliance"),
        AVOID_PROFILE_ALLIANCE("Avoid profile alliance"),
        NEVER_PROFILE_ALLIANCE("Never attack profile alliance");

        private final String displayName;

        AlliancePolicy(String displayName) {
            this.displayName = displayName;
        }

        private static AlliancePolicy fromConfig(String value) {
            if (value == null || value.isBlank()) {
                return DEFAULT_ALLIANCE_POLICY;
            }
            for (AlliancePolicy policy : values()) {
                if (policy.displayName.equalsIgnoreCase(value.trim()) || policy.name().equalsIgnoreCase(value.trim())) {
                    return policy;
                }
            }
            return DEFAULT_ALLIANCE_POLICY;
        }

        private static boolean isDefaultConfig(String value) {
            return value == null
                    || value.isBlank()
                    || DEFAULT_ALLIANCE_POLICY.displayName.equalsIgnoreCase(value.trim())
                    || DEFAULT_ALLIANCE_POLICY.name().equalsIgnoreCase(value.trim());
        }

        private Comparator<OpponentCandidate> comparator(String profileAllianceTag) {
            return switch (this) {
                case AVOID_PROFILE_ALLIANCE -> Comparator
                        .comparingInt((OpponentCandidate candidate) -> candidate.allianceRisk(profileAllianceTag));
                default -> (left, right) -> 0;
            };
        }
    }

    private enum ServerPolicy {
        ANY("Any server"),
        PREFER_PROFILE_SERVER("Prefer profile server"),
        AVOID_PROFILE_SERVER("Avoid profile server"),
        NEVER_PROFILE_SERVER("Never attack profile server");

        private final String displayName;

        ServerPolicy(String displayName) {
            this.displayName = displayName;
        }

        private static ServerPolicy fromConfig(String value) {
            if (value == null || value.isBlank()) {
                return ANY;
            }
            for (ServerPolicy policy : values()) {
                if (policy.displayName.equalsIgnoreCase(value.trim()) || policy.name().equalsIgnoreCase(value.trim())) {
                    return policy;
                }
            }
            return ANY;
        }

        private Comparator<OpponentCandidate> comparator(String profileServer) {
            return switch (this) {
                case PREFER_PROFILE_SERVER -> Comparator
                        .comparing((OpponentCandidate candidate) -> !candidate.matchesServer(profileServer));
                case AVOID_PROFILE_SERVER -> Comparator
                        .comparing((OpponentCandidate candidate) -> candidate.matchesServer(profileServer));
                default -> (left, right) -> 0;
            };
        }
    }

    private record AllianceRead(String tag, AllianceStatus status, String detail) {
        private static AllianceRead notChecked() {
            return new AllianceRead(null, AllianceStatus.NOT_CHECKED, null);
        }

        private static AllianceRead read(String tag) {
            return new AllianceRead(tag, AllianceStatus.READ, null);
        }

        private static AllianceRead malformed(String tag, String detail) {
            return new AllianceRead(tag, AllianceStatus.MALFORMED, detail);
        }

        private static AllianceRead noAlliance() {
            return new AllianceRead(null, AllianceStatus.NO_ALLIANCE, null);
        }

        private static AllianceRead guard() {
            return new AllianceRead(null, AllianceStatus.GUARD, null);
        }

        private static AllianceRead unreadable(String detail) {
            return new AllianceRead(null, AllianceStatus.UNREADABLE, detail);
        }

        private String logValue() {
            return switch (status) {
                case READ -> tag;
                case MALFORMED -> tag + " (malformed" + (detail == null ? "" : ": " + detail) + ")";
                case NO_ALLIANCE -> "no alliance";
                case GUARD -> "guard";
                case UNREADABLE -> "unreadable" + (detail == null ? "" : ": " + detail);
                case NOT_CHECKED -> "not checked";
            };
        }
    }

    private record ServerRead(String value, ServerStatus status) {
        private static ServerRead notChecked() {
            return new ServerRead(null, ServerStatus.NOT_CHECKED);
        }

        private String logValue() {
            return switch (status) {
                case READ -> value;
                case NOT_SHOWN -> "not shown";
                case UNREADABLE -> "unreadable";
                case NOT_CHECKED -> "not checked";
            };
        }
    }

    private record OpponentCandidate(int number, int opponentY, PowerRead power, AllianceRead alliance,
                                     ServerRead server, boolean eligible, String decision) {
        private static OpponentCandidate eligible(int number, int opponentY, PowerRead power,
                                                  AllianceRead alliance, ServerRead server) {
            return new OpponentCandidate(number, opponentY, power, alliance, server, true, "eligible");
        }

        private static OpponentCandidate skipped(int number, int opponentY, PowerRead power,
                                                 AllianceRead alliance, ServerRead server, String reason) {
            return new OpponentCandidate(number, opponentY, power, alliance, server, false, "skip:" + reason);
        }

        private boolean matchesServer(String profileServer) {
            return profileServer != null && server.status == ServerStatus.READ && profileServer.equals(server.value);
        }

        private boolean matchesAlliance(String profileAllianceTag) {
            return profileAllianceTag != null
                    && (alliance.status == AllianceStatus.READ || alliance.status == AllianceStatus.MALFORMED)
                    && profileAllianceTag.equals(alliance.tag);
        }

        private int allianceRisk(String profileAllianceTag) {
            if (matchesAlliance(profileAllianceTag)) {
                return 2;
            }
            if (alliance.status == AllianceStatus.UNREADABLE || alliance.status == AllianceStatus.NOT_CHECKED) {
                return 1;
            }
            return 0;
        }

        private String selectionSummary() {
            return String.format("power=%s alliance=%s server=%s",
                    power.logValue(), alliance.logValue(), server.logValue());
        }
    }
}
