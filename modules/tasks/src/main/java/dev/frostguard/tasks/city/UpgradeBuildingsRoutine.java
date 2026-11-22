package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.tasks.dailies.ChiefOrderLiveFrame;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.*;
import dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig;
import dev.frostguard.engine.helper.FurnacePanelDetector;
import dev.frostguard.engine.diagnostics.DiagnosticSnapshotStore;
import dev.frostguard.tasks.city.CityUpgradeFlow.Attempt;
import dev.frostguard.tasks.city.CityUpgradeFlow.FailureReason;
import dev.frostguard.engine.nav.CommonOCRSettings;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.nav.SidebarSection;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.vision.convert.GameTimeUtils;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import static dev.frostguard.api.configs.TemplatesEnum.BUILDING_BUTTON_INFO;
import static dev.frostguard.api.configs.TemplatesEnum.BUILDING_BUTTON_RESEARCH;
import static dev.frostguard.api.configs.TemplatesEnum.BUILDING_BUTTON_SPEED;
import static dev.frostguard.api.configs.TemplatesEnum.BUILDING_BUTTON_TRAIN;
import static dev.frostguard.api.configs.TemplatesEnum.BUILDING_BUTTON_UPGRADE;
import static dev.frostguard.api.configs.TemplatesEnum.BUILDING_SURVIVOR_BUTTON_UPGRADE;
import static dev.frostguard.api.configs.TemplatesEnum.GAME_HOME_SHORTCUTS_HELP_REQUEST4;
import static dev.frostguard.api.configs.TemplatesEnum.GAME_HOME_SHORTCUTS_OBTAIN;
import static dev.frostguard.api.configs.TemplatesEnum.GAME_HOME_SHORTCUTS_UPGRADE_TEXT;
import static dev.frostguard.api.configs.TemplatesEnum.GAME_HOME_FURNACE;
import static dev.frostguard.api.configs.TemplatesEnum.REPLENISH_ALL_BUTTON;
import static dev.frostguard.engine.nav.LeftMenuTextSettings.*;

public class UpgradeBuildingsRoutine extends DelayedTask {

private static final AreaData QUEUE_AREA_1_VALUE = new AreaData(new PointData(95, 370), new PointData(358, 407));

private static final AreaData QUEUE_AREA_2_VALUE = new AreaData(new PointData(95, 443), new PointData(358, 480));

private static final AreaData BUILDING_ACTION_BUTTON_AREA_VALUE = new AreaData(new PointData(190, 1160), new PointData(530, 1250));

private static final AreaData BUILDING_CONFIRM_UPGRADE_SEARCH_AREA_VALUE =
        new AreaData(new PointData(350, 900), new PointData(700, 1255));

static final AreaData BUILDING_NAME_AREA_VALUE = new AreaData(new PointData(260, 510), new PointData(510, 575));

private static final int BLOCKER_RELEASE_GRACE_MINUTES = 5;

private static final int COMPLETION_SETTLE_SECONDS = 2;

private static final int BUILDING_CONTROL_CHECKS = 3;

private static final int BUILDING_CONTROL_POLL_MS = 300;

private static final int UPGRADE_CONFIRMATION_THRESHOLD = 90;

private final CityUpgradeDiagnostics diagnostics = new CityUpgradeDiagnostics();
private final boolean furnacePriority;
private boolean doubleTimeAttempted;

private static final int TEMPLATE_TAP_RADIUS = 8;

private static final int MAX_RESOURCE_REPLENISHMENTS = 4;

private static final PointData REPLENISH_CONFIRM_POINT = new PointData(511, 1056);

private static final SearchConfig REPLENISH_BUTTON_RECHECK = SearchConfig.builder()
        // Every step obtains a fresh observation; repeated misses consumed
        // unnecessary time on the live 22:30 Furnace resource cycle.
        .withMaxAttempts(1)
        .withDelay(0)
        .withThreshold(90)
        .withCoordinates(new PointData(180, 1070), new PointData(535, 1195))
        .build();

private final List<AreaData> queues = new ArrayList<>(Arrays.asList(QUEUE_AREA_1_VALUE, QUEUE_AREA_2_VALUE));

public UpgradeBuildingsRoutine(AccountDescriptor profile, TpDailyTaskEnum tpDailyTaskEnum) {
        this(profile,tpDailyTaskEnum,false);
    }

protected UpgradeBuildingsRoutine(AccountDescriptor profile,
        TpDailyTaskEnum tpDailyTaskEnum, boolean furnacePriority) {
        super(profile,tpDailyTaskEnum);
        this.furnacePriority=furnacePriority;
    }

@Override
    protected void execute() {
        try {
        CityUpgradeFlow.execute(this::executeQueues, new CityUpgradeFlow.Recovery() {
            @Override
            public void retainFailure(String type) {
                retainDiagnostic(type, false);
            }

            @Override
            public void recoverRoot() {
                closeVerifiedUpgradeBonusBeforeHome();
                recoverLocation(LaunchPoint.ANY);
            }

            @Override
            public void reportRecovery(boolean recovered, RuntimeException original, RuntimeException recoveryFailure) {
                logWarning(routineLogUpgradeBuildingsLine("Failure recovery: rootConfirmed=" + recovered
                        + "; original=" + original.getMessage()
                        + (recoveryFailure == null ? "; scheduler will retain the original failure and retry"
                                : "; recoveryFailure=" + recoveryFailure.getMessage())));
            }
        });
        } catch (CityUpgradeFlow.UnresolvedBuildingException shortage) {
            if (shortage.reason() != CityUpgradeFlow.FailureReason.RESOURCES_NOT_OBTAINED) {
                throw shortage;
            }
            LocalDateTime nextCheck = LocalDateTime.now().plusMinutes(45);
            markWorkIncomplete("City Upgrade needs resources; no unverified Replenish All, VIP Shop, or premium taps");
            reschedule(nextCheck);
            logWarning(routineLogUpgradeBuildingsLine(
                    "Resource shortage safely deferred until " + nextCheck.format(DATETIME_FORMATTER)
                    + "; City was recovered, no VIP Shop navigation"));
        }
    }

private void executeQueues() {
        diagnostics.begin(0, 0);
        doubleTimeAttempted = false;
        reachCityView();


        List<UpgradeBuildingsRoutine.QueueReadout> queueResults = inspectAllQueues();


        logQueueSummaryFlow(queueResults);
        clearConstructionReservationWhenStarted(queueResults);


        boolean reserveFirstQueue=FurnacePrioritySelectionPolicy.shouldReserveFirstQueue(
                Boolean.TRUE.equals(profile.getConfig(
                        ConfigurationKeyEnum.CITY_UPGRADE_PRIORITISE_FURNACE_BOOL,Boolean.class)),
                !furnacePriority);
        if (reserveFirstQueue) {
            logInfo("City Upgrade: reserving first construction slot for enabled Furnace Priority; "
                    + "ordinary buildings may use the second slot");
        }
        boolean hasIdleQueue = queueResults.stream()
                .anyMatch(result -> (!reserveFirstQueue || result.queueNumber() != 1)
                        && (result.state.status == UpgradeBuildingsRoutine.QueueMood.IDLE ||
                            result.state.status == UpgradeBuildingsRoutine.QueueMood.IDLE_TEMP));

        if (hasIdleQueue) {


            List<ProductionBlocker> productionBlockers = new ArrayList<>();
            Set<Integer> attemptedQueues = new java.util.HashSet<>();
            int nonPrioritySelections=0;

            for (UpgradeBuildingsRoutine.QueueReadout result : queueResults) {
                if (!reserveFirstQueue || result.queueNumber()!=1) {
                if (result.state.status == UpgradeBuildingsRoutine.QueueMood.IDLE ||
                        result.state.status == UpgradeBuildingsRoutine.QueueMood.IDLE_TEMP) {
                    logInfo(routineLogUpgradeBuildingsLine("Processing queue " + result.queueNumber + " (Status: " + result.state.status + ")"));
                    QueueHandlingResult handlingResult = handleQueue(result);

                    if (handlingResult.constructionAttempted()) {
                        attemptedQueues.add(result.queueNumber());
                    }
                    if (handlingResult.skippedByFocus()) nonPrioritySelections++;
                    if (handlingResult.blocker() != null) {
                        productionBlockers.add(handlingResult.blocker());
                    }
                }
                }
            }

            recoverLocation(LaunchPoint.HOME);

            logInfo(routineLogUpgradeBuildingsLine("Reanalyzing queues after processing idle queues..."));

            reachCityView();


            List<UpgradeBuildingsRoutine.QueueReadout> updatedResults = inspectAllQueues();


            logInfo(routineLogUpgradeBuildingsLine("=== Updated Queue Analysis After Processing ==="));
            logQueueSummaryFlow(updatedResults);
            clearConstructionReservationWhenStarted(updatedResults);


            if (furnacePriority && nonPrioritySelections>0
                    && attemptedQueues.isEmpty() && productionBlockers.isEmpty()) {
                // A non-chain recommendation is not a failed upgrade: no
                // hidden retries, no extra taps, no false construction success.
                LocalDateTime next=LocalDateTime.now().plusMinutes(30);
                markWorkIncomplete("Furnace Priority: recommended building was not "
                        + "the Furnace or a verified chain prerequisite; no construction started");
                reschedule(next);
                marchHelper.closeLeftMenu();
                return;
            }
            if (!productionBlockers.isEmpty()) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime trainingHandoff = productionBlockers.stream()
                        .map(ProductionBlocker::completionTime)
                        .min(LocalDateTime::compareTo)
                        .orElse(now)
                        .plusSeconds(COMPLETION_SETTLE_SECONDS);
                Optional<LocalDateTime> constructionSlot = earliestConstructionSlot(updatedResults, now);
                LocalDateTime retryAt = constructionSlot
                        .map(slot -> CityUpgradeSchedule.earliest(trainingHandoff, slot))
                        .orElse(trainingHandoff);
                logInfo(routineLogUpgradeBuildingsLine(
                        "Next visit at " + retryAt
                                + "; training handoff=" + trainingHandoff
                                + "; construction slot=" + constructionSlot.map(LocalDateTime::toString).orElse("none")));
                this.reschedule(retryAt);
                marchHelper.closeLeftMenu();
                return;
            }

            if (rescheduleForRetainedReservation(attemptedQueues, updatedResults)) {
                marchHelper.closeLeftMenu();
                return;
            }


            deferBasedOnBusyQueues(updatedResults);
            marchHelper.closeLeftMenu();
        } else {
            if (rescheduleForRetainedReservation(Set.of(), queueResults)) {
                marchHelper.closeLeftMenu();
                return;
            }
            deferBasedOnBusyQueues(queueResults);
        }
    }

@Override
    protected LaunchPoint getRequiredStartLocation() {
        return LaunchPoint.HOME;
    }

enum QueueMood {
        IDLE,

        BUSY,

        NOT_PURCHASED,

        IDLE_TEMP,

        UNKNOWN

    }

private record QueueSnapshot(UpgradeBuildingsRoutine.QueueMood status, String timeRemaining) {
    }

private record QueueReadout(int queueNumber, AreaData queueArea, UpgradeBuildingsRoutine.QueueSnapshot state) {

        @Override
        public @NotNull String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Queue ").append(queueNumber).append(": ");
            sb.append(state.status);
            if (state.timeRemaining != null) {
                sb.append(" (").append(state.timeRemaining).append(")");
            }
            return sb.toString();
        }
    }

private record ProductionBlocker(Set<ConstructionBlockerRegistry.Consumer> consumers,
        int constructionQueue, LocalDateTime completionTime) {
    }

private record QueueHandlingResult(boolean constructionAttempted,
        ProductionBlocker blocker, boolean skippedByFocus) {
    }

private String routineLogUpgradeBuildingsLine(String note) {
        return "UpgradeBuildingsRoutine | " + note;
    }

static boolean mayAutomaticallyReplenish(dev.frostguard.engine.emulator.EmulatorType backend) {
        // Same fail-closed policy as ResearchRoutine: on Serve-Emu the "Obtain more"
        // dialog includes a VIP Shop Go action near the former hardcoded confirmation.
        // A static tap at (511,1056) opened VIP Shop on 2026-11-20. No resources
        // or purchases are authorized without a separately verified safe UI flow.
        return backend != dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU;
    }

/**
 * Serve-Emu only: honour the per-profile opt-in for owned resource packs.
 * Never tap the old (511,1056) confirmation; this was VIP Shop on 20 Nov.
 * An enabled BLUE/GREEN Replenish All is not yet proof of enough inventory,
 * so require the precise Obtain More title, fresh-frame color agreement
 * and OCR of the exact button label BEFORE a single attempt. No follow-up
 * purchase/confirm click is authorized; unknown transitions are deferred.
 */
private boolean refillUsingOwnedPacksOnly() {
    checkPreemption();
    RawImageData current = emuManager.captureScreen(EMULATOR_NUMBER);
    boolean obtainMore = CityOwnedResourceButtonFrame.isObtainMore(current);
    ImageSearchResultData obtain = obtainMore ? ImageSearchResultData.miss()
            : templateSearchHelper.locatePattern(
                    GAME_HOME_SHORTCUTS_OBTAIN, SearchConfigConstants.DEFAULT_SINGLE);
    if (!obtainMore && !obtain.isFound()) {
        return true;
    }

    boolean useOwned = Boolean.TRUE.equals(profile.getConfig(
            ConfigurationKeyEnum.CITY_UPGRADE_USE_OWNED_RESOURCE_ITEMS_BOOL, Boolean.class));
    // Furnace Priority shares the owned-item option but never inherits
    // paid City Upgrade authorization. Gem spending must remain explicit.
    boolean useDiamonds = !furnacePriority
            && dev.frostguard.tasks.security.TaskGemSpendPolicy.allowed(
                    profile, TpDailyTaskEnum.CITY_UPGRADE_FURNACE);
    if (!useOwned && !useDiamonds) {
        logInfo(routineLogUpgradeBuildingsLine(
                "Both owned resource items and diamond replenishment disabled: deferring"));
        if (obtainMore) pressBack();
        return false;
    }
    if (!obtainMore) {
        if (!tapInside(obtain)) return false;
        sleepTask(350);
    }

    RawImageData first = emuManager.captureScreen(EMULATOR_NUMBER);
    if (!CityOwnedResourceButtonFrame.isObtainMore(first)) {
        logWarning(routineLogUpgradeBuildingsLine(
                "Obtain more page not confirmed: refusing any resource or shop tap"));
        return false;
    }
    if (!CityOwnedResourceButtonFrame.hasEnabledResourceButton(first) || !useOwned) {
        if (useDiamonds) {
            return replenishMissingResourcesWithDiamonds(first);
        }
        logInfo(routineLogUpgradeBuildingsLine(
                "Resource packs exhausted or item use disabled: Replenish All grey; "
                        + "diamond spending OFF, no VIP Shop, or false consumption report"));
        pressBack();
        return false;
    }
    sleepTask(170);
    RawImageData second = emuManager.captureScreen(EMULATOR_NUMBER);
    if (!CityOwnedResourceButtonFrame.hasEnabledResourceButton(second)) {
        logWarning(routineLogUpgradeBuildingsLine(
                "Replenish button was not confirmed active on two frames; no tap"));
        pressBack();
        return false;
    }

    String text;
    try {
        text = emuManager.readText(EMULATOR_NUMBER,
                new PointData(232, 1095), new PointData(500, 1168), WHITE_SETTINGS, false);
    } catch (Exception recognitionError) {
        CityUpgradeFlow.rethrowControlSignal(recognitionError);
        logWarning(routineLogUpgradeBuildingsLine(
                "Replenish All text OCR unavailable; no resource item used"));
        pressBack();
        return false;
    }
    if (!verifiedOwnedPackButtonText(text)) {
        logWarning(routineLogUpgradeBuildingsLine(
                "Enabled button not positively labelled Replenish All (OCR="
                        + String.valueOf(text).replaceAll("[^a-zA-Z ]", "") + "); no tap"));
        pressBack();
        return false;
    }

    logInfo(routineLogUpgradeBuildingsLine(
            "Verified available owned-item Replenish All; one tap only; "
                    + "no gems and no blind confirmation"));
    tapNear(new PointData(360, 1135));
    sleepTask(600);
    RawImageData after = emuManager.captureScreen(EMULATOR_NUMBER);
    if (CityOwnedResourceButtonFrame.isObtainMore(after)) {
        if (useDiamonds && !CityOwnedResourceButtonFrame.hasEnabledResourceButton(after)
                && CityGemReplenishButtonFrame.hasGoldenBuyAndUse(after)) {
            logInfo(routineLogUpgradeBuildingsLine(
                    "Owned resource items applied, a shortage remains; checking consented diamond quote"));
            return replenishMissingResourcesWithDiamonds(after);
        }
        logWarning(routineLogUpgradeBuildingsLine(
                "Obtain more still visible after owned-item attempt; no other taps or "
                        + "unverified resource success; deferring"));
        pressBack();
        return false;
    }
    // Never claim success without a verified building confirmation. The
    // caller's confirmDetectedBuildingUpgrade checks the actual next action.
    ImageSearchResultData upgrade = locateOnFrame(after, GAME_HOME_SHORTCUTS_UPGRADE_TEXT,
            BUILDING_CONFIRM_UPGRADE_SEARCH_AREA_VALUE, UPGRADE_CONFIRMATION_THRESHOLD);
    if (!upgrade.isFound()) {
        logWarning(routineLogUpgradeBuildingsLine(
                "No verified upgrade action after owned-resource attempt; "
                        + "no payment confirmation, deferring"));
        pressBack();
        return false;
    }
    logInfo(routineLogUpgradeBuildingsLine(
            "Owned-resource step returned to verified Upgrade control; proceeding"));
    return true;
}

/**
 * A guarded paid alternative to the disabled Replenish All control.
 *
 * On 20 November the 720x1280 Obtain more screen offered 10K secured Iron
 * for 100 diamonds while displaying 14,597/21,000 Iron. Exactly one
 * verified pack covers that 6,403 deficit. This path is opt-in only.
 *
 * Only the top resource card's GOLD Buy & Use may be tapped. VIP Shop is
 * lower on the page, and the old blind (511,1056) is never reused. Every
 * paid tap reserves its price DURABLY before dispatch, so unknown results
 * cannot trigger repeated blind spending across scheduled retries.
 */
private boolean replenishMissingResourcesWithDiamonds(RawImageData initial) {
    if (furnacePriority) {
        logInfo("Furnace Priority: gem spending is not authorized; only owned items allowed");
        pressBack();
        return false;
    }
    if (!dev.frostguard.tasks.security.TaskGemSpendPolicy.allowed(
            profile, TpDailyTaskEnum.CITY_UPGRADE_FURNACE)) {
        pressBack();
        return false;
    }
    Integer configuredLimit = profile.getConfig(
            ConfigurationKeyEnum.CITY_UPGRADE_GEM_DAILY_LIMIT_INT, Integer.class);
    int dailyLimit = configuredLimit == null ? 0 : configuredLimit;
    if (dailyLimit < 1 || dailyLimit > 10_000) {
        logInfo(routineLogUpgradeBuildingsLine("City diamonds require a valid daily limit (1..10000); no purchase"));
        pressBack();
        return false;
    }
    try {
        int budgetRemaining = CityGemSpendLedger.remaining(profile.getId(), dailyLimit);
        if (budgetRemaining < 1) {
            logInfo(routineLogUpgradeBuildingsLine("City diamond budget exhausted; waiting for new day"));
            pressBack();
            return false;
        }
        for (int i = 0; i < MAX_RESOURCE_REPLENISHMENTS; i++) {
            checkPreemption();
            RawImageData before = i == 0 ? initial : emuManager.captureScreen(EMULATOR_NUMBER);
            if (!CityGemReplenishButtonFrame.hasGoldenBuyAndUse(before)) {
                logWarning(routineLogUpgradeBuildingsLine(
                        "Gem offer must show the exact Obtain more title and gold Buy & Use, no tap"));
                return false;
            }
            java.util.Optional<CityGemReplenishQuote.Quote> candidate =
                    readVerifiedCityGemQuote(budgetRemaining);
            if (candidate.isEmpty()) {
                logWarning(routineLogUpgradeBuildingsLine(
                        "Refusing gem purchase: inventory deficit, pack size, wallet, "
                                + "price or Buy & Use label could not all be verified"));
                pressBack();
                return false;
            }
            CityGemReplenishQuote.Quote quote = candidate.get();
            if (quote.packsNeeded() > MAX_RESOURCE_REPLENISHMENTS - i) {
                logInfo(routineLogUpgradeBuildingsLine(
                        "Missing resource needs more packs than this bounded run supports; no partial purchase"));
                pressBack();
                return false;
            }
            sleepTask(190);
            RawImageData confirm = emuManager.captureScreen(EMULATOR_NUMBER);
            if (!CityGemReplenishButtonFrame.hasGoldenBuyAndUse(confirm)
                    || !sameCityGemQuote(quote, readVerifiedCityGemQuote(budgetRemaining))) {
                logWarning(routineLogUpgradeBuildingsLine(
                        "Diamond price/deficit offer changed between captures; refusing purchase"));
                pressBack();
                return false;
            }
            if (!dev.frostguard.tasks.security.TaskGemSpendPolicy.allowed(
                    profile, TpDailyTaskEnum.CITY_UPGRADE_FURNACE)) {
                logInfo(routineLogUpgradeBuildingsLine("Diamond permission disabled before final tap"));
                pressBack();
                return false;
            }
            if (!CityGemSpendLedger.reserve(profile.getId(), dailyLimit, quote.unitGems())) {
                logWarning(routineLogUpgradeBuildingsLine(
                        "Diamond reservation rejected by persistent daily budget; no purchase"));
                pressBack();
                return false;
            }
            logInfo(routineLogUpgradeBuildingsLine(
                    "City owned packs unavailable; verified " + quote.resource()
                            + " deficit=" + quote.deficit() + ", item=" + quote.packSize()
                            + ", minimal packs=" + quote.packsNeeded()
                            + ", price=" + quote.unitGems() + " diamonds each. "
                            + "One gem purchase reserved, tapping top Buy & Use only"));
            tapNear(new PointData(580, 382));
            sleepTask(750);
            RawImageData after = emuManager.captureScreen(EMULATOR_NUMBER);
            if (CityOwnedResourceButtonFrame.isObtainMore(after)) {
                // The purchase may stay on the source page. Verify that the
                // *same* resource quantity increased before considering more.
                String currentRatio = readCityGemText(280, 239, 495, 291,
                        OcrSettingsData.builder().textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
                                .allowedGlyphs("0123456789,/ ").build());
                var progress = CityGemReplenishQuote.readProgress(currentRatio);
                if (progress.isEmpty()
                        || progress.get().have() < Math.min(quote.required(), quote.have() + quote.packSize())
                        || progress.get().required() != quote.required()) {
                    logWarning(routineLogUpgradeBuildingsLine(
                            "Paid Buy & Use outcome not proven by updated resource count; "
                                    + "reserved diamonds remain accounted, no further purchase"));
                    pressBack();
                    return false;
                }
                if (progress.get().have() >= quote.required()) {
                    logInfo(routineLogUpgradeBuildingsLine(
                            "Verified resource top-up covers current shortage; closing Obtain more"));
                    pressBack();
                    sleepTask(250);
                    return true;
                }
                // Another pack is still genuinely required; new live quote and
                // remaining day budget are revalidated on the next iteration.
                budgetRemaining = CityGemSpendLedger.remaining(profile.getId(), dailyLimit);
                continue;
            }
            // Some game versions immediately close Obtain more on Buy & Use.
            // Only a verified Upgrade action is a safe postcondition.
            ImageSearchResultData upgrade = locateOnFrame(after, GAME_HOME_SHORTCUTS_UPGRADE_TEXT,
                    BUILDING_CONFIRM_UPGRADE_SEARCH_AREA_VALUE, UPGRADE_CONFIRMATION_THRESHOLD);
            if (upgrade.isFound() && quote.packsNeeded() == 1) {
                logInfo(routineLogUpgradeBuildingsLine(
                        "Top-up returned to verified building Upgrade control; no further paid tap"));
                return true;
            }
            logWarning(routineLogUpgradeBuildingsLine(
                    "Buy & Use returned to an unverified page; cannot confirm resources or purchase, "
                            + "no further taps. Daily reservation retained"));
            return false;
        }
    } catch (java.io.IOException budgetProblem) {
        logWarning(routineLogUpgradeBuildingsLine(
                "City diamond ledger unavailable: " + budgetProblem.getMessage() + "; no purchase"));
    } catch (Exception recognitionError) {
        CityUpgradeFlow.rethrowControlSignal(recognitionError);
        logWarning(routineLogUpgradeBuildingsLine(
                "Paid resource quote not safely verified: " + recognitionError.getMessage()
                        + "; no automatic confirmation or retry"));
    }
    return false;
}

private java.util.Optional<CityGemReplenishQuote.Quote> readVerifiedCityGemQuote(int budget) throws Exception {
    String ratio = readCityGemText(280, 239, 495, 291,
            OcrSettingsData.builder().textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
                    .allowedGlyphs("0123456789,/ ").build());
    String product = readCityGemText(161, 330, 465, 380,
            OcrSettingsData.builder().textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build());
    String gives = readCityGemText(162, 371, 480, 423,
            OcrSettingsData.builder().textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build());
    String button = readCityGemText(513, 348, 663, 401,
            OcrSettingsData.builder().textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
                    .allowedGlyphs("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ &").build());
    String price = readCityGemText(573, 394, 653, 446,
            OcrSettingsData.forNumberRecognition());
    String wallet = readCityGemText(602, 7, 672, 57,
            OcrSettingsData.forNumberRecognition());
    return CityGemReplenishQuote.parse(ratio, product, gives, button, price, wallet, budget);
}

private String readCityGemText(int x1, int y1, int x2, int y2, OcrSettingsData settings) throws Exception {
    String value = emuManager.readText(EMULATOR_NUMBER,
            new PointData(x1, y1), new PointData(x2, y2), settings, false);
    return value == null ? "" : value.trim();
}

private static boolean sameCityGemQuote(CityGemReplenishQuote.Quote first,
                                         java.util.Optional<CityGemReplenishQuote.Quote> second) {
    return second.isPresent() && first.resource().equals(second.get().resource())
            && first.have() == second.get().have()
            && first.required() == second.get().required()
            && first.packSize() == second.get().packSize()
            && first.unitGems() == second.get().unitGems()
            && first.packsNeeded() == second.get().packsNeeded();
}

static boolean verifiedOwnedPackButtonText(String text) {
    return text != null && text.toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z]", "").equals("replenishall");
}

private boolean refillResourcesIfNeededFlow() {
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            return refillUsingOwnedPacksOnly();
        }
        var result = RepeatedResourceReplenishmentFlow.run(
                new RepeatedResourceReplenishmentFlow.Ui() {
                    @Override
                    public PointData findReplenishAll() {
                        return foundPoint(templateSearchHelper.locatePattern(
                                REPLENISH_ALL_BUTTON, REPLENISH_BUTTON_RECHECK));
                    }

                    @Override
                    public PointData findObtain() {
                        return foundPoint(templateSearchHelper.locatePattern(
                                GAME_HOME_SHORTCUTS_OBTAIN, SearchConfigConstants.DEFAULT_SINGLE));
                    }

                    @Override
                    public void openObtain(PointData point) {
                        tapNear(point);
                        sleepTask(300);
                    }

                    @Override
                    public void replenishAndConfirm(PointData point) {
                        logInfo(routineLogUpgradeBuildingsLine("Refilling one missing resource for the upgrade..."));
                        tapNear(point);
                        sleepTask(300);
                        tapNear(REPLENISH_CONFIRM_POINT);
                        sleepTask(550);
                    }
                },
                MAX_RESOURCE_REPLENISHMENTS);

        if (result.ready()) {
            if (result.replenishedResources() > 0) {
                logInfo(routineLogUpgradeBuildingsLine(
                        "Replenished " + result.replenishedResources() + " missing resource(s)."));
            }
            return true;
        }

        logWarning(routineLogUpgradeBuildingsLine(
                "Resource replenishment did not finish (" + result.outcome()
                        + "). Skipping the building confirmation."));
        return false;
    }

private PointData foundPoint(ImageSearchResultData result) {
        return result != null && result.isFound() ? result.getPoint() : null;
    }

private FailureReason handleSurvivorBuilding() {
        logInfo(routineLogUpgradeBuildingsLine("Handling Survivor Building"));


        int limit = 100;
        ImageSearchResultData survivorUpgrade;

        while (!(survivorUpgrade = templateSearchHelper.locatePattern(BUILDING_SURVIVOR_BUTTON_UPGRADE,
                SearchConfigConstants.SINGLE_WITH_2_RETRIES)).isFound()) {
            tapInside(new PointData(560, 640), new PointData(650, 690), 1, 200);
            limit--;
            if (limit <= 0) {
                break;
            }
        }


        if (!survivorUpgrade.isFound()) {
            return FailureReason.CONTROL_NOT_RECOGNIZED;
        }
        tapInside(survivorUpgrade.getPoint(), survivorUpgrade.getPoint(), 1, 1000);


        if (!refillResourcesIfNeededFlow()) {
            return FailureReason.RESOURCES_NOT_OBTAINED;
        }


        tapInside(new PointData(450, 1190), new PointData(600, 1230), 1, 1000);


        if (tapAllianceHelp()) {
            sleepTask(500);
            tapInside(new PointData(540, 1200), new PointData(700, 1250), 1, 1000);
        }
        return null;
    }

private void deferBasedOnBusyQueues(List<QueueReadout> queueResults) {
        logInfo(routineLogUpgradeBuildingsLine("Inspecting construction queue outcomes to reschedule..."));


        QueueReadout shortestBusyQueue = queueResults.stream()
                .filter(result -> result.state.status == QueueMood.BUSY && result.state.timeRemaining != null)
                .min((q1, q2) -> {
                    long time1 = decodeTimeToMinutes(q1.state.timeRemaining);
                    long time2 = decodeTimeToMinutes(q2.state.timeRemaining);
                    return Long.compare(time1, time2);
                })
                .orElse(null);

        if (shortestBusyQueue != null) {
            long minutesToWait = decodeTimeToMinutes(shortestBusyQueue.state.timeRemaining);
            LocalDateTime rescheduleTime = CityUpgradeSchedule.constructionRetry(LocalDateTime.now(), minutesToWait);

            if (minutesToWait > 30) {
                logInfo(routineLogUpgradeBuildingsLine("Wait time exceeds 30 minutes (" + minutesToWait + " min). Planning next run for half time: " +
                        minutesToWait / 2 + " minutes from now"));
            } else if (minutesToWait < 5) {
                logInfo(routineLogUpgradeBuildingsLine("Wait time is less than 5 minutes. Keeping normal schedule: " +
                        minutesToWait + " minutes from now"));
            } else {
                logInfo(routineLogUpgradeBuildingsLine("Wait time is " + minutesToWait + " minutes. Using normal schedule"));
            }

            logInfo(routineLogUpgradeBuildingsLine("Shortest busy queue: Queue " + shortestBusyQueue.queueNumber +
                    " with " + shortestBusyQueue.state.timeRemaining + " remaining"));
            logInfo(routineLogUpgradeBuildingsLine("Planning next run task for: " + rescheduleTime));

            this.reschedule(rescheduleTime);
        } else {


            LocalDateTime rescheduleTime = LocalDateTime.now().plusHours(1);
            logWarning(routineLogUpgradeBuildingsLine("Zero BUSY queues with time information detected. Planning next run for 1 hour: " + rescheduleTime));
            this.reschedule(rescheduleTime);
        }
    }

private Optional<LocalDateTime> earliestConstructionSlot(List<QueueReadout> queues, LocalDateTime now) {
        return queues.stream()
                .filter(result -> result.state.status == QueueMood.BUSY && result.state.timeRemaining != null)
                .map(result -> decodeTimeToMinutes(result.state.timeRemaining))
                .min(Long::compare)
                .map(minutes -> CityUpgradeSchedule.constructionRetry(now, minutes));
    }

private void logQueueSummaryFlow(List<UpgradeBuildingsRoutine.QueueReadout> queueResults) {
        logInfo(routineLogUpgradeBuildingsLine("=== Queue Analysis Summary ==="));
        for (UpgradeBuildingsRoutine.QueueReadout result : queueResults) {
            logInfo(routineLogUpgradeBuildingsLine(result.toString()));
        }
    }

private ProductionBlocker readBusyProductionBuilding(int constructionQueue) {
        diagnostics.stage("training-clock");
        String buildingName = readSelectedBuildingName();
        String clockText = readTrainingClock();
        diagnostics.observation("training name='" + buildingName + "' clock='" + clockText + "'");
        // The recommended *Research Center* can be occupied by a running
        // research. Its blue Research/Speedup/Finish controls must never be
        // misinterpreted as an Upgrade. Treat a positively read active
        // countdown as a production handoff instead of retrying every 5 min.
        Duration researchTime = ResearchCenterBusyRead.positive(buildingName, clockText, false);
        if (researchTime != null) {
            LocalDateTime completionTime=LocalDateTime.now().plus(researchTime);
            Set<ConstructionBlockerRegistry.Consumer> consumers =
                    EnumSet.of(ConstructionBlockerRegistry.Consumer.RESEARCH);
            logInfo(routineLogUpgradeBuildingsLine(
                    "Research Center has active research ("+clockText+"); no building Upgrade control. "
                    + "Deferring City Upgrade until the research completes at "+completionTime));
            // Do not falsely reserve a construction queue for a *research*
            // countdown: the independent Research task must remain eligible.
            return new ProductionBlocker(consumers, constructionQueue, completionTime);
        }
        TrainingCampBusyRead.Decision decision = TrainingCampBusyRead.positive(buildingName, clockText, false);
        if (decision == null) {
            return null;
        }

        LocalDateTime completionTime = LocalDateTime.now().plus(decision.remaining());
        reserveConsumers(decision.camps(), constructionQueue, completionTime);
        logInfo(routineLogUpgradeBuildingsLine(
                "Training camp " + decision.camps() + " is busy; name='" + buildingName
                        + "'; clock='" + clockText
                        + "'; upgrade control absent. Next visit at " + completionTime
                        + ". Construction was not started."));
        return new ProductionBlocker(decision.camps(), constructionQueue, completionTime);
    }

private String readTrainingClock() {
        try {
            String text = emuManager.readText(
                    EMULATOR_NUMBER,
                    TrainingCampBusyRead.CLOCK_AREA.topLeft(),
                    TrainingCampBusyRead.CLOCK_AREA.bottomRight(),
                    CommonOCRSettings.MARCH_QUEUE_TIMER_SETTINGS,
                    true);
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            CityUpgradeFlow.rethrowControlSignal(e);
            logWarning(routineLogUpgradeBuildingsLine("Could not read the training clock: " + e.getMessage()));
            return "";
        }
    }

private ProductionBlocker handleProductionBlocker(int constructionQueue) {
        checkPreemption();
        RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
        ImageSearchResultData train = emuManager.locatePattern(EMULATOR_NUMBER, frame,
                BUILDING_BUTTON_TRAIN, SearchConfigConstants.DEFAULT_SINGLE.getThreshold());
        ImageSearchResultData research = train.isFound() ? null : emuManager.locatePattern(EMULATOR_NUMBER, frame,
                BUILDING_BUTTON_RESEARCH, SearchConfigConstants.DEFAULT_SINGLE.getThreshold());
        diagnostics.decision(frame, "threshold=" + SearchConfigConstants.DEFAULT_SINGLE.getThreshold()
                + "; train=" + train + "; research=" + (research == null ? "not-searched" : research));

        if (!train.isFound() && (research == null || !research.isFound())) {
            return null;
        }

        Set<ConstructionBlockerRegistry.Consumer> consumers;
        if (train.isFound()) {
            String buildingName = readSelectedBuildingName();
            ConstructionBlockerRegistry.Consumer identified = identifyTrainingConsumer(buildingName);
            if (identified == null) {
                consumers = EnumSet.of(
                        ConstructionBlockerRegistry.Consumer.INFANTRY,
                        ConstructionBlockerRegistry.Consumer.LANCER,
                        ConstructionBlockerRegistry.Consumer.MARKSMAN);
                logWarning(routineLogUpgradeBuildingsLine(
                        "Training building name was unreadable. Reserving all training queues as a safe fallback."));
            } else {
                consumers = EnumSet.of(identified);
            }
        } else {
            consumers = EnumSet.of(ConstructionBlockerRegistry.Consumer.RESEARCH);
        }

        ImageSearchResultData speedupButton = templateSearchHelper.locatePattern(BUILDING_BUTTON_SPEED,
                SearchConfigConstants.DEFAULT_SINGLE);
        if (!speedupButton.isFound()) {
            logWarning(routineLogUpgradeBuildingsLine(
                    "Production blocker detected, but its speedup button was not found. Retrying in 5 minutes."));
            LocalDateTime retryAt = LocalDateTime.now().plusMinutes(BLOCKER_RELEASE_GRACE_MINUTES);
            reserveConsumers(consumers, constructionQueue, retryAt);
            return new ProductionBlocker(consumers, constructionQueue, retryAt);
        }

        diagnostics.stage("production-timer");
        tapAround(speedupButton.getPoint(), TEMPLATE_TAP_RADIUS, 500);
        Duration remaining = durationHelper.attemptRecognition(
                new PointData(292, 284),
                new PointData(432, 314),
                5,
                300,
                null,
                GameTimeUtils::isAcceptedFormat,
                GameTimeUtils::parseDuration);

        if (remaining == null) {
            logWarning(routineLogUpgradeBuildingsLine(
                    "Could not read the production blocker timer. Retrying in 5 minutes."));
            LocalDateTime retryAt = LocalDateTime.now().plusMinutes(BLOCKER_RELEASE_GRACE_MINUTES);
            reserveConsumers(consumers, constructionQueue, retryAt);
            return new ProductionBlocker(consumers, constructionQueue, retryAt);
        }

        LocalDateTime completionTime = LocalDateTime.now().plus(remaining);
        reserveConsumers(consumers, constructionQueue, completionTime);
        logInfo(routineLogUpgradeBuildingsLine("Production blocker " + consumers
                + " completes at approximately " + completionTime
                + "; consumer remains reserved until construction start is verified"));
        return new ProductionBlocker(consumers, constructionQueue, completionTime);
    }

private String readSelectedBuildingName() {
        try {
            String text = emuManager.readText(
                    EMULATOR_NUMBER,
                    BUILDING_NAME_AREA_VALUE.topLeft(),
                    BUILDING_NAME_AREA_VALUE.bottomRight(),
                    WHITE_SETTINGS,
                    true).trim();
            logInfo(routineLogUpgradeBuildingsLine("Selected building name OCR: '" + text + "'"));
            return text;
        } catch (Exception e) {
            CityUpgradeFlow.rethrowControlSignal(e);
            logWarning(routineLogUpgradeBuildingsLine("Could not read selected building name: " + e.getMessage()));
            return "";
        }
    }

static ConstructionBlockerRegistry.Consumer identifyTrainingConsumer(String buildingName) {
        String normalized = buildingName == null
                ? ""
                : buildingName.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (normalized.contains("infantry")) {
            return ConstructionBlockerRegistry.Consumer.INFANTRY;
        }
        if (normalized.contains("lancer")) {
            return ConstructionBlockerRegistry.Consumer.LANCER;
        }
        if (normalized.contains("marksman")) {
            return ConstructionBlockerRegistry.Consumer.MARKSMAN;
        }
        return null;
    }

private void reserveConsumers(Set<ConstructionBlockerRegistry.Consumer> consumers, int constructionQueue,
        LocalDateTime retryAt) {
        ConstructionBlockerRegistry.reserve(profile, consumers, constructionQueue, retryAt);
}

private void clearConstructionReservationWhenStarted(List<QueueReadout> queueResults) {
        ConstructionBlockerRegistry.reservation(profile).ifPresent(reservation -> queueResults.stream()
                .filter(result -> result.queueNumber() == reservation.constructionQueue())
                .filter(result -> shouldReleaseReservation(result.state().status()))
                .findFirst()
                .ifPresent(result -> {
                    logInfo(routineLogUpgradeBuildingsLine(
                            "Reserved construction queue " + result.queueNumber()
                                    + " is BUSY; clearing production consumer lock "
                                    + reservation.consumers()));
                    ConstructionBlockerRegistry.clear(profile);
                }));
    }

static boolean shouldReleaseReservation(QueueMood reservedQueueState) {
        return reservedQueueState == QueueMood.BUSY;
}

static boolean shouldClearRetainedReservation(QueueMood reservedQueueState, boolean attempted, boolean expired) {
        return reservedQueueState == QueueMood.NOT_PURCHASED
                || attempted && (reservedQueueState == QueueMood.IDLE || reservedQueueState == QueueMood.IDLE_TEMP)
                || expired;
}

private boolean rescheduleForRetainedReservation(Set<Integer> attemptedQueues, List<QueueReadout> queueResults) {
        Optional<ConstructionBlockerRegistry.Reservation> retainedReservation =
                ConstructionBlockerRegistry.reservation(profile);
        if (retainedReservation.isEmpty()) {
            return false;
        }

        ConstructionBlockerRegistry.Reservation reservation = retainedReservation.get();
        LocalDateTime now = LocalDateTime.now();
        QueueMood reservedQueueState = queueResults.stream()
                .filter(result -> result.queueNumber() == reservation.constructionQueue())
                .map(result -> result.state().status())
                .findFirst()
                .orElse(QueueMood.UNKNOWN);

        boolean attempted = attemptedQueues.contains(reservation.constructionQueue());
        boolean expired = !reservation.retryAt().isAfter(now);
        if (shouldClearRetainedReservation(reservedQueueState, attempted, expired)) {
            String reason = reservedQueueState == QueueMood.NOT_PURCHASED
                    ? "reserved queue is not purchased"
                    : attempted && (reservedQueueState == QueueMood.IDLE || reservedQueueState == QueueMood.IDLE_TEMP)
                            ? "construction did not start on the reserved queue"
                            : "the reservation expired without construction start evidence";
            logWarning(routineLogUpgradeBuildingsLine(
                    "Clearing production consumer lock for queue " + reservation.constructionQueue()
                            + " because " + reason));
            ConstructionBlockerRegistry.clear(profile);
            return false;
        }
        String attemptEvidence = attempted
                ? "the construction attempt did not make the queue BUSY"
                : "the reserved queue did not provide start evidence";
        logWarning(routineLogUpgradeBuildingsLine(
                "Keeping production consumer lock because " + attemptEvidence
                        + ". Retrying construction at " + reservation.retryAt()));
        this.reschedule(reservation.retryAt());
        return true;
}

private void tapAround(PointData center, int radius, int delayMs) {
        tapInside(
                new PointData(center.getX() - radius, center.getY() - radius),
                new PointData(center.getX() + radius, center.getY() + radius),
                1,
                delayMs);
    }

private long decodeTimeToMinutes(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return 0;
        }

        try {
            long totalMinutes = 0;
            String timePart = timeString.trim();


            if (timePart.toLowerCase().contains("d")) {
                String[] daysPart = timePart.toLowerCase().split("d");
                if (daysPart.length > 0) {
                    String daysStr = daysPart[0].replaceAll("[^0-9]", "");
                    if (!daysStr.isEmpty()) {
                        int days = Integer.parseInt(daysStr);
                        totalMinutes += (long) days * 24 * 60;

                    }
                }


                if (daysPart.length > 1) {
                    timePart = daysPart[1].trim();
                } else {
                    return totalMinutes;
                }
            }


            timePart = timePart.replaceAll("[^0-9:]", "");

            if (timePart.isEmpty()) {
                return totalMinutes;
            }


            if (timePart.contains(":")) {


                String[] timeParts = timePart.split(":");
                if (timeParts.length >= 2) {


                    if (!timeParts[0].isEmpty()) {
                        int hours = Integer.parseInt(timeParts[0]);
                        totalMinutes += hours * 60L;
                    }


                    if (!timeParts[1].isEmpty()) {
                        int minutes = Integer.parseInt(timeParts[1]);
                        totalMinutes += minutes;
                    }
                }
            } else {


                if (timePart.length() >= 4) {


                    String hoursStr = timePart.substring(0, 2);
                    int hours = Integer.parseInt(hoursStr);
                    totalMinutes += hours * 60L;


                    String minutesStr = timePart.substring(2, 4);
                    int minutes = Integer.parseInt(minutesStr);
                    totalMinutes += minutes;
                }
            }

            return totalMinutes;

        } catch (Exception e) {
            CityUpgradeFlow.rethrowControlSignal(e);
            logError(routineLogUpgradeBuildingsLine("Error parsing time string '" + timeString + "': " + e.getMessage()));
            return 15;

        }
    }

private FailureReason startBuildingAction(String actionName, ImageSearchResultData upgrade) {
        diagnostics.stage("open-" + actionName + "-dialog");
        logInfo(routineLogUpgradeBuildingsLine("Starting building " + actionName + "; target=" + upgrade));
        if (upgrade != null) {
            if (!tapInside(upgrade)) {
                return FailureReason.CONTROL_NOT_RECOGNIZED;
            }
        } else {
            tapInside(BUILDING_ACTION_BUTTON_AREA_VALUE);
        }
        sleepTask(1000);

        diagnostics.stage("replenish-resources");
        if (!refillResourcesIfNeededFlow()) {
            return FailureReason.RESOURCES_NOT_OBTAINED;
        }

        diagnostics.stage("confirm-" + actionName);
        FailureReason failure = "upgrade".equals(actionName)
                ? confirmDetectedBuildingUpgrade()
                : confirmNewBuilding();
        if (failure != null) {
            return failure;
        }
        diagnostics.stage("alliance-help");
        tapAllianceHelp();
        return null;
    }

private FailureReason confirmDetectedBuildingUpgrade() {
        BuildingUpgradeConfirmationFlow.Outcome outcome = BuildingUpgradeConfirmationFlow.run(
                new BuildingUpgradeConfirmationFlow.Ui() {
                    @Override
                    public boolean tapDetectedUpgrade() {
                        ImageSearchResultData upgrade = locateConfirmationWithEvidence();
                        if (!upgrade.isFound()) {
                            return false;
                        }
                        logInfo(routineLogUpgradeBuildingsLine("Upgrade confirmation detected: " + upgrade));
                        diagnostics.stage("confirm-upgrade-transition");
                        return tapInside(upgrade);
                    }

                    @Override
                    public void waitForTransition() {
                        sleepTask(500);
                    }

                    @Override
                    public boolean isConfirmationPending() {
                        checkPreemption();
                        RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
                        ImageSearchResultData action = locateOnFrame(frame, GAME_HOME_SHORTCUTS_UPGRADE_TEXT,
                                BUILDING_CONFIRM_UPGRADE_SEARCH_AREA_VALUE, UPGRADE_CONFIRMATION_THRESHOLD);
                        ImageSearchResultData home = emuManager.locatePattern(EMULATOR_NUMBER, frame,
                                GAME_HOME_FURNACE, SearchConfigConstants.DEFAULT_SINGLE.getThreshold());
                        diagnostics.decision(frame, "confirmation=" + action + "; home=" + home);
                        return action.isFound() || !home.isFound();
                    }
                }, BUILDING_CONTROL_CHECKS);
        if (outcome == BuildingUpgradeConfirmationFlow.Outcome.CONFIRMED) {
            logInfo(routineLogUpgradeBuildingsLine("Building upgrade confirmed; Home anchor returned"));
            return null;
        }
        return outcome == BuildingUpgradeConfirmationFlow.Outcome.BUTTON_NOT_FOUND
                ? FailureReason.CONFIRMATION_NOT_FOUND
                : FailureReason.HOME_TRANSITION_NOT_CONFIRMED;
    }

private ImageSearchResultData locateConfirmationWithEvidence() {
        ImageSearchResultData result = ImageSearchResultData.miss();
        for (int attempt = 0; attempt < BUILDING_CONTROL_CHECKS; attempt++) {
            checkPreemption();
            RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
            result = locateOnFrame(frame, GAME_HOME_SHORTCUTS_UPGRADE_TEXT,
                    BUILDING_CONFIRM_UPGRADE_SEARCH_AREA_VALUE, UPGRADE_CONFIRMATION_THRESHOLD);
            diagnostics.decision(frame, "confirmation threshold=" + UPGRADE_CONFIRMATION_THRESHOLD + " " + result);
            if (result.isFound()) return result;
            if (attempt < BUILDING_CONTROL_CHECKS - 1) sleepTask(BUILDING_CONTROL_POLL_MS);
        }
        return result;
    }

private FailureReason confirmNewBuilding() {
        tapInside(new PointData(489, 1034), new PointData(500, 1050));
        sleepTask(500);
        checkPreemption();
        RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
        ImageSearchResultData home = emuManager.locatePattern(EMULATOR_NUMBER, frame,
                GAME_HOME_FURNACE, SearchConfigConstants.DEFAULT_SINGLE.getThreshold());
        diagnostics.decision(frame, "new-building home=" + home);
        return home.isFound() ? null : FailureReason.HOME_TRANSITION_NOT_CONFIRMED;
    }

private boolean isBuildButtonVisible() {
        try {
            emuManager.captureScreen(EMULATOR_NUMBER);
            String buttonText = emuManager.readText(
                    EMULATOR_NUMBER,
                    BUILDING_ACTION_BUTTON_AREA_VALUE.topLeft(),
                    BUILDING_ACTION_BUTTON_AREA_VALUE.bottomRight(),
                    WHITE_SETTINGS,
                    true);
            String normalized = buttonText == null ? "" : buttonText.toLowerCase().replaceAll("[^a-z]", "");
            diagnostics.observation("build OCR='" + normalized + "'");
            boolean detected = normalized.contains("build") || normalized.contains("bui");
            if (detected) {
                logInfo(routineLogUpgradeBuildingsLine("Build button detected via OCR: '" + buttonText + "'"));
            }
            return detected;
        } catch (Exception e) {
            CityUpgradeFlow.rethrowControlSignal(e);
            logWarning(routineLogUpgradeBuildingsLine("Build button OCR failed: " + e.getMessage()));
            return false;
        }
    }

private void logQueueStateFlow(int queueIndex, UpgradeBuildingsRoutine.QueueSnapshot state) {
        switch (state.status) {
            case IDLE:
                logInfo(routineLogUpgradeBuildingsLine("Queue " + queueIndex + " is IDLE - available for use"));
                break;
            case BUSY:
                logInfo(routineLogUpgradeBuildingsLine("Queue " + queueIndex + " is BUSY - Time remaining: " + state.timeRemaining));
                break;
            case NOT_PURCHASED:
                logInfo(routineLogUpgradeBuildingsLine("Queue " + queueIndex + " is NOT PURCHASED - needs to be acquired"));
                break;
            case IDLE_TEMP:
                logInfo(routineLogUpgradeBuildingsLine("Queue " + queueIndex + " is IDLE_TEMP - detected by orange color"));
                break;
            case UNKNOWN:
                logWarning(routineLogUpgradeBuildingsLine("Queue " + queueIndex + " state is UNKNOWN - OCR did not complete to detect state"));
                break;
        }
    }

private UpgradeBuildingsRoutine.QueueSnapshot inspectQueueState(AreaData queueArea) {
        try {
            SearchConfig idlePresence = SearchConfig.builder()
                    .withArea(queueArea).withThreshold(88).withMaxAttempts(1).build();
            // A busy construction queue correctly has NO Idle text; checking
            // that optional marker is not an actionable error. On Serve-Emu
            // use a single fast image probe before the authoritative OCR.
            ImageSearchResultData idle = emuManager.getSelectedEmulatorType()
                    == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU
                    ? templateSearchHelper.locatePatternFast(
                            TemplatesEnum.MARCH_QUEUE_STATUS_IDLE, idlePresence)
                    : templateSearchHelper.locatePattern(
                            TemplatesEnum.MARCH_QUEUE_STATUS_IDLE, idlePresence);
            if (idle.isFound()) {
                return new UpgradeBuildingsRoutine.QueueSnapshot(UpgradeBuildingsRoutine.QueueMood.IDLE, null);
            }


            OcrSettingsData[] settingsToTry = {
                    WHITE_SETTINGS,
                    WHITE_NUMBERS,
                    RED_SETTINGS,
                    ORANGE_SETTINGS,
            };

            for (OcrSettingsData ocrPreset : settingsToTry) {
                String ocrText = emuManager.readText(
                        EMULATOR_NUMBER,
                        queueArea.topLeft(),
                        queueArea.bottomRight(),
                        ocrPreset,
                        true).trim();

                logDebug(routineLogUpgradeBuildingsLine("OCR result with ocrPreset " + ocrPreset.getClass().getSimpleName() + ": '" + ocrText + "'"));


                if (ocrText.toLowerCase().contains("idle")) {

                    if (ocrPreset == ORANGE_SETTINGS) {
                        logDebug(routineLogUpgradeBuildingsLine("Orange 'idle' text detected - IDLE_TEMP"));
                        return new UpgradeBuildingsRoutine.QueueSnapshot(UpgradeBuildingsRoutine.QueueMood.IDLE_TEMP, null);
                    } else {
                        return new UpgradeBuildingsRoutine.QueueSnapshot(UpgradeBuildingsRoutine.QueueMood.IDLE, null);
                    }
                }


                if (ocrText.toLowerCase().contains("purchase") ||
                        ocrText.toLowerCase().contains("queue")) {
                    return new UpgradeBuildingsRoutine.QueueSnapshot(UpgradeBuildingsRoutine.QueueMood.NOT_PURCHASED, null);
                }


                if (ocrText.matches(".*(\\d+d\\s*)?\\d{6}.*")) {


                    String cleanedTime = ocrText.replaceAll("[^0-9d]", "").trim();
                    if (!cleanedTime.isEmpty()) {
                        return new UpgradeBuildingsRoutine.QueueSnapshot(UpgradeBuildingsRoutine.QueueMood.BUSY, cleanedTime);
                    }
                }
            }


            return new UpgradeBuildingsRoutine.QueueSnapshot(UpgradeBuildingsRoutine.QueueMood.UNKNOWN, null);

        } catch (Exception e) {
            CityUpgradeFlow.rethrowControlSignal(e);
            logError(routineLogUpgradeBuildingsLine("Issue while OCR analysis: " + e.getMessage()));
            return new UpgradeBuildingsRoutine.QueueSnapshot(UpgradeBuildingsRoutine.QueueMood.UNKNOWN, null);
        }
    }

private List<UpgradeBuildingsRoutine.QueueReadout> inspectAllQueues() {
        List<UpgradeBuildingsRoutine.QueueReadout> results = new ArrayList<>();

        try {


            emuManager.captureScreen(EMULATOR_NUMBER);

            int queueIndex = 1;
            for (AreaData queueArea : queues) {
                logInfo(routineLogUpgradeBuildingsLine("Analyzing queue " + queueIndex));


                UpgradeBuildingsRoutine.QueueSnapshot state = inspectQueueState(queueArea);


                UpgradeBuildingsRoutine.QueueReadout result = new UpgradeBuildingsRoutine.QueueReadout(
                        queueIndex, queueArea, state);
                results.add(result);


                logQueueStateFlow(queueIndex, state);

                queueIndex++;
            }


            List<UpgradeBuildingsRoutine.QueueReadout> unknownResults = results.stream()
                    .filter(result -> result.state.status == UpgradeBuildingsRoutine.QueueMood.UNKNOWN)
                    .collect(Collectors.toList());

            if (!unknownResults.isEmpty()) {
                logInfo(routineLogUpgradeBuildingsLine("Detected " + unknownResults.size()
                        + " queue(s) with UNKNOWN status. Retrying with a new screenshot."));


                emuManager.captureScreen(EMULATOR_NUMBER);


                List<UpgradeBuildingsRoutine.QueueReadout> updatedResults = new ArrayList<>();


                for (UpgradeBuildingsRoutine.QueueReadout originalResult : results) {
                    if (originalResult.state.status == UpgradeBuildingsRoutine.QueueMood.UNKNOWN) {


                        logInfo(routineLogUpgradeBuildingsLine("Retrying analysis for queue " + originalResult.queueNumber));
                        UpgradeBuildingsRoutine.QueueSnapshot newState = inspectQueueState(originalResult.queueArea);


                        UpgradeBuildingsRoutine.QueueReadout newResult = new UpgradeBuildingsRoutine.QueueReadout(
                                originalResult.queueNumber, originalResult.queueArea, newState);


                        updatedResults.add(newResult);


                        logInfo(routineLogUpgradeBuildingsLine("Queue " + originalResult.queueNumber + " reanalyzed. New state: " + newState.status));
                        logQueueStateFlow(originalResult.queueNumber, newState);
                    } else {


                        updatedResults.add(originalResult);
                    }
                }


                results = updatedResults;
            }
        } catch (Exception e) {
            CityUpgradeFlow.rethrowControlSignal(e);
            logError(routineLogUpgradeBuildingsLine("Error analyzing construction queues: " + e.getMessage()));
        }

        return results;
    }

private boolean tapAllianceHelp() {
        ImageSearchResultData help = templateSearchHelper.locatePatternMultiScale(
                GAME_HOME_SHORTCUTS_HELP_REQUEST4, SearchConfigConstants.HIGH_SENSITIVITY);

        if (help == null || !help.isFound()) {
            logWarning(routineLogUpgradeBuildingsLine("Alliance help button not detected"));
            return false;
        }

        tapInside(help.getPoint(), help.getPoint(), 1, 500);
        return true;
    }

private void reachCityView() {
        if (!navigationHelper.openSidebarSection(SidebarSection.CITY)) {
            throw new IllegalStateException("Could not open the City sidebar section");
        }
    }

private void closeVerifiedUpgradeBonusBeforeHome() {
    if (emuManager.getSelectedEmulatorType()
            != dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) return;
    RawImageData first=emuManager.captureScreen(EMULATOR_NUMBER);
    ImageSearchResultData close=CityUpgradeBonusExitFrame.verifiedClose(first);
    if(!close.isFound())return;
    sleepTask(160);
    ImageSearchResultData confirm=CityUpgradeBonusExitFrame.verifiedClose(
            emuManager.captureScreen(EMULATOR_NUMBER));
    if(!dev.frostguard.engine.helper.RecognitionConsensus.agrees(close,confirm,12))return;
    logInfo(routineLogUpgradeBuildingsLine(
            "Verified Upgrade Bonus close action; exiting direct to City before Home navigation"));
    tapInside(confirm);
    sleepTask(350);
}

private QueueHandlingResult handleQueue(QueueReadout queueResult) {
        return CityUpgradeFlow.handleQueue(queueResult.queueNumber(), new CityUpgradeFlow.QueueUi<>() {
            @Override
            public Attempt<QueueHandlingResult> attempt(int number) {
                diagnostics.begin(queueResult.queueNumber(), number);
                checkPreemption();
                Attempt<QueueHandlingResult> result = handleQueueAttempt(queueResult);
                if (result.failure() == null) {
                    logInfo(routineLogUpgradeBuildingsLine("Recommended building handled; queue="
                            + queueResult.queueNumber() + "; attempt=" + number
                            + "; outcome=" + (result.result().blocker() == null ? "construction-attempted" : "production-blocked")));
                }
                return result;
            }

            @Override
            public void retainFailure(int number, FailureReason reason) {
                retainDiagnostic(reason.name().toLowerCase(Locale.ROOT), number == 1);
            }

            @Override
            public void recoverHome() {
                closeVerifiedUpgradeBonusBeforeHome();
                recoverLocation(LaunchPoint.HOME);
            }
        });
    }

private Attempt<QueueHandlingResult> handleQueueAttempt(QueueReadout queueResult) {
        reachCityView();
        sleepTask(500);
        diagnostics.stage("open-recommended-building");
        tapInside(queueResult.queueArea);
        sleepTask(500);

        diagnostics.stage("recognize-building-control");
        // Poll without tapping: claiming completed production may leave no building selected.
        for (int poll = 0; poll < BUILDING_CONTROL_CHECKS; poll++) {
            checkPreemption();
            RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
            FurnacePanelDetector.Evidence furnace = FurnacePanelDetector.inspect(
                    (template, area, threshold) -> locateOnFrame(frame, template, area, threshold));
            diagnostics.decision(frame, furnace.toString());
            if (furnacePriority && !furnace.panelVisible()) {
                // The generic queue often recommends Iron Mine or Storehouse
                // while Furnace is busy. A focus run may NEVER upgrade those.
                // Two independent reads must identify a genuine furnace-chain
                // prerequisite before ANY Upgrade/Build action is attempted.
                String first=readSelectedBuildingName();
                sleepTask(150);
                String second=readSelectedBuildingName();
                boolean twiceNamed=FurnacePrioritySelectionPolicy.mayUseRecommendedBuilding(
                        false, first, second);
                boolean twiceEmbassy=false;
                if (!twiceNamed) {
                    // Title template is an independent fallback for a selected
                    // Embassy (real 15 Nov Serve-Emu reference), not a guess
                    // based on building position or any freehand coordinate.
                    RawImageData firstTitle=emuManager.captureScreen(EMULATOR_NUMBER);
                    if(FurnacePrioritySelectedBuildingFrame.embassy(firstTitle)) {
                        sleepTask(150);
                        twiceEmbassy=FurnacePrioritySelectedBuildingFrame.embassy(
                                emuManager.captureScreen(EMULATOR_NUMBER));
                    }
                }
                if (!twiceNamed && !twiceEmbassy) {
                    logInfo("Furnace Priority rejected recommended building; "
                            + "first='" + first + "', second='" + second
                            + "'. No upgrade/build button will be pressed.");
                    diagnostics.observation("furnace-focus-nonchain "
                            + FurnacePrioritySelectionPolicy.normalize(first));
                    return Attempt.completed(new QueueHandlingResult(false,null,true));
                }
                logInfo("Furnace Priority confirmed chain prerequisite "
                        + (twiceEmbassy ? "Embassy (dual-frame title)" :
                            FurnacePrioritySelectionPolicy.normalize(second)));
            }
            if (furnace.actionable()) {
                logInfo(routineLogUpgradeBuildingsLine("Furnace entry recognized; " + furnace));
                if (shouldPrepareDoubleTime(true)) {
                    doubleTimeAttempted = true;
                    // This exact Furnace Upgrade control was verified, and an
                    // idle construction queue exists. Prepare the five-minute
                    // buff only now, never on a busy/unrecognized building.
                    prepareDoubleTimeBeforeFurnaceUpgrade();
                    // Chief Order navigation changes the selected building;
                    // return to the same queue and revalidate the upgrade
                    // action. The boolean guard prevents a second enact.
                    return handleQueueAttempt(queueResult);
                }
                return buildingAttempt(startBuildingAction("upgrade", furnace.upgrade()));
            }

            ImageSearchResultData info = emuManager.locatePattern(EMULATOR_NUMBER, frame,
                    BUILDING_BUTTON_INFO, SearchConfigConstants.RESILIENT.getThreshold());
            ImageSearchResultData upgrade = info.isFound() && !furnacePriority ? null
                    : emuManager.locatePattern(EMULATOR_NUMBER, frame,
                    BUILDING_BUTTON_UPGRADE, SearchConfigConstants.RESILIENT.getThreshold());
            diagnostics.decision(frame, furnace + "; buildingControlThreshold="
                    + SearchConfigConstants.RESILIENT.getThreshold() + "; info=" + info + "; upgrade="
                    + (upgrade == null ? "not-searched" : upgrade));
            if (info.isFound() && !furnacePriority) {
                diagnostics.stage("survivor-building");
                tapNear(new PointData(info.getPoint().getX() + 100, info.getPoint().getY()));
                return buildingAttempt(handleSurvivorBuilding());
            }
            if (upgrade != null && upgrade.isFound()) {
                // New Furnace artwork sometimes matches its title but not
                // the Furnace-specific orange button. A separately verified
                // generic Upgrade button on the same Furnace frame is safe.
                if (shouldPrepareDoubleTime(furnace.panelVisible())) {
                    doubleTimeAttempted=true;
                    prepareDoubleTimeBeforeFurnaceUpgrade();
                    return handleQueueAttempt(queueResult);
                }
                return buildingAttempt(startBuildingAction("upgrade", upgrade));
            }
            // In current 720x1280 game artwork the guidance hand covers the
            // white Upgrade arrow, reducing template score below recognition.
            // Require both a precisely located Details control and the hand
            // pointing at its adjacent Upgrade before attempting this tap.
            if (!info.isFound() || furnacePriority) {
                CityUpgradeGuidanceDetector.Evidence guided = CityUpgradeGuidanceDetector.inspect(
                        (template, area, threshold) -> locateOnFrame(frame, template, area, threshold));
                diagnostics.decision(frame, "tutorial-hand upgrade=" + guided);
                if (guided.actionable()) {
                    logInfo(routineLogUpgradeBuildingsLine(
                            "Selected building Upgrade guided by confirmed hand+Details; " + guided));
                    if (shouldPrepareDoubleTime(furnace.panelVisible())) {
                        doubleTimeAttempted=true;
                        prepareDoubleTimeBeforeFurnaceUpgrade();
                        return handleQueueAttempt(queueResult);
                    }
                    return buildingAttempt(startBuildingAction("upgrade", guided.upgrade()));
                }
            }
            if (poll < BUILDING_CONTROL_CHECKS - 1) sleepTask(BUILDING_CONTROL_POLL_MS);
        }

        diagnostics.stage("recognize-build-control");
        if (isBuildButtonVisible()) {
            return buildingAttempt(startBuildingAction("build", null));
        }
        ProductionBlocker training = readBusyProductionBuilding(queueResult.queueNumber());
        if (training != null) {
            return Attempt.completed(new QueueHandlingResult(false, training, false));
        }
        diagnostics.stage("production-blocker");
        ProductionBlocker blocker = handleProductionBlocker(queueResult.queueNumber());
        return blocker == null
                ? Attempt.unresolved(FailureReason.CONTROL_NOT_RECOGNIZED)
                : Attempt.completed(new QueueHandlingResult(false, blocker, false));
    }

private boolean shouldPrepareDoubleTime(boolean furnaceConfirmed) {
    return furnaceConfirmed && !doubleTimeAttempted
            && Boolean.TRUE.equals(profile.getConfig(
                    ConfigurationKeyEnum.CITY_UPGRADE_DOUBLE_TIME_BOOL,Boolean.class));
}

/**
 * One bounded Chief Order attempt, immediately before a confirmed Furnace
 * upgrade. Insufficient points, cooldown or unreadable UI are normal skips;
 * construction continues, never uses gems and never retries Enact blindly.
 */
private void prepareDoubleTimeBeforeFurnaceUpgrade() {
    diagnostics.stage("double-time-preparation");
    checkPreemption();
    try {
        recoverLocation(LaunchPoint.HOME);
        ImageSearchResultData menu=templateSearchHelper.locatePattern(
                TemplatesEnum.CHIEF_ORDER_MENU_BUTTON,
                SearchConfig.builder().withMaxAttempts(1).withThreshold(90).build());
        if (menu == null || !menu.isFound()) {
            logInfo(routineLogUpgradeBuildingsLine("Double Time skipped: Chief Order menu not present"));
            return;
        }
        tapInside(menu);
        sleepTask(350);
        RawImageData board=emuManager.captureScreen(EMULATOR_NUMBER);
        if (!ChiefOrderLiveFrame.isChiefOrderList(board)) {
            logWarning(routineLogUpgradeBuildingsLine("Double Time skipped: order board unverified"));
            return;
        }
        var tile=ChiefOrderLiveFrame.inspectList(board,"Double Time");
        if (tile.state()==ChiefOrderLiveFrame.ListState.ACTIVE) {
            logInfo(routineLogUpgradeBuildingsLine("Double Time already active; proceeding to build"));
            return;
        }
        if (tile.state()!=ChiefOrderLiveFrame.ListState.AVAILABLE) {
            logInfo(routineLogUpgradeBuildingsLine("Double Time unavailable: "+tile.state()));
            return;
        }
        Long balance=ChiefOrderLiveFrame.availableOrderPoints(board);
        if(balance==null || balance<DoubleTimePolicy.REQUIRED_POINTS) {
            logInfo(routineLogUpgradeBuildingsLine(
                    "Double Time skipped: available Chief Order points insufficient/unreadable: "+balance));
            return;
        }

        // Middle-right Double Time tile, confirmed by its own title and
        // cooldown OCR above. Do not select another Chief Order.
        tapNear(new PointData(490,640),8);
        sleepTask(380);
        RawImageData book=emuManager.captureScreen(EMULATOR_NUMBER);
        var offer=ChiefOrderLiveFrame.inspect(book,"Double Time");
        if (offer.action()!=ChiefOrderLiveFrame.Action.COSTS_POINTS) {
            logWarning(routineLogUpgradeBuildingsLine(
                    "Double Time cost or identity not verified; no points spent: "+offer.action()));
            return;
        }
        ImageSearchResultData enact=templateSearchHelper.locatePattern(
                TemplatesEnum.CHIEF_ORDER_ENACT_BUTTON,
                SearchConfig.builder().withMaxAttempts(1).withThreshold(90)
                    .withArea(AreaData.of(205,860,525,975)).build());
        boolean allowed=DoubleTimePolicy.mayEnact(balance,offer.cost(),
                ChiefOrderLiveFrame.isOrderBook(book,"Double Time"),
                enact!=null && enact.isFound());
        if (!allowed) {
            logInfo(routineLogUpgradeBuildingsLine(
                    "Double Time skipped: price/balance/Enact safety check failed"));
            return;
        }
        logInfo(routineLogUpgradeBuildingsLine(
                "Double Time: one verified 800000-point Enact tap before Furnace upgrade"));
        tapInside(enact);
        sleepTask(600);
        // Never repeat a potentially successful point-spending tap. The
        // next action is to return Home and start the build promptly.
        RawImageData result=emuManager.captureScreen(EMULATOR_NUMBER);
        ImageSearchResultData reward=emuManager.locatePattern(EMULATOR_NUMBER,
                result,TemplatesEnum.SHARED_REWARDS_BANNER,
                new PointData(30,245),new PointData(690,400),90);
        if(reward!=null && reward.isFound()) {
            tapNear(new PointData(360,1190),8);
            sleepTask(280);
        }
    } catch (RuntimeException failure) {
        CityUpgradeFlow.rethrowControlSignal(failure);
        logWarning(routineLogUpgradeBuildingsLine(
                "Double Time attempt unavailable: "+failure.getClass().getSimpleName()));
    } finally {
        // Never return into an old Furnace dialog after Chief Order. Re-open
        // the building from a verified City root to prevent wrong clicks.
        recoverLocation(LaunchPoint.HOME);
    }
}

private Attempt<QueueHandlingResult> buildingAttempt(FailureReason failure) {
        return failure == null ? Attempt.completed(new QueueHandlingResult(true, null, false))
                : Attempt.unresolved(failure);
    }

private ImageSearchResultData locateOnFrame(RawImageData frame, TemplatesEnum template, AreaData area, int threshold) {
        return emuManager.locatePattern(EMULATOR_NUMBER, frame, template,
                area.topLeft(), area.bottomRight(), threshold);
    }

private void recoverLocation(LaunchPoint target) {
        checkPreemption();
        logInfo(routineLogUpgradeBuildingsLine("Recovering screen; target=" + target));
        diagnostics.stage("recover-" + target.name().toLowerCase(Locale.ROOT));
        LaunchPoint reached = navigationHelper.ensureCorrectScreenLocation(target, this::checkPreemption);
        logInfo(routineLogUpgradeBuildingsLine("Recovery confirmed; target=" + target + "; reached=" + reached));
    }

private void retainDiagnostic(String type, boolean retryable) {
        checkPreemption();
        String snapshot = diagnostics.retain(DiagnosticSnapshotStore::forCurrentWorkspace,
                () -> emuManager.captureScreen(EMULATOR_NUMBER), type);
        String message = routineLogUpgradeBuildingsLine("City Upgrade diagnostic; reason=" + type
                + "; " + diagnostics.context() + "; " + snapshot);
        if (retryable) {
            logDebug(message);
        } else {
            logWarning(message);
        }
    }
}
