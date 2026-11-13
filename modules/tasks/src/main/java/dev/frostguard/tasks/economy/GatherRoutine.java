package dev.frostguard.tasks.economy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.frostguard.vision.convert.GameTimeUtils;
import dev.frostguard.vision.ocr.ResilientOcrExecutor;
import dev.frostguard.data.entity.DailyTask;
import dev.frostguard.data.repository.DailyTaskRepository;
import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.MarchActivityType;
import dev.frostguard.api.domain.MarchMovementPhase;
import dev.frostguard.api.domain.MarchResourceType;
import dev.frostguard.api.domain.MarchSlotAvailability;
import dev.frostguard.api.domain.MarchSlotState;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.api.runtime.WorkspacePaths;
import dev.frostguard.engine.helper.MarchSlotAvailabilityEstimator;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.GatherQueuePolicy;
import dev.frostguard.engine.error.TaskOutcomeUnknownException;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.engine.schedule.TaskQueue;
import dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig;
import dev.frostguard.engine.service.StatisticsService;

import javax.imageio.ImageIO;

/**
 * Optimized GatherRoutine: Manages persistent resource rotation, fairness, and
 * efficient queue utilization.
 */
public class GatherRoutine extends DelayedTask {

    // ========== Constants & Config Keys ==========
    private static final int DEFAULT_QUEUES = 6;
    private static final int DEFAULT_LEVEL = 5;
    private static final boolean DEFAULT_REMOVE_HEROES = false;
    private static final boolean DEFAULT_INTEL_SMART = false;
    private static final int PENDING_HIGH_PRIORITY_RETRY_MINUTES = 5;
    // pernerch/2026-07-02: lookahead for dual-event detection (Intel + Bear within this window → defer both)
    private static final int DUAL_EVENT_LOOKAHEAD_MINUTES = 15;
    // pernerch/2026-07-02: initial margin added after max march return time before re-deploying
    private static final int TROOP_RETURN_MARGIN_MINUTES = 2;
    // pernerch/2026-07-02: retry interval when recalled troops are still marching home
    private static final int TROOP_RETURN_RETRY_MINUTES = 1;
    // pernerch/2026-07-02: Bear Trap active duration (30 min) used to estimate end time for defer calculation
    private static final int BEAR_TRAP_DURATION_MINUTES = 30;
    private static final int LOWER_BOUND_RECHECK_BUFFER_MINUTES = 1;
    private static final int GATHER_RETURN_BUFFER_MINUTES = 5;
    private static final int UNKNOWN_MARCH_RETRY_MINUTES = 5;
    private static final int STATIONED_MARCH_RETRY_MINUTES = 60;
    private static final MarchSlotAvailabilityEstimator.Settings MARCH_SLOT_ESTIMATE_SETTINGS =
            new MarchSlotAvailabilityEstimator.Settings(
                    Duration.ofMinutes(LOWER_BOUND_RECHECK_BUFFER_MINUTES),
                    Duration.ofMinutes(GATHER_RETURN_BUFFER_MINUTES),
                    Duration.ofMinutes(UNKNOWN_MARCH_RETRY_MINUTES),
                    Duration.ofMinutes(STATIONED_MARCH_RETRY_MINUTES));

    // Region Constants (UI)
    private static final MarchQueueRegion[] MARCH_QUEUES = {
            new MarchQueueRegion(new PointData(10, 342), new PointData(435, 407)),
            new MarchQueueRegion(new PointData(10, 415), new PointData(435, 480)),
            new MarchQueueRegion(new PointData(10, 488), new PointData(435, 553)),
            new MarchQueueRegion(new PointData(10, 561), new PointData(435, 626)),
            new MarchQueueRegion(new PointData(10, 634), new PointData(435, 699)),
            new MarchQueueRegion(new PointData(10, 707), new PointData(435, 772)),
    };

    // PointData Constants (UI)
    private static final PointData SEARCH_BTN_TL = new PointData(25, 850);
    private static final PointData SEARCH_BTN_BR = new PointData(67, 898);
    private static final PointData RES_TAB_SWIPE_START = new PointData(678, 913);
    private static final PointData RES_TAB_SWIPE_END = new PointData(40, 913);
    private static final PointData LEVEL_DISPLAY_TL = new PointData(78, 991);
    private static final PointData LEVEL_DISPLAY_BR = new PointData(474, 1028);
    private static final PointData LEVEL_SLIDER_START = new PointData(435, 1052);
    private static final PointData LEVEL_SLIDER_END = new PointData(40, 1052);
    private static final PointData LEVEL_INC_TL = new PointData(470, 1040);
    private static final PointData LEVEL_INC_BR = new PointData(500, 1066);
    private static final PointData LEVEL_DEC_TL = new PointData(50, 1040);
    private static final PointData LEVEL_DEC_BR = new PointData(85, 1066);
    private static final PointData ONLY_FULL_RESOURCES_TOGGLE = new PointData(183, 1140);
    private static final AreaData ONLY_FULL_RESOURCES_TICK_AREA =
            new AreaData(new PointData(145, 1110), new PointData(225, 1170));
    private static final PointData SEARCH_EXEC_TL = new PointData(301, 1200);
    private static final PointData SEARCH_EXEC_BR = new PointData(412, 1229);
    private static final PointData RECALL_CONFIRM_TL = new PointData(446, 780);
    private static final PointData RECALL_CONFIRM_BR = new PointData(578, 800);

    private final DailyTaskRepository dailyTaskRepository = DailyTaskRepository.getRepository();

    // ========== State & Configuration ==========
    private int activeQueues;
    private boolean removeHeroes;
    private boolean noHeroFallback;
    private boolean intelSmart;
    private boolean intelRecall;
    private boolean intelEnabled;
    private boolean gatherSpeed;
    private boolean autoJoinEnabled;
    private boolean onlyFullResources;
    private boolean downgradeLevelOnMissingNode;

    private List<GatherType> enabledTypes;
    private List<GatherType> rotationPool;
    private LocalDateTime earliestReschedule;
    private ResilientOcrExecutor<LocalDateTime> textHelper;
    private boolean confirmedDailyMissionProgress;
    // pernerch/2026-07-02: stored per-profile task instance (one GatherRoutine per profile).
    // Records when gather troops were recalled by Intel or Bear Trap so we can wait for them
    // to return before re-deploying. Also persisted to profile config for crash recovery.
    private LocalDateTime lastRecallTime;

    public GatherRoutine(AccountDescriptor profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    // ================= EXECUTE =================

    @Override
    protected void execute() {
        confirmedDailyMissionProgress = false;
        loadConfig();

        if (enabledTypes.isEmpty()) {
            logInfo("No gather types enabled. Disabling task.");
            setRecurring(false);
            return;
        }

        // pernerch/2026-07-02: after an Intel/Bear recall, wait until troops are home before re-deploying.
        // Checks the per-profile recall timestamp and extends by 1 minute if troops are still out.
        if (checkTroopReturnPending())
            return;

        // pernerch/2026-07-02: replaces blind 35-min reschedule with smart dual-event (Intel+Bear)
        // awareness, actual march recall, and defer calculation based on real event end times.
        if (checkHighPriorityEventConflict())
            return;
        if (checkGatherSpeedWait())
            return;
        // 1. Read the shared March Queue model. A lower runtime queue limit constrains future
        // deployments; it must not recall marches that were valid under the previous config.
        GatherMarchSnapshot marchSnapshot = readGatherMarchSnapshot();
        List<GatherType> activeMarches = new ArrayList<>(marchSnapshot.activeTypes());
        int activeGatherCount = marchSnapshot.activeGatherCount();
        int idleSlotCount = marchSnapshot.idleSlotCount();
        logInfo(String.format("Active gather marches: %d / %d; idle physical march slots: %d",
                activeGatherCount, activeQueues, idleSlotCount));

        // Changed by pernerch | Date: 2026-07-02 | Why: when higher-priority tasks are pending,
        // defer based on real active-march timing instead of a blind fixed delay.
        List<TpDailyTaskEnum> pendingHigherPriorityTasks = GatherQueuePolicy.getPendingHigherPriorityMarchTasks(profile);
        if (!pendingHigherPriorityTasks.isEmpty()) {
            if (pendingHigherPriorityTasks.contains(TpDailyTaskEnum.INTEL) && triggerPendingIntelNowFlow()) {
                return;
            }

            if (activeGatherCount > 0) {
                LocalDateTime next = earliestReschedule != null
                        ? earliestReschedule
                        : marchSnapshot.nextCheckAt() != null ? marchSnapshot.nextCheckAt() : LocalDateTime.now().plusMinutes(5);
                logInfo(String.format(
                "Deferring gather deployment because higher-priority march task(s) are pending: %s. " +
                    "%d gather march(es) are outside; next return at %s.",
                pendingHigherPriorityTasks,
                        activeGatherCount,
                        GameTimeUtils.formatCountdown(next)));
                reschedule(next);
            } else {
            LocalDateTime retryAt = LocalDateTime.now().plusMinutes(PENDING_HIGH_PRIORITY_RETRY_MINUTES);
            logInfo(String.format(
                "Deferring gather deployment because higher-priority march task(s) are pending: %s. " +
                    "No active gather marches are outside; retrying in %d minutes at %s to avoid noisy rechecks.",
                pendingHigherPriorityTasks,
                PENDING_HIGH_PRIORITY_RETRY_MINUTES,
                GameTimeUtils.formatCountdown(retryAt)));
            reschedule(retryAt);
            }
            return;
        }

        if (idleSlotCount <= 0 && activeGatherCount < activeQueues) {
            if (!autoJoinEnabled) {
                int recalledBlockedMarches = recallBlockedMarchesWhenAutojoinOffFlow();
                if (recalledBlockedMarches > 0) {
                    LocalDateTime retryAt = LocalDateTime.now().plusMinutes(1);
                    logInfo(String.format(
                            "Autojoin is disabled and all physical march slots are blocked. Recalled %d march(es); rechecking gather in 1 minute at %s.",
                            recalledBlockedMarches,
                            GameTimeUtils.formatCountdown(retryAt)));
                    reschedule(retryAt);
                    return;
                }
            }

            LocalDateTime retryAt = marchSnapshot.nextCheckAt() != null
                    ? marchSnapshot.nextCheckAt()
                    : LocalDateTime.now().plusMinutes(UNKNOWN_MARCH_RETRY_MINUTES);
            logInfo(String.format(
                    "No idle physical march slot is available for gather. Rechecking at %s.",
                    GameTimeUtils.formatCountdown(retryAt)));
            reschedule(retryAt);
            return;
        }

        if (hasReachedConfiguredQueueLimit(activeGatherCount, activeQueues) && earliestReschedule == null) {
            // Only fall back to 5-min polling if the next check is near or unknown.
            LocalDateTime nextGatherCheck = marchSnapshot.earliestGatherCheckAt();
            LocalDateTime retryAt;
            if (nextGatherCheck != null && ChronoUnit.MINUTES.between(LocalDateTime.now(), nextGatherCheck) > 10) {
                retryAt = nextGatherCheck.plusMinutes(TROOP_RETURN_MARGIN_MINUTES);
                logInfo(String.format(
                        "All configured gather queues are currently active%s. " +
                        "Scheduling next gather check at %s.",
                        autoJoinEnabled ? " (autojoin enabled)" : " (autojoin disabled)",
                        GameTimeUtils.formatCountdown(retryAt)));
            } else {
                retryAt = LocalDateTime.now().plusMinutes(5);
                logInfo(String.format(
                        "All configured gather queues are currently active%s. " +
                        "Next check is near or unknown - retrying in 5 minutes at %s.",
                        autoJoinEnabled ? " (autojoin enabled)" : " (autojoin disabled)",
                        GameTimeUtils.formatCountdown(retryAt)));
            }
            reschedule(retryAt);
            return;
        }

        // 2. Fill Queues (Persistent Rotation)
        GatherFillResult fillResult = fillQueues(activeGatherCount, idleSlotCount, activeMarches);

        // A dispatch is not proof that a march actually left. Compare the March Queue before and after.
        if (fillResult.deployed() > 0) {
            GatherMarchSnapshot verifiedSnapshot = readGatherMarchSnapshot();
            int confirmedDeployments = countConfirmedNewGatherMarches(
                    marchSnapshot.slots(), verifiedSnapshot.slots());
            confirmedDailyMissionProgress = confirmedDeployments > 0;
            if (confirmedDeployments < fillResult.deployed()) {
                LocalDateTime retryAt = LocalDateTime.now().plusMinutes(UNKNOWN_MARCH_RETRY_MINUTES);
                logWarning(String.format(
                        "Gather dispatch verification incomplete: dispatched=%d confirmed=%d. "
                                + "No repeat tap; rechecking March Queue at %s.",
                        fillResult.deployed(), confirmedDeployments, GameTimeUtils.formatCountdown(retryAt)));
                reschedule(retryAt);
                if (confirmedDeployments == 0) {
                    throw new TaskOutcomeUnknownException(
                            "Gather deployment could not be confirmed; bounded March Queue retry scheduled without recording completion.");
                }
                return;
            }
            logInfo("Confirmed " + confirmedDeployments + " new gather march deployment(s) in March Queue.");
        }

        // 3. Save & Finalize
        finalizeReschedule(fillResult);
    }

    // ================= CONFIGURATION =================

    private void loadConfig() {
        // Changed by pernerch | Date: 2026-07-02 | Why: centralize queue limit via policy for consistent hard-cap behavior.
        this.activeQueues = GatherQueuePolicy.resolveActiveQueueLimit(
                get(ConfigurationKeyEnum.GATHER_ACTIVE_MARCH_QUEUE_INT, DEFAULT_QUEUES));
        this.removeHeroes = get(ConfigurationKeyEnum.GATHER_REMOVE_HEROS_BOOL, DEFAULT_REMOVE_HEROES);
        this.noHeroFallback = get(ConfigurationKeyEnum.GATHER_NO_HERO_FALLBACK_BOOL, false);
        this.intelSmart = get(ConfigurationKeyEnum.INTEL_SMART_PROCESSING_BOOL, DEFAULT_INTEL_SMART);
        this.intelRecall = get(ConfigurationKeyEnum.INTEL_RECALL_GATHER_TROOPS_BOOL, false);
        this.intelEnabled = get(ConfigurationKeyEnum.INTEL_BOOL, false);
        this.gatherSpeed = get(ConfigurationKeyEnum.GATHER_SPEED_BOOL, false);
        this.autoJoinEnabled = get(ConfigurationKeyEnum.ALLIANCE_AUTOJOIN_BOOL, false);
        this.onlyFullResources = get(ConfigurationKeyEnum.GATHER_ONLY_FULL_RESOURCES_BOOL, false);
        this.downgradeLevelOnMissingNode = get(ConfigurationKeyEnum.GATHER_DOWNGRADE_LEVEL_BOOL, true);

        this.enabledTypes = Arrays.stream(GatherType.values())
                .filter(this::isTypeEnabled)
                .collect(Collectors.toList());

        loadRotationPool();
        if (rotationPool != null) {
            rotationPool.retainAll(enabledTypes);
            saveRotationPool(); // Ensure consistent state
        }

        this.textHelper = new ResilientOcrExecutor<>(provider);
        this.earliestReschedule = null;
        // pernerch/2026-07-02: restore recall timestamp from profile config so it survives task restarts.
        String recallTimeStr = profile.getConfig(ConfigurationKeyEnum.GATHER_LAST_RECALL_TIME_STRING, String.class);
        if (recallTimeStr != null && !recallTimeStr.isEmpty()) {
            try { this.lastRecallTime = LocalDateTime.parse(recallTimeStr); }
            catch (Exception ignored) { this.lastRecallTime = null; }
        }
    }

    private boolean isTypeEnabled(GatherType type) {
        return get(type.enabledKey, false);
    }

    @SuppressWarnings("unchecked")
    private <T> T get(ConfigurationKeyEnum key, T defaultValue) {
        T val = profile.getConfig(key, (Class<T>) defaultValue.getClass());
        return val != null ? val : defaultValue;
    }

    // ================= ROTATION LOGIC =================

    private GatherFillResult fillQueues(int currentActive, int idleSlotCount, List<GatherType> activeMarches) {
        int freeSlots = Math.min(activeQueues - currentActive, idleSlotCount);
        int deployed = 0;
        int noNode = 0;
        int blocked = 0;
        int sameTargetBlocked = 0;
        Set<GatherType> unavailableThisRun = new HashSet<>();
        logInfo(String.format("Gather fill: active=%d/%d idleSlots=%d freeSlots=%d pool=%s",
                currentActive, activeQueues, idleSlotCount, freeSlots, rotationPool));

        // Remove types already marching from the current pool for initial fairness
        if (rotationPool.removeAll(activeMarches)) {
            logDebug("Removed active gather types from pool: " + activeMarches);
            saveRotationPool();
        }

        if (freeSlots <= 0) {
            saveRotationPool();
            return new GatherFillResult(0, 0, 0, 0, false, 0);
        }

        // Shuffle loaded pool for randomness in this run
        Collections.shuffle(rotationPool);

        int remaining = freeSlots;
        int safetyLoop = 0;

        while (remaining > 0 && safetyLoop++ < 10) {

            // Refill if empty
            if (rotationPool.isEmpty()) {
                if (!refillRotationPool(activeMarches, unavailableThisRun)) {
                    break;
                }
                Collections.shuffle(rotationPool);
            }

            // Try ALL pool items â€” don't limit to remaining, so if one type fails
            // we still try others. The inner loop stops when slots are full.
            List<GatherType> batch = rotationPool.stream()
                    .filter(type -> !unavailableThisRun.contains(type))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (batch.isEmpty())
                break;

            boolean progress = false;
            for (GatherType type : batch) {
                if (remaining <= 0 || currentActive >= activeQueues)
                    break;

                GatherDeployResult deployResult = deploy(type);
                if (deployResult == GatherDeployResult.DEPLOYED) {
                    currentActive++;
                    remaining--;
                    deployed++;
                    rotationPool.remove(type);
                    progress = true;
                    StatisticsService.obtain().addToCounter(profile, "Gather Marches Deployed", 1);
                    activeMarches.add(type);
                } else {
                    if (deployResult == GatherDeployResult.NO_TROOPS_AVAILABLE) {
                        remaining = 0;
                        saveRotationPool();
                        return new GatherFillResult(deployed, noNode, blocked, sameTargetBlocked, true, 0);
                    }
                    // Remove failed type from pool to avoid retrying it endlessly
                    rotationPool.remove(type);
                    if (deployResult == GatherDeployResult.NO_NODE_FOUND) {
                        noNode++;
                        unavailableThisRun.add(type);
                    }
                    if (deployResult == GatherDeployResult.BLOCKED) {
                        blocked++;
                    }
                    if (deployResult == GatherDeployResult.SAME_TARGET) {
                        blocked++;
                        sameTargetBlocked++;
                    }
                }
            }

            if (!progress || currentActive >= activeQueues)
                break;
        }

        saveRotationPool();
        return new GatherFillResult(deployed, noNode, blocked, sameTargetBlocked, false, remaining);
    }

    private boolean refillRotationPool(List<GatherType> activeMarches, Set<GatherType> unavailableThisRun) {
        GatherRotationSelectionPolicy.Refill refill = GatherRotationSelectionPolicy.refill(
                enabledTypes, activeMarches, unavailableThisRun);
        rotationPool = new ArrayList<>(refill.candidates());

        switch (refill.mode()) {
            case MISSING_TYPES -> logDebug("Gather pool exhausted; prioritizing missing types: " + rotationPool);
            case DUPLICATES -> logDebug(
                    "All enabled gather types are active; allowing duplicate types for additional slots: "
                            + rotationPool);
            case UNAVAILABLE_MISSING_TYPES -> logInfo(
                    "Gather pool cannot refill because a missing resource type is unavailable in this run.");
        }
        return !rotationPool.isEmpty();
    }

    private void loadRotationPool() {
        String saved = profile.getConfig(ConfigurationKeyEnum.GATHER_ROTATION_POOL, String.class);
        logDebug("Loaded gather pool config: '" + saved + "'");
        if (saved == null || saved.isEmpty()) {
            rotationPool = new ArrayList<>(enabledTypes);
            logDebug("Gather pool config empty. Resetting to full: " + rotationPool);
            return;
        }
        try {
            rotationPool = Arrays.stream(saved.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(GatherType::valueOf)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            rotationPool = new ArrayList<>(enabledTypes);
            logDebug("Could not parse gather pool config; resetting: " + e.getMessage());
        }
    }

    private void saveRotationPool() {
        if (rotationPool == null)
            return;
        String val = rotationPool.stream().map(Enum::name).collect(Collectors.joining(","));
        logDebug("Saving gather pool config: '" + val + "'");
        writeProfileSetting(ConfigurationKeyEnum.GATHER_ROTATION_POOL, val);
    }

    // ================= SCAN & CHECKS =================

    private GatherMarchSnapshot readGatherMarchSnapshot() {
        return toGatherMarchSnapshot(marchHelper.readMarchQueueSinglePass());
    }

    private GatherMarchSnapshot toGatherMarchSnapshot(List<MarchSlotState> slots) {
        List<GatherType> activeTypes = slots.stream()
                .filter(slot -> slot.activityType() == MarchActivityType.GATHER)
                .map(slot -> toGatherType(slot.resourceType()))
                .filter(type -> type != null)
                .collect(Collectors.toCollection(ArrayList::new));

        int activeGatherCount = (int) slots.stream()
                .filter(slot -> slot.activityType() == MarchActivityType.GATHER)
                .count();
        int idleSlotCount = (int) slots.stream()
                .filter(slot -> slot.availability() == MarchSlotAvailability.IDLE)
                .count();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextCheckAt = MarchSlotAvailabilityEstimator
                .estimateEarliestCheckAt(slots, now, MARCH_SLOT_ESTIMATE_SETTINGS)
                .orElse(null);
        LocalDateTime earliestGatherCheckAt = slots.stream()
                .filter(slot -> slot.activityType() == MarchActivityType.GATHER)
                .map(slot -> MarchSlotAvailabilityEstimator
                        .estimateNextCheck(slot, MARCH_SLOT_ESTIMATE_SETTINGS)
                        .map(now::plus)
                        .orElse(null))
                .filter(time -> time != null)
                .min(Comparator.naturalOrder())
                .orElse(null);

        return new GatherMarchSnapshot(slots, activeTypes, activeGatherCount, idleSlotCount,
                nextCheckAt, earliestGatherCheckAt);
    }

    private GatherType toGatherType(MarchResourceType resourceType) {
        if (resourceType == null) {
            return null;
        }
        return switch (resourceType) {
            case MEAT -> GatherType.MEAT;
            case WOOD -> GatherType.WOOD;
            case COAL -> GatherType.COAL;
            case IRON -> GatherType.IRON;
            case UNKNOWN -> null;
        };
    }

    // Changed by pernerch | Date: 2026-07-02 | Why: when autojoin is disabled and all gather
    // slots are blocked, recall already-recallable marches before falling back to a fixed wait.
    private int recallBlockedMarchesWhenAutojoinOffFlow() {
        navigationHelper.ensureCorrectScreenLocation(LaunchPoint.WORLD);
        List<MarchSlotState> beforeRecall = marchHelper.readMarchQueueSinglePass();
        sleepTask(250);
        marchHelper.openLeftMenuCitySection(false);

        int dispatched = 0;
        try {
            PointData limit = new PointData(415,
                    MARCH_QUEUES[MARCH_QUEUES.length - 1].bottomRight.getY());

            List<ImageSearchResultData> recallButtons = templateSearchHelper.locateAllPatterns(
                    TemplatesEnum.MARCHES_AREA_RECALL_BUTTON,
                    SearchConfig.builder()
                            .withArea(new AreaData(MARCH_QUEUES[0].topLeft, limit))
                            .withMaxAttempts(3)
                            .withMaxResults(MARCH_QUEUES.length)
                            .withDelay(3)
                            .build());

            if (recallButtons == null || recallButtons.isEmpty()) {
                return 0;
            }

            recallButtons.sort(Comparator.comparingInt(button -> button.getPoint().getY()));
            for (ImageSearchResultData recallButton : recallButtons) {
                if (recallButton == null || !recallButton.isFound()) {
                    continue;
                }

                tapInside(recallButton.getPoint(), recallButton.getPoint(), 1, 200);
                tapInside(RECALL_CONFIRM_TL, RECALL_CONFIRM_BR, 1, 200);
                sleepTask(400);
                dispatched++;
            }
        } finally {
            marchHelper.closeLeftMenu();
        }

        if (dispatched == 0) {
            return 0;
        }
        List<MarchSlotState> afterRecall = marchHelper.readMarchQueueSinglePass();
        int confirmed = countConfirmedRecallTransitions(beforeRecall, afterRecall);
        logInfo(String.format("Autojoin-off recall verification: dispatched=%d confirmed=%d", dispatched, confirmed));
        return confirmed;
    }

    static RecallBatchResult executeRecallBatch(List<ActiveGatherMarchCandidate> candidates,
            Function<ActiveGatherMarchCandidate, RecallAttempt> recall) {
        int recalled = 0;
        for (ActiveGatherMarchCandidate candidate : candidates) {
            RecallAttempt attempt = recall.apply(candidate);
            if (attempt == RecallAttempt.CONTROLS_NOT_FOUND) {
                return new RecallBatchResult(recalled, true, false);
            }
            if (attempt == RecallAttempt.UNKNOWN_OUTCOME) {
                return new RecallBatchResult(recalled, false, true);
            }
            if (attempt == RecallAttempt.RECALLED) recalled++;
        }
        return new RecallBatchResult(recalled, false, false);
    }

    static List<MarchSlotState> collectActiveGatherMarchSlots(List<MarchSlotState> slots) {
        return slots.stream().filter(MarchSlotState::isGather).toList();
    }

    static boolean hasUnknownMarchQueueState(List<MarchSlotState> slots) {
        return slots.isEmpty() || slots.stream().anyMatch(slot ->
                slot.availability() == MarchSlotAvailability.UNKNOWN
                        || (slot.availability() == MarchSlotAvailability.OCCUPIED
                        && (slot.activityType() == MarchActivityType.UNKNOWN
                        || slot.movementPhase() == MarchMovementPhase.UNKNOWN)));
    }

    static boolean isRecallCandidate(MarchSlotState slot) {
        return slot != null
                && slot.availability() == MarchSlotAvailability.OCCUPIED
                && slot.activityType() == MarchActivityType.GATHER
                && (slot.movementPhase() == MarchMovementPhase.OUTBOUND
                || slot.movementPhase() == MarchMovementPhase.WORKING);
    }

    static boolean isRecallConfirmed(MarchSlotState before, MarchSlotState after) {
        if (before == null || after == null
                || before.availability() != MarchSlotAvailability.OCCUPIED
                || before.activityType() == MarchActivityType.NONE
                || before.activityType() == MarchActivityType.UNKNOWN
                || before.movementPhase() == MarchMovementPhase.RETURNING
                || before.movementPhase() == MarchMovementPhase.UNKNOWN) {
            return false;
        }
        return after.availability() == MarchSlotAvailability.IDLE
                || after.movementPhase() == MarchMovementPhase.RETURNING;
    }

    static int countConfirmedRecallTransitions(List<MarchSlotState> before, List<MarchSlotState> after) {
        int confirmed = 0;
        for (MarchSlotState previous : before) {
            MarchSlotState current = after.stream()
                    .filter(slot -> slot.slot() == previous.slot())
                    .findFirst()
                    .orElse(null);
            if (isRecallConfirmed(previous, current)) {
                confirmed++;
            }
        }
        return confirmed;
    }

    // Builds candidates only for marches that can still be recalled (not already returning or unknown).
    private List<ActiveGatherMarchCandidate> collectRecallEligibleGatherMarchCandidatesFlow() {
        return collectRecallEligibleGatherMarchCandidates(marchHelper.readMarchQueueSinglePass());
    }

    private List<ActiveGatherMarchCandidate> collectRecallEligibleGatherMarchCandidates(List<MarchSlotState> slots) {
        List<ActiveGatherMarchCandidate> candidates = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (MarchSlotState slot : slots) {
            if (!isRecallCandidate(slot)) {
                continue;
            }

            GatherType type = toGatherType(slot.resourceType());
            if (type == null) {
                logDebug("Skipping active gather row with unknown resource type: #" + slot.slot()
                        + " evidence=" + slot.evidence());
                continue;
            }

            LocalDateTime returnTime = MarchSlotAvailabilityEstimator
                    .estimateNextCheck(slot, MARCH_SLOT_ESTIMATE_SETTINGS)
                    .map(now::plus)
                    .orElse(now.plusMinutes(UNKNOWN_MARCH_RETRY_MINUTES));
            candidates.add(new ActiveGatherMarchCandidate(type, slot.slot() - 1, returnTime, slot));
        }
        return candidates;
    }

    // Targets a specific gather row when an explicit high-priority event recall is enabled.
    private RecallAttempt recallGatherMarchByQueueFlow(ActiveGatherMarchCandidate candidate, RecallReason reason) {
        navigationHelper.ensureCorrectScreenLocation(LaunchPoint.WORLD);
        sleepTask(250);
        marchHelper.openLeftMenuSection(false);
        RecallAttempt dispatched;
        try {
            dispatched = recallGatherMarchFromOpenPanelFlow(candidate, reason);
            if (dispatched == RecallAttempt.CONTROLS_NOT_FOUND) {
                saveMissingRecallControlsScreenshot();
            }
        } finally {
            marchHelper.closeLeftMenu();
        }
        if (dispatched != RecallAttempt.RECALLED) {
            return dispatched;
        }
        List<MarchSlotState> afterRecall = marchHelper.readMarchQueueSinglePass();
        MarchSlotState after = afterRecall.stream()
                .filter(slot -> slot.slot() == candidate.queueIndex() + 1)
                .findFirst()
                .orElse(null);
        if (isRecallConfirmed(candidate.initialState(), after)) {
            logInfo("Gather recall confirmed by March Queue state: queue=#" + (candidate.queueIndex() + 1));
            return RecallAttempt.RECALLED;
        }
        logWarning("Gather recall outcome is unknown after the confirmation tap: queue=#"
                + (candidate.queueIndex() + 1) + "; no second recall tap will be sent.");
        return RecallAttempt.UNKNOWN_OUTCOME;
    }

    private RecallAttempt recallGatherMarchFromOpenPanelFlow(
            ActiveGatherMarchCandidate candidate, RecallReason reason) {
        int queueIndex = candidate.queueIndex();
        PointData limit = new PointData(415,
                MARCH_QUEUES[MARCH_QUEUES.length - 1].bottomRight.getY());

        List<ImageSearchResultData> recallButtons = templateSearchHelper.locateAllPatterns(
                TemplatesEnum.MARCHES_AREA_RECALL_BUTTON,
                SearchConfig.builder()
                        .withArea(new AreaData(MARCH_QUEUES[0].topLeft, limit))
                        .withMaxAttempts(3)
                        .withMaxResults(MARCH_QUEUES.length)
                        .withDelay(3)
                        .build());

        if (recallButtons.isEmpty()) {
            return RecallAttempt.CONTROLS_NOT_FOUND;
        }

        int targetCenterY = (MARCH_QUEUES[queueIndex].topLeft.getY()
                + MARCH_QUEUES[queueIndex].bottomRight.getY()) / 2;
        ImageSearchResultData targetButton = recallButtons.stream()
                .min(Comparator.comparingInt(button -> Math.abs(button.getPoint().getY() - targetCenterY)))
                .orElse(null);

        if (targetButton == null) {
            return RecallAttempt.CONTROLS_NOT_FOUND;
        }

        tapInside(targetButton.getPoint(), targetButton.getPoint(), 1, 200);
        tapInside(RECALL_CONFIRM_TL, RECALL_CONFIRM_BR, 1, 200);
        sleepTask(500);
        logInfo(String.format(
                "Gather march recall dispatched | reason=%s | queue=#%d | type=%s | return=%s",
                reason.logValue,
                queueIndex + 1,
                candidate.type(),
                GameTimeUtils.formatCountdown(candidate.returnTime())));
        return RecallAttempt.RECALLED;
    }

    private void saveMissingRecallControlsScreenshot() {
        try {
            Path outputDirectory = WorkspacePaths.current().root().resolve("temp");
            Files.createDirectories(outputDirectory);
            String profileName = profile.getName() == null ? "profile" : profile.getName();
            String safeProfileName = profileName.replaceAll("[^A-Za-z0-9._-]", "_");
            Path output = outputDirectory.resolve(
                    "gather-recall-controls-missing-" + safeProfileName + "-latest.png");
            RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
            BufferedImage image = dev.frostguard.vision.convert.ImageConverter.toBufferedImage(frame);
            if (!ImageIO.write(image, "png", output.toFile())) {
                throw new IOException("PNG writer unavailable");
            }
            logWarning("Gather recall diagnostic screenshot saved: " + output);
        } catch (Exception e) {
            logWarning("Could not save gather recall diagnostic screenshot: " + e.getMessage());
        }
    }

    // ================= DEPLOYMENT PIPELINE =================

    private GatherDeployResult deploy(GatherType type) {
        int targetLevel = get(type.levelKey, DEFAULT_LEVEL);
        int minLevel = downgradeLevelOnMissingNode ? 1 : targetLevel;

        for (int level = targetLevel; level >= minLevel; level--) {
            int sameTargetFailures = 0;
            GatherDeployResult result;

            do {
                logInfo(String.format(
                        "Gather deploy attempt: type=%s level=%d onlyFull=%s nodeAttempt=%d/%d",
                        type, level, onlyFullResources, sameTargetFailures + 1,
                        GatherSameTargetRetryPolicy.MAX_NODE_ATTEMPTS));

                if (!openSearchMenu()) {
                    return retryLater(GatherDeployResult.BLOCKED);
                }
                if (!selectTile(type)) {
                    return retryLater(GatherDeployResult.BLOCKED);
                }
                if (!setLevel(level)) {
                    return retryLater(GatherDeployResult.BLOCKED);
                }
                setOnlyFullResourcesSearch(onlyFullResources);
                if (!executeSearch()) {
                    // A search with no confirmed Gather target is a missing node,
                    // not proof that a march was launched or the UI was navigated.
                    result = GatherDeployResult.NO_NODE_FOUND;
                    break;
                }

                result = deployMarchAction(type, level);
                if (result == GatherDeployResult.SAME_TARGET) {
                    sameTargetFailures++;
                    if (GatherSameTargetRetryPolicy.shouldSearchAnotherNode(sameTargetFailures)) {
                        logInfo(String.format(
                                "Gather target already has an incoming march: type=%s level=%d. Searching another node.",
                                type, level));
                        sleepTask(500);
                    }
                }
            } while (result == GatherDeployResult.SAME_TARGET
                    && GatherSameTargetRetryPolicy.shouldSearchAnotherNode(sameTargetFailures));

            if (result == GatherDeployResult.DEPLOYED) {
                return result;
            }
            if (result == GatherDeployResult.SAME_TARGET) {
                logInfo(String.format(
                        "Gather target conflict persisted after %d node attempts: type=%s level=%d.",
                        sameTargetFailures, type, level));
                return result;
            }
            if (result != GatherDeployResult.NO_NODE_FOUND || level == minLevel) {
                return retryLater(result);
            }

            logInfo(String.format("No %s node found at level %d; trying level %d.", type, level, level - 1));
            pressBack();
            sleepTask(500);
        }

        return GatherDeployResult.NO_NODE_FOUND;
    }

    private boolean openSearchMenu() {
        tapInside(SEARCH_BTN_TL, SEARCH_BTN_BR);
        sleepTask(2000);
        swipe(RES_TAB_SWIPE_START, RES_TAB_SWIPE_END);
        sleepTask(500);
        boolean opened = isResourceSearchPanelVisible();
        if (!opened) {
            logWarning("Gather resource search did not open or show Meat/Wood after swipe; "
                    + "not attempting resource selection or blind search taps.");
        }
        return opened;
    }

    private boolean isResourceSearchPanelVisible() {
        SearchConfig config = SearchConfig.builder().withArea(AreaData.of(0, 835, 720, 995))
                .withThreshold(90).withMaxAttempts(2).withDelay(200).build();
        return templateSearchHelper.locatePattern(TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_MEAT,
                config).isFound()
                || templateSearchHelper.locatePattern(TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_WOOD,
                        config).isFound();
    }

    private boolean selectTile(GatherType type) {
        for (int i = 0; i < 4; i++) {
            ImageSearchResultData tile = templateSearchHelper.locatePattern(type.tile, SearchConfig.builder().build());
            if (tile.isFound()) {
                tapInside(tile);
                sleepTask(500);
                return true;
            }
            if (i < 3) {
                swipe(RES_TAB_SWIPE_START, RES_TAB_SWIPE_END);
                sleepTask(500);
            }
        }
        return false;
    }

    private boolean setLevel(int target) {
        Integer current = readLevel();
        if (current != null && current == target)
            return true;

        if (current == null) {
            resetLevelToOne();
            if (target > 1)
                tapInside(LEVEL_INC_TL, LEVEL_INC_BR, target - 1, 150);
        } else {
            if (current < target)
                tapInside(LEVEL_INC_TL, LEVEL_INC_BR, target - current, 150);
            else
                tapInside(LEVEL_DEC_TL, LEVEL_DEC_BR, current - target, 150);
        }
        return true;
    }

    private void setOnlyFullResourcesSearch(boolean desired) {
        boolean current = templateSearchHelper
                .locatePattern(TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_TICK,
                        SearchConfig.builder()
                                .withArea(ONLY_FULL_RESOURCES_TICK_AREA)
                                .withMaxAttempts(2)
                                .build())
                .isFound();
        if (current != desired) {
            logDebug("Setting only-full-resources search to " + desired);
            tapNear(ONLY_FULL_RESOURCES_TOGGLE);
            sleepTask(300);
        }
    }

    private boolean executeSearch() {
        tapInside(SEARCH_EXEC_TL, SEARCH_EXEC_BR);
        sleepTask(1500);
        // Do not treat the Search tap alone as a located resource. The actual
        // Lv.1 Meat panel keeps the resource-search sidebar open, but adds a
        // visible Gather action in the node modal. Absence is a bounded
        // no-node result and can use the existing lower-level fallback.
        for (int attempt = 0; attempt < 2; attempt++) {
            if (locateGatherButton().isFound()) return true;
            if (attempt == 0) sleepTask(650);
        }
        logWarning("Gather search did not produce a verified Gather action; "
                + "not proceeding to formation or deployment");
        return false;
    }

    private GatherDeployResult deployMarchAction(GatherType type, int level) {
        ImageSearchResultData btn = locateGatherButton();
        if (!btn.isFound()) {
            saveMissingGatherButtonScreenshot(type, level);
            logWarning(String.format(
                    "Gather button not recognized after resource search: type=%s level=%d onlyFull=%s. "
                    + "The resource may exist but its button may differ from the saved template.",
                    type, level, onlyFullResources));
            return GatherDeployResult.NO_NODE_FOUND;
        }

        tapInside(btn);
        sleepTask(1000);

        ImageSearchResultData hero = templateSearchHelper.locatePattern(type.preferredHero,
                SearchConfig.builder().withCoordinates(new PointData(51, 231), new PointData(295, 649)).build());

        if (!hero.isFound() && !noHeroFallback) {
            logDebug("Preferred hero not found for " + type + ". Proceeding with default march.");
        }
        GatherHeroSelectionPolicy.Action heroAction = GatherHeroSelectionPolicy.select(
                hero.isFound(), removeHeroes, noHeroFallback);
        logInfo("Gather formation hero selection: action=" + heroAction
                + " preferredHeroFound=" + hero.isFound());
        if (!applyHeroSelection(type, heroAction)) {
            logWarning("Gather hero selection could not be verified; deployment stopped.");
            saveGatherFormationScreenshot("hero-selection", type, level);
            return GatherDeployResult.BLOCKED;
        }

        if (deploymentHelper.hasNoDeployableTroops()) {
            logInfo("No deployable troops found on gather formation screen.");
            return GatherDeployResult.NO_TROOPS_AVAILABLE;
        }

        ImageSearchResultData deploy = locateConfirmedGatherDeployButton();
        if (!deploy.isFound()) {
            if (deploymentHelper.hasNoDeployableTroops()) {
                logInfo("Gather deploy button absent: no deployable troops available.");
                saveGatherFormationScreenshot("no-troops", type, level);
                return GatherDeployResult.NO_TROOPS_AVAILABLE;
            }
            logWarning("Gather formation reached, but Deploy button was not recognized; "
                    + "not treating this as a completed march.");
            saveGatherFormationScreenshot("deploy-button", type, level);
            return GatherDeployResult.BLOCKED;
        }

        logInfo(String.format(java.util.Locale.ROOT,
                "Gather Deploy button verified at %s (score %.1f), tapping once.",
                deploy.getPoint(), deploy.getMatchScore()));
        tapInside(deploy);
        sleepTask(1000);

        if (deploymentHelper.isMarchQueueFull()) {
            return GatherDeployResult.BLOCKED;
        }

        if (deploymentHelper.isSameTargetDialog()) {
            pressBack();
            pressBack();
            return GatherDeployResult.SAME_TARGET;
        }
        // A disappearing button does not prove that the march departed. Only
        // the verified World screen after one Deploy tap authorizes completion.
        var launched = deploymentHelper.verifyLaunchTransition(TemplatesEnum.SERVE_EMU_GATHER_DEPLOY);
        if (launched != dev.frostguard.engine.helper.DeploymentHelper.LaunchCheck.WORLD_VERIFIED) {
            logWarning("Gather Deploy tap not confirmed by return to World: " + launched
                    + "; not counting a dispatched march or sending another tap");
            saveGatherFormationScreenshot("launch-world-unverified", type, level);
            return GatherDeployResult.BLOCKED;
        }
        return GatherDeployResult.DEPLOYED;
    }

    private ImageSearchResultData locateConfirmedGatherDeployButton() {
        // Modern 720x1280 UI has different anti-aliasing; two-frame consensus
        // is required and the search is restricted to the bottom Deploy area.
        ImageSearchResultData current = templateSearchHelper.locateAction(
                TemplatesEnum.SERVE_EMU_GATHER_DEPLOY,
                SearchConfig.builder().withMaxAttempts(2).withDelay(140)
                        .withThreshold(90).withArea(AreaData.of(400, 1140, 705, 1270)).build());
        if (current.isFound()) return current;
        TemplatesEnum button = TemplatesEnum.GATHER_DEPLOY_BUTTON;
        ImageSearchResultData exact = templateSearchHelper.locatePattern(button,
                SearchConfig.builder().withMaxAttempts(2).build());
        if (exact.isFound()) return exact;
        ImageSearchResultData scaled = templateSearchHelper.locatePatternMultiScale(button,
                SearchConfig.builder().withThreshold(90).withMaxAttempts(2).build());
        if (scaled.isFound()) return scaled;
        ImageSearchResultData first = templateSearchHelper.locatePatternMultiScale(button,
                SearchConfig.builder().withThreshold(86).build());
        if (first.isFound() && first.getPoint() != null) {
            sleepTask(350);
            ImageSearchResultData second = templateSearchHelper.locatePatternMultiScale(button,
                    SearchConfig.builder().withThreshold(86).build());
            if (second.isFound() && second.getPoint() != null
                    && first.getPoint().manhattanDistanceTo(second.getPoint()) <= 20) {
                logInfo(String.format(java.util.Locale.ROOT,
                        "Gather Deploy button confirmed in two frames (%.1f / %.1f)",
                        first.getMatchScore(), second.getMatchScore()));
                return second;
            }
        }
        return scaled;
    }

    private void saveGatherFormationScreenshot(String reason, GatherType type, int level) {
        try {
            Path folder = WorkspacePaths.current().root().resolve("temp");
            Files.createDirectories(folder);
            String profileName = profile.getName() == null ? "profile" : profile.getName();
            String safeName = profileName.replaceAll("[^A-Za-z0-9._-]", "_");
            Path output = folder.resolve("gather-formation-" + reason + "-"
                    + safeName + "-latest.png");
            RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
            if (ImageIO.write(dev.frostguard.vision.convert.ImageConverter.toBufferedImage(frame),
                    "png", output.toFile())) {
                logWarning("Gather formation diagnostic saved: " + output
                        + " (type=" + type + ", level=" + level + ")");
            }
        } catch (Exception e) {
            logWarning("Could not capture Gather formation diagnostic: " + e.getMessage());
        }
    }

    /**
     * Prefer the existing exact Gather template. BlueStacks rendering may alter
     * button scaling slightly, so attempt multi-scale recognition before declaring
     * a node missing. A lower-confidence match must repeat at the same location.
     */
    private ImageSearchResultData locateGatherButton() {
        // Confirm in two distinct frames so an incidental Gather label cannot
        // cause a blind tap. The result remains screen-scoped to the searched
        // resource's card and is followed by a verified formation state.
        return templateSearchHelper.locateAction(TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_GATHER,
                SearchConfig.builder().withMaxAttempts(2).withDelay(150)
                        .withThreshold(89).build());
    }

    private void saveMissingGatherButtonScreenshot(GatherType type, int level) {
        try {
            Path folder = WorkspacePaths.current().root().resolve("temp");
            Files.createDirectories(folder);
            String safeName = profile.getName().replaceAll("[^A-Za-z0-9._-]", "_");
            Path output = folder.resolve("gather-button-missing-" + safeName + "-latest.png");
            RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
            BufferedImage image = dev.frostguard.vision.convert.ImageConverter.toBufferedImage(frame);
            ImageIO.write(image, "png", output.toFile());
            logWarning("Gather detection screenshot saved: " + output
                    + " (type=" + type + ", level=" + level + ")");
        } catch (Exception e) {
            logWarning("Could not save Gather detection screenshot: " + e.getMessage());
        }
    }

    // ================= HELPERS (UI/OCR) =================

    private Integer readLevel() {
        OcrSettingsData s = OcrSettingsData.assembler().charWhitelist("0123456789")
                .stripBackground(true).setTextColor(new Color(255, 255, 255)).build();
        return readNumberValue(LEVEL_DISPLAY_TL, LEVEL_DISPLAY_BR, s);
    }

    private boolean applyHeroSelection(GatherType type, GatherHeroSelectionPolicy.Action action) {
        return switch (action) {
            case KEEP_DEFAULT -> true;
            case REMOVE_ADDITIONAL -> {
                removeDefaultHeroes();
                yield true;
            }
            case REMOVE_ALL -> removeAllHeroes(type);
        };
    }

    private boolean removeAllHeroes(GatherType type) {
        List<ImageSearchResultData> buttons = locateSelectedHeroRemoveButtons();
        if (buttons.isEmpty()) {
            logWarning("Preferred hero not found for " + type
                    + ", but selected heroes could not be identified. Cancelling gather deployment.");
            return false;
        }

        buttons.sort(Comparator.comparingInt(result -> result.getPoint().getX()));
        tapInside(buttons.getFirst());
        sleepTask(500);

        if (!locateSelectedHeroRemoveButtons().isEmpty()) {
            logWarning("Preferred hero not found for " + type
                    + ", but the hero selection did not clear. Cancelling gather deployment.");
            return false;
        }

        logInfo("Preferred hero not found for " + type + ". Deploying gather march without heroes.");
        return true;
    }

    private List<ImageSearchResultData> locateSelectedHeroRemoveButtons() {
        return templateSearchHelper.locateAllPatterns(
                TemplatesEnum.RALLY_REMOVE_HERO_BUTTON,
                SearchConfig.builder().withThreshold(90).withMaxResults(3).build());
    }

    private void removeDefaultHeroes() {
        List<ImageSearchResultData> btns = locateSelectedHeroRemoveButtons();

        if (btns.isEmpty())
            return;
        btns.sort(Comparator.comparingInt(r -> r.getPoint().getX()));

        for (int i = 1; i < btns.size(); i++) {
            tapInside(btns.get(i));
            sleepTask(300);
        }
    }

    private void resetLevelToOne() {
        swipe(LEVEL_SLIDER_START, LEVEL_SLIDER_END);
        sleepTask(300);
    }

    private GatherDeployResult retryLater(GatherDeployResult result) {
        pressBack(); // Safety back
        return result;
    }

    // ================= SCHEDULING & CONFLICTS =================

    private void updateReschedule(LocalDateTime t) {
        if (earliestReschedule == null || t.isBefore(earliestReschedule))
            earliestReschedule = t;
    }

    static boolean hasReachedConfiguredQueueLimit(int activeGatherCount, int configuredQueueLimit) {
        return activeGatherCount >= configuredQueueLimit;
    }

    private void finalizeReschedule(GatherFillResult fillResult) {
        if (GatherSameTargetRetryPolicy.requiresCooldown(
                fillResult.sameTargetBlocked(), fillResult.unfilledSlots())) {
            LocalDateTime retryAt = LocalDateTime.now()
                    .plus(GatherSameTargetRetryPolicy.EXHAUSTED_RETRY_DELAY);
            logInfo(String.format(
                    "Gather fill finished: deployed=%d noNode=%d blocked=%d noTroops=%s. "
                            + "Same-target conflicts left %d slot(s) unfilled; retrying node search in %d minutes at %s.",
                    fillResult.deployed(),
                    fillResult.noNode(),
                    fillResult.blocked(),
                    fillResult.stoppedForNoTroops(),
                    fillResult.unfilledSlots(),
                    GatherSameTargetRetryPolicy.EXHAUSTED_RETRY_DELAY.toMinutes(),
                    GameTimeUtils.formatCountdown(retryAt)));
            reschedule(retryAt);
            return;
        }
        if (earliestReschedule != null) {
            reschedule(earliestReschedule);
            return;
        }
        LocalDateTime nextGatherCheck = resolveEarliestGatherRedeployTime();
        if (nextGatherCheck != null) {
            LocalDateTime scheduleAt = nextGatherCheck.plusMinutes(TROOP_RETURN_MARGIN_MINUTES);
            String reason = fillResult.stoppedForNoTroops()
                    ? "No deployable troops remain"
                    : "All gather slots filled";
            logInfo(String.format(
                    "Gather fill finished: deployed=%d noNode=%d blocked=%d noTroops=%s. %s. Next gather check at %s (return-aware + %d min margin).",
                    fillResult.deployed(),
                    fillResult.noNode(),
                    fillResult.blocked(),
                    fillResult.stoppedForNoTroops(),
                    reason,
                    GameTimeUtils.formatCountdown(scheduleAt), TROOP_RETURN_MARGIN_MINUTES));
            reschedule(scheduleAt);
        } else {
            reschedule(LocalDateTime.now().plusMinutes(5));
        }
    }

    private LocalDateTime resolveEarliestGatherRedeployTime() {
        try {
            LocalDateTime now = LocalDateTime.now();
            return marchHelper.readMarchQueueSinglePass().stream()
                    .map(slot -> estimateGatherRedeployTime(slot, now))
                    .filter(time -> time != null)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
        } catch (Exception e) {
            logDebug("Could not resolve next gather redeploy time: " + e.getMessage());
            return null;
        }
    }

    private LocalDateTime estimateGatherRedeployTime(MarchSlotState slot, LocalDateTime now) {
        if (slot == null || now == null) {
            return null;
        }
        if (slot.movementPhase() == MarchMovementPhase.RETURNING && slot.countdown() != null) {
            return now.plus(slot.countdown());
        }
        if (slot.activityType() == MarchActivityType.GATHER
                && slot.movementPhase() == MarchMovementPhase.WORKING) {
            return MarchSlotAvailabilityEstimator
                    .estimateNextCheck(slot, MARCH_SLOT_ESTIMATE_SETTINGS)
                    .map(now::plus)
                    .orElse(null);
        }
        return null;
    }

    // pernerch/2026-07-02: replaces blind checkIntelConflict(). Handles:
    // - Intel (full recall): recall all gather troops, defer past Intel end
    // - Intel (smart): only defer, no full recall
    // - Bear Trap (with recall+rally): recall all gather troops, defer past Bear end
    // - Dual-event (Intel+Bear both within 15 min): defer past BOTH to avoid pointless round-trips
    private boolean checkHighPriorityEventConflict() {
        boolean intelNeedsFullRecall = intelEnabled && intelRecall && !intelSmart;
        boolean intelNeedsSmartDefer = intelEnabled && (intelRecall || intelSmart);
        boolean intelPendingSoon     = intelNeedsSmartDefer
                                       && isEventPendingWithin(TpDailyTaskEnum.INTEL, DUAL_EVENT_LOOKAHEAD_MINUTES);

        boolean bearNeedsRecall  = isBearTrapRecallRequired();
        boolean bearPendingSoon  = bearNeedsRecall
                                   && isEventPendingWithin(TpDailyTaskEnum.BEAR_TRAP, DUAL_EVENT_LOOKAHEAD_MINUTES);

        if (!intelPendingSoon && !bearPendingSoon) return false;

        // Recall gather marches if required by the relevant event
        if (intelNeedsFullRecall && intelPendingSoon) {
            logInfo("Intel (full-recall mode) pending within " + DUAL_EVENT_LOOKAHEAD_MINUTES
                + " min. Recalling all gather marches.");
            recallAllGatherMarchesAndTrack();
        } else if (intelPendingSoon) {
            logInfo("Intel (smart mode) pending within " + DUAL_EVENT_LOOKAHEAD_MINUTES
                + " min. Deferring gather without full recall (duplicates only).");
        }
        if (bearPendingSoon) {
            logInfo("Bear Trap (recall+rally mode) pending within " + DUAL_EVENT_LOOKAHEAD_MINUTES
                + " min. Recalling all gather marches.");
            recallAllGatherMarchesAndTrack();
        }

        // Compute defer time past all pending high-priority events
        LocalDateTime deferUntil = computeDeferTimeAfterHighPriorityEvents(intelPendingSoon, bearPendingSoon);

        if (intelPendingSoon && bearPendingSoon) {
            logInfo(String.format(
                "Intel AND Bear Trap both pending within %d min. Deferring gather until after both events at %s.",
                DUAL_EVENT_LOOKAHEAD_MINUTES, GameTimeUtils.formatCountdown(deferUntil)));
        } else if (intelPendingSoon) {
            logInfo(String.format("Intel pending within %d min. Deferring gather until %s.",
                DUAL_EVENT_LOOKAHEAD_MINUTES, GameTimeUtils.formatCountdown(deferUntil)));
        } else {
            logInfo(String.format("Bear Trap pending within %d min. Deferring gather until %s.",
                DUAL_EVENT_LOOKAHEAD_MINUTES, GameTimeUtils.formatCountdown(deferUntil)));
        }
        reschedule(deferUntil);
        return true;
    }

    // pernerch/2026-07-02: true when Bear Trap is configured to consume ALL gather marches.
    // Bear with own rally only (no joiners) leaves gather marches free, so no recall needed.
    private boolean isBearTrapRecallRequired() {
        boolean bearEnabled  = get(ConfigurationKeyEnum.BEAR_TRAP_EVENT_BOOL, false);
        if (!bearEnabled) return false;
        boolean recallTroops = get(ConfigurationKeyEnum.BEAR_TRAP_RECALL_TROOPS_BOOL, false);
        boolean ownRally     = get(ConfigurationKeyEnum.BEAR_TRAP_CALL_RALLY_BOOL, false);
        boolean joinRally    = get(ConfigurationKeyEnum.BEAR_TRAP_JOIN_RALLY_BOOL, false);
        // Recall only needed when bear takes all march slots: recallTroops=true AND (own rally OR join rally)
        return recallTroops && (ownRally || joinRally);
    }

    // pernerch/2026-07-02: checks if the given task is scheduled within the next N minutes.
    private boolean isEventPendingWithin(TpDailyTaskEnum task, int minutes) {
        try {
            DailyTask t = dailyTaskRepository.findByAccountIdAndTaskType(profile.getId(), task);
            if (t == null || t.getScheduledAt() == null) return false;
            long minutesUntil = ChronoUnit.MINUTES.between(LocalDateTime.now(), t.getScheduledAt());
            return minutesUntil >= 0 && minutesUntil < minutes;
        } catch (Exception e) {
            return false;
        }
    }

    // pernerch/2026-07-02: recalls all active gather marches and records the recall timestamp.
    // Timestamp is stored both as instance field (fast) and in profile config (survives restart).
    private void recallAllGatherMarchesAndTrack() {
        List<ActiveGatherMarchCandidate> candidates = collectRecallEligibleGatherMarchCandidatesFlow();
        if (candidates.isEmpty()) {
            logInfo("No recall-eligible gather marches found.");
            return;
        }
        RecallBatchResult result = executeRecallBatch(candidates, candidate -> {
            RecallAttempt attempt = recallGatherMarchByQueueFlow(candidate, RecallReason.HIGH_PRIORITY_EVENT);
            if (attempt == RecallAttempt.RECALLED) sleepTask(300);
            return attempt;
        });
        if (result.recalled() > 0) {
            lastRecallTime = LocalDateTime.now();
            writeProfileSetting(ConfigurationKeyEnum.GATHER_LAST_RECALL_TIME_STRING, lastRecallTime.toString());
            logInfo("Confirmed gather recall time recorded: " + lastRecallTime.format(DATETIME_FORMATTER));
        }
        if (result.controlsMissing()) {
            logWarning(String.format(
                    "High-priority gather recall stopped: recall controls not detected; remaining marches=%d",
                    candidates.size() - result.recalled()));
        }
        if (result.unknownOutcome()) {
            logWarning(String.format(
                    "High-priority gather recall stopped on an unknown outcome; no repeated tap sent; "
                            + "remaining marches=%d",
                    candidates.size() - result.recalled()));
        }
        logInfo("Confirmed " + result.recalled() + " gather march recall(s) for high-priority event.");
    }

    // pernerch/2026-07-02: calculates when to resume gather after all pending high-priority events end.
    // For Intel: scheduled start + 15 min (typical Intel duration). For Bear: scheduled start + 30 min.
    // Final time gets the troop-return margin added so troops have time to walk home.
    private LocalDateTime computeDeferTimeAfterHighPriorityEvents(boolean intelPending, boolean bearPending) {
        LocalDateTime deferUntil = LocalDateTime.now();
        if (intelPending) {
            try {
                DailyTask intel = dailyTaskRepository.findByAccountIdAndTaskType(profile.getId(), TpDailyTaskEnum.INTEL);
                if (intel != null && intel.getScheduledAt() != null) {
                    LocalDateTime intelEnd = intel.getScheduledAt().plusMinutes(15);
                    if (intelEnd.isAfter(deferUntil)) deferUntil = intelEnd;
                }
            } catch (Exception ignored) {}
        }
        if (bearPending) {
            try {
                DailyTask bear = dailyTaskRepository.findByAccountIdAndTaskType(profile.getId(), TpDailyTaskEnum.BEAR_TRAP);
                if (bear != null && bear.getScheduledAt() != null) {
                    LocalDateTime bearEnd = bear.getScheduledAt().plusMinutes(BEAR_TRAP_DURATION_MINUTES);
                    if (bearEnd.isAfter(deferUntil)) deferUntil = bearEnd;
                }
            } catch (Exception ignored) {}
        }
        return deferUntil.plusMinutes(TROOP_RETURN_MARGIN_MINUTES);
    }

    // pernerch/2026-07-02: after an Intel or Bear recall, waits for troops to return home before
    // re-deploying. Uses option B: start with TROOP_RETURN_MARGIN_MINUTES, then +1 min per check
    // until the March Queue reports zero gather marches (including those already returning).
    private boolean checkTroopReturnPending() {
        if (lastRecallTime == null) return false;
        // Expire recall state after 2 hours to prevent permanent blocking from stale data
        if (ChronoUnit.MINUTES.between(lastRecallTime, LocalDateTime.now()) > 120) {
            clearRecallState();
            return false;
        }
        List<MarchSlotState> queueState = marchHelper.readMarchQueueSinglePass();
        List<MarchSlotState> active = collectActiveGatherMarchSlots(queueState);
        boolean queueStateUnknown = hasUnknownMarchQueueState(queueState);
        if (active.isEmpty() && !queueStateUnknown) {
            logInfo("All recalled gather troops have returned home. Clearing recall state and proceeding with fresh deployment.");
            clearRecallState();
            return false; // troops home, proceed with normal execute
        }
        LocalDateTime retryAt = LocalDateTime.now().plusMinutes(TROOP_RETURN_RETRY_MINUTES);
        if (queueStateUnknown) {
            logWarning("March Queue state is incomplete; keeping the recall wait active instead of assuming troops are home.");
        }
        logInfo(String.format(
            "Recalled gather troops still outside or unverified (%d gather march(es) active). Rechecking in %d min at %s.",
            active.size(), TROOP_RETURN_RETRY_MINUTES, GameTimeUtils.formatCountdown(retryAt)));
        reschedule(retryAt);
        return true;
    }

    private void clearRecallState() {
        this.lastRecallTime = null;
        writeProfileSetting(ConfigurationKeyEnum.GATHER_LAST_RECALL_TIME_STRING, "");
    }

    // Changed by pernerch | Date: 2026-07-02 | Why: when Gather is blocked by a pending Intel
    // task, force Intel immediately so it can use free marches now or reschedule itself on low stamina.
    private boolean triggerPendingIntelNowFlow() {
        TaskQueue queue = scheduleService.getCoordinator().getQueue(profile.getId());
        if (queue == null) {
            logWarning("Intel is pending but no active queue was available to force Intel immediately.");
            return false;
        }

        logInfo("Intel is pending. Forcing Intel now so marches are either used immediately or freed until Intel can run again.");
        queue.runNow(TpDailyTaskEnum.INTEL, true);
        return true;
    }

    private boolean checkGatherSpeedWait() {
        if (!gatherSpeed)
            return false;
        try {
            DailyTask t = dailyTaskRepository.findByAccountIdAndTaskType(profile.getId(), TpDailyTaskEnum.GATHER_BOOST);
            if (t == null)
                return false;
            long m = ChronoUnit.MINUTES.between(LocalDateTime.now(), t.getScheduledAt());
            if (m > 0 && m < 5) {
                reschedule(LocalDateTime.now().plusMinutes(2));
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    @Override
    protected LaunchPoint getRequiredStartLocation() {
        return LaunchPoint.WORLD;
    }

    @Override
    public boolean provideDailyMissionProgress() {
        return confirmedDailyMissionProgress;
    }

    static int countConfirmedNewGatherMarches(List<MarchSlotState> before, List<MarchSlotState> after) {
        if (before.isEmpty() || after.isEmpty()) {
            return 0;
        }
        int confirmed = 0;
        for (MarchSlotState current : after) {
            if (!isActiveGatherMarch(current)) {
                continue;
            }
            MarchSlotState previous = before.stream()
                    .filter(slot -> slot.slot() == current.slot())
                    .findFirst()
                    .orElse(null);
            if (previous != null
                    && (previous.availability() == MarchSlotAvailability.IDLE
                    || previous.movementPhase() == MarchMovementPhase.RETURNING)) {
                confirmed++;
            }
        }
        return confirmed;
    }

    private static boolean isActiveGatherMarch(MarchSlotState slot) {
        return slot.activityType() == MarchActivityType.GATHER
                && slot.availability() == MarchSlotAvailability.OCCUPIED
                && (slot.movementPhase() == MarchMovementPhase.OUTBOUND
                || slot.movementPhase() == MarchMovementPhase.WORKING);
    }

    // ================= INNER CLASSES =================

    public enum GatherType {
        MEAT(TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_MEAT,
                TemplatesEnum.GATHER_MEAT_HERO,
                ConfigurationKeyEnum.GATHER_MEAT_BOOL, ConfigurationKeyEnum.GATHER_MEAT_LEVEL_INT),
        WOOD(TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_WOOD,
                TemplatesEnum.GATHER_WOOD_HERO,
                ConfigurationKeyEnum.GATHER_WOOD_BOOL, ConfigurationKeyEnum.GATHER_WOOD_LEVEL_INT),
        COAL(TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_COAL,
                TemplatesEnum.GATHER_COAL_HERO,
                ConfigurationKeyEnum.GATHER_COAL_BOOL, ConfigurationKeyEnum.GATHER_COAL_LEVEL_INT),
        IRON(TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_IRON,
                TemplatesEnum.GATHER_IRON_HERO,
                ConfigurationKeyEnum.GATHER_IRON_BOOL, ConfigurationKeyEnum.GATHER_IRON_LEVEL_INT);

        final TemplatesEnum tile, preferredHero;
        final ConfigurationKeyEnum enabledKey, levelKey;

        GatherType(TemplatesEnum tile, TemplatesEnum preferredHero,
                ConfigurationKeyEnum enabledKey, ConfigurationKeyEnum levelKey) {
            this.tile = tile;
            this.preferredHero = preferredHero;
            this.enabledKey = enabledKey;
            this.levelKey = levelKey;
        }
    }

    private static class MarchQueueRegion {
        final PointData topLeft, bottomRight;

        MarchQueueRegion(PointData topLeft, PointData bottomRight) {
            this.topLeft = topLeft;
            this.bottomRight = bottomRight;
        }
    }

    private record GatherMarchSnapshot(List<MarchSlotState> slots, List<GatherType> activeTypes,
                                       int activeGatherCount, int idleSlotCount,
                                       LocalDateTime nextCheckAt, LocalDateTime earliestGatherCheckAt) {
    }

    record ActiveGatherMarchCandidate(GatherType type, int queueIndex, LocalDateTime returnTime,
                                      MarchSlotState initialState) {
        ActiveGatherMarchCandidate(GatherType type, int queueIndex, LocalDateTime returnTime) {
            this(type, queueIndex, returnTime, null);
        }
    }

    record RecallBatchResult(int recalled, boolean controlsMissing, boolean unknownOutcome) {
        RecallBatchResult(int recalled, boolean controlsMissing) {
            this(recalled, controlsMissing, false);
        }
    }

    private record GatherFillResult(int deployed, int noNode, int blocked, int sameTargetBlocked,
                                    boolean stoppedForNoTroops, int unfilledSlots) {
    }

    private enum GatherDeployResult {
        DEPLOYED,
        NO_NODE_FOUND,
        NO_TROOPS_AVAILABLE,
        BLOCKED,
        SAME_TARGET
    }

    enum RecallAttempt {
        RECALLED,
        CONTROLS_NOT_FOUND,
        UNKNOWN_OUTCOME
    }

    enum RecallReason {
        // pernerch/2026-07-02: march recalled because Intel (full-recall) or Bear Trap (recall+rally) is imminent
        HIGH_PRIORITY_EVENT("high-priority-event");

        private final String logValue;

        RecallReason(String logValue) {
            this.logValue = logValue;
        }
    }
}
