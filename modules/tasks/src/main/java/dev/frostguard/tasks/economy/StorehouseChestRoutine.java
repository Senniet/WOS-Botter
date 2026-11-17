package dev.frostguard.tasks.economy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import dev.frostguard.vision.convert.GameTimeUtils;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.vision.convert.RegexNumberParser;
import dev.frostguard.vision.ocr.ResilientOcrExecutor;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.error.TaskOutcomeUnknownException;
import dev.frostguard.engine.service.StaminaService;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import dev.frostguard.engine.nav.SidebarDestination;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.nav.CommonOCRSettings;
import dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig;
import dev.frostguard.tasks.diagnostics.TaskControlSignals;

/**
 * Task responsible for claiming rewards from the Storehouse.
 * 
 * <p>
 * This task:
 * <ul>
 * <li>Navigates to the Storehouse via Research Center</li>
 * <li>Claims daily chest rewards (available every few hours)</li>
 * <li>Claims a visible stamina can on the same visit</li>
 * <li>Reads the on-building countdown via OCR to schedule the next chest visit</li>
 * </ul>
 * 
 * <p>
 * <b>Reward Types:</b>
 * <ul>
 * <li>Chest: General resources, multiple claims per day</li>
 * <li>Stamina: 120 base stamina + bonus from Agnes expert</li>
 * </ul>
 */
public class StorehouseChestRoutine extends DelayedTask {

    // ========== Navigation Coordinates ==========
    private static final AreaData STOREHOUSE_VISIBLE_BUILDING_AREA = new AreaData(
            new PointData(105, 530), new PointData(125, 550));
    private static final AreaData STOREHOUSE_TITLE_AREA = new AreaData(
            new PointData(245, 515), new PointData(505, 575));
    static final AreaData CHEST_REWARD_OVERLAY_CLOSE_AREA = AreaData.of(565, 530, 715, 705);
    private static final int STOREHOUSE_SELECTION_TIMEOUT_MILLIS = 2_000;
    private static final int STOREHOUSE_SELECTION_POLL_MILLIS = 140;
    private static final int STOREHOUSE_DESELECTION_SETTLE_MILLIS = 350;
    private static final AreaData MODERN_STOREHOUSE_NAME_AREA = AreaData.of(280, 510, 500, 580);
    private static final AreaData MODERN_STOREHOUSE_DETAILS_AREA = AreaData.of(290, 835, 480, 965);

    // ========== Stamina Reward Coordinates ==========
    private static final PointData STAMINA_AMOUNT_TOP_LEFT = new PointData(436, 632);
    private static final PointData STAMINA_AMOUNT_BOTTOM_RIGHT = new PointData(487, 657);
    // Current 2026 screen places the title lower than older captures. Keep
    // both placements inside the verified ROI rather than missing the dialog.
    private static final AreaData STAMINA_REWARD_TITLE_AREA = new AreaData(
            new PointData(150, 740), new PointData(560, 885));
    private static final AreaData STAMINA_CLAIM_BUTTON_AREA = new AreaData(
            new PointData(200, 900), new PointData(520, 1020));
    private static final AreaData STAMINA_TOOLTIP_TITLE_AREA = new AreaData(
            new PointData(45, 670), new PointData(300, 750));

    // ========== Fallback Timer OCR ==========
    static final PointData FALLBACK_TIMER_TOP_LEFT = new PointData(285, 642);
    static final PointData FALLBACK_TIMER_BOTTOM_RIGHT = new PointData(430, 666);

    // ========== Constants ==========
    private static final int TIMER_OCR_MAX_ATTEMPTS = 3;
    private static final int BUBBLE_SEARCH_ATTEMPTS = 3;
    private static final long BUBBLE_SEARCH_DELAY_MILLIS = 180L;
    private static final int CLAIM_CLOSE_SETTLE_MILLIS = 800;
    private static final int BUBBLE_CONFIRMATION_MARGIN = 24;
    private static final int CLAIM_BUTTON_POLL_INTERVAL_MILLIS = 250;
    private static final int CLAIM_BUTTON_TIMEOUT_MILLIS = 8_000;
    private static final int REWARD_TITLE_THRESHOLD = 88;
    private static final int CLAIM_BUTTON_THRESHOLD = 88;
    private static final int TOOLTIP_TITLE_THRESHOLD = 88;
    private static final String BUILDING_COUNTDOWN_WHITELIST = "0123456789:d";
    private static final int BASE_STOREHOUSE_STAMINA = 120;
    private static final AreaData STAMINA_CAPACITY_NOTICE_AREA = AreaData.of(40, 300, 680, 430);
    private static final AreaData STAMINA_DIALOG_CLOSE_AREA = AreaData.of(560, 135, 665, 235);
    private static final int STAMINA_CAPACITY_NOTICE_THRESHOLD = 88;
    private static final int STAMINA_DIALOG_CLOSE_THRESHOLD = 85;

    // On-building countdown glyphs measured on a 720x1280 city frame.
    static final Color BUILDING_TIMER_GREEN = new Color(61, 216, 13);

    // ========== OCR Settings ==========
    private static final OcrSettingsData STAMINA_OCR_SETTINGS = OcrSettingsData.assembler()
            .setTextColor(new Color(248, 247, 234))
            .stripBackground(true)
            .charWhitelist("0123456789")
            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)

            .build();

    private ResilientOcrExecutor<String> textHelper;
    private StorehouseVisitFlow.VisitState visitState = StorehouseVisitFlow.VisitState.READY;
    private StorehouseVisitFlow.ActivityPhase activityPhase = StorehouseVisitFlow.ActivityPhase.RESCHEDULING;
    private Integer pendingAgnesStamina;
    private boolean confirmedDailyMissionProgress;

    public StorehouseChestRoutine(AccountDescriptor profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    @Override
    protected boolean acceptsInjections() {
        return false;
    }

    @Override
    protected void execute() {
        this.textHelper = new ResilientOcrExecutor<>(provider);
        pendingAgnesStamina = null;
        confirmedDailyMissionProgress = false;
        setVisitState(StorehouseVisitFlow.VisitState.READY, "New execute() visit; state recalculated in memory");

        StorehouseVisitFlow.VisitDecision decision;
        try {
            if (openStorehouse()) {
                decision = StorehouseVisitFlow.execute(new StorehouseActions());
            } else {
                captureInterfaceFailure("storehouse-open");
                decision = StorehouseVisitFlow.retryBeforeFlow("Storehouse could not be opened.");
            }
        } catch (RuntimeException failure) {
            TaskControlSignals.rethrowControlSignal(failure);
            logError("Storehouse visit failed unexpectedly; retrying in five minutes: " + failure.getMessage());
            captureInterfaceFailure("visit-error");
            decision = StorehouseVisitFlow.retryBeforeFlow("Unexpected visit error: "
                    + failure.getClass().getSimpleName());
        }

        confirmedDailyMissionProgress = hasConfirmedProgress(
                decision.confirmedChestCollections(), decision.confirmedStaminaCollections());
        setActivityPhase(StorehouseVisitFlow.ActivityPhase.RESCHEDULING);
        setVisitState(decision.state(), decision.reason());
        LocalDateTime scheduledTime = LocalDateTime.now().plus(decision.delay());
        logInfo(String.format("Storehouse visit state %s; %s. Confirmed collections: chest=%d, stamina=%d. "
                        + "Rescheduling once for %s at %s.",
                visitState, decision.reason(), decision.confirmedChestCollections(),
                decision.confirmedStaminaCollections(), decision.delay(),
                scheduledTime.format(DATETIME_FORMATTER)));
        reschedule(scheduledTime);
        if (shouldPropagateUnknownOutcome(decision.state(), confirmedDailyMissionProgress)) {
            throw new TaskOutcomeUnknownException(
                    "Storehouse visit ended with an unconfirmed outcome; bounded retry scheduled without recording completion.");
        }
    }

    /**
     * Opens the Storehouse from the stable Research Center sidebar anchor.
     */
    private boolean openStorehouse() {
        logDebug("Navigating to Storehouse");

        if (!navigationHelper.navigateToSidebarDestination(SidebarDestination.RESEARCH_CENTER)) {
            logError("Research Center sidebar destination not reached.");
            return false;
        }

        // A previous selection can survive navigation. First inspect the
        // current screen; never tap a different building when Storehouse is
        // already visibly selected.
        boolean alreadySelected = isStorehouseSelectedModern();
        if (!alreadySelected) {
            // Legacy city coordinate is only a candidate: verify that this
            // selected object is really Storehouse AND displays Details.
            tapInside(STOREHOUSE_VISIBLE_BUILDING_AREA.topLeft(), STOREHOUSE_VISIBLE_BUILDING_AREA.bottomRight());
        }

        if (alreadySelected || waitUntilStorehouseSelected()) {
            logInfo("Storehouse selected and verified by its title (template or confirmed OCR).");
            // The reward bubbles are only actionable after Android Back closes the
            // building's Details/Upgrade selection controls.
            pressBack();
            sleepTask(STOREHOUSE_DESELECTION_SETTLE_MILLIS);
            logInfo("Storehouse selection controls closed; reward bubbles are now accessible.");
            return true;
        }

        logWarning("Storehouse title anchor was not detected after the direct selection.");
        return false;
    }

    /**
     * Current account captures show a visible Storehouse title even when the
     * older selectedCurrent template misses because of lighting/season.
     * Require the full building name on two fresh frames; single OCR noise
     * must never authorize reward collection or pressing Back.
     */
    private boolean isStorehouseSelectedModern() {
        // Separate UI labels at separate locations reduce the likelihood of
        // approving an unrelated selected building or a partial city overlay.
        SearchConfig name = SearchConfig.builder().withMaxAttempts(1).withThreshold(90)
                .withArea(MODERN_STOREHOUSE_NAME_AREA).build();
        SearchConfig details = SearchConfig.builder().withMaxAttempts(1).withThreshold(90)
                .withArea(MODERN_STOREHOUSE_DETAILS_AREA).build();
        return templateSearchHelper.locateAction(
                TemplatesEnum.STOREHOUSE_SELECTED_NAME_MODERN, name).isFound()
            && templateSearchHelper.locateAction(
                TemplatesEnum.STOREHOUSE_SELECTED_DETAILS_MODERN, details).isFound();
    }

    private boolean waitUntilStorehouseSelected() {
        long started = System.nanoTime();
        long deadline = started + STOREHOUSE_SELECTION_TIMEOUT_MILLIS * 1_000_000L;
        do {
            if (isStorehouseSelectedModern()) {
                logInfo("Storehouse visual selection ready in "
                        + ((System.nanoTime() - started) / 1_000_000L) + " ms");
                return true;
            }
            if (System.nanoTime() >= deadline) break;
            sleepTask(STOREHOUSE_SELECTION_POLL_MILLIS);
        } while (true);
        // Keep OCR support for a future Storehouse artwork revision.
        return isStorehouseSelected();
    }

    private boolean isStorehouseSelected() {
        ImageSearchResultData template = templateSearchHelper.locatePattern(
                TemplatesEnum.STOREHOUSE_SELECTED_CURRENT,
                SearchConfig.builder()
                        .withArea(STOREHOUSE_TITLE_AREA)
                        .withThreshold(85)
                        .withMaxAttempts(2)
                        .withDelay(250L)
                        .build());
        if (template.isFound()) return true;
        try {
            String first = emuManager.readText(EMULATOR_NUMBER,
                    STOREHOUSE_TITLE_AREA.topLeft(), STOREHOUSE_TITLE_AREA.bottomRight(),
                    CommonOCRSettings.SHOP_TAB_TEXT_SETTINGS, false);
            if (!isStorehouseTitle(first)) return false;
            sleepTask(200);
            String second = emuManager.readText(EMULATOR_NUMBER,
                    STOREHOUSE_TITLE_AREA.topLeft(), STOREHOUSE_TITLE_AREA.bottomRight(),
                    CommonOCRSettings.SHOP_TAB_TEXT_SETTINGS, false);
            if (isStorehouseTitle(second)) {
                logInfo("Storehouse title confirmed via independent OCR frames.");
                return true;
            }
        } catch (Exception error) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException("Storehouse OCR interrupted");
            }
            logDebug("Storehouse title OCR unavailable: " + error.getClass().getSimpleName());
        }
        return false;
    }

    static boolean isStorehouseTitle(String text) {
        if (text == null) return false;
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return normalized.contains("storehouse");
    }

    private final class StorehouseActions implements StorehouseVisitFlow.Actions<StorehouseBubbleDetector.Candidate> {

        @Override
        public StorehouseVisitFlow.Observation<StorehouseBubbleDetector.Candidate> findChest() {
            return observeBubble(StorehouseBubbleDetector.Kind.CHEST, StorehouseBubbleDetector.SEARCH_AREA);
        }

        @Override
        public StorehouseVisitFlow.Observation<StorehouseBubbleDetector.Candidate> findStamina() {
            return observeBubble(StorehouseBubbleDetector.Kind.STAMINA, StorehouseBubbleDetector.SEARCH_AREA);
        }

        @Override
        public StorehouseVisitFlow.CollectionState collectChest(StorehouseBubbleDetector.Candidate candidate) {
            logInfo("Chest found. Claiming reward.");
            tapInside(candidate.center(), candidate.center());
            sleepTask(500);
            tapInside(CHEST_REWARD_OVERLAY_CLOSE_AREA);
            sleepTask(CLAIM_CLOSE_SETTLE_MILLIS);
            return confirmBubbleDisappeared(candidate, StorehouseBubbleDetector.Kind.CHEST);
        }

        @Override
        public StorehouseVisitFlow.CollectionState collectStamina(
                StorehouseBubbleDetector.Candidate candidate) {
            logInfo("Stamina bubble found. Opening its claim dialog.");
            tapInside(candidate.center(), candidate.center());
            ImageSearchResultData claimButton = awaitStaminaClaimButton();
            if (claimButton == null) {
                captureInterfaceFailure("stamina-claim-button-missing");
                return StorehouseVisitFlow.CollectionState.UNCONFIRMED;
            }

            pendingAgnesStamina = readAgnesBonus();
            logDebug("Agnes stamina OCR result: "
                    + (pendingAgnesStamina == null ? "unreadable" : pendingAgnesStamina));
            logInfo("Stamina Claim button confirmed. Claiming the reward.");
            tapInside(claimButton);
            sleepTask(450);
            if (isStaminaCapacityBlocked()) {
                logInfo("Storehouse Claim responded with a full Chief Stamina capacity notice;"
                        + " reward was not collected.");
                pendingAgnesStamina = null;
                if (!closeStaminaRewardDialog()) {
                    captureInterfaceFailure("stamina-full-dialog-close-unverified");
                    return StorehouseVisitFlow.CollectionState.UNKNOWN;
                }
                return StorehouseVisitFlow.CollectionState.BLOCKED_CAPACITY;
            }
            // Reuse the existing verified 2026 close control even when the
            // max-capacity toast was absent or disappeared too quickly to scan.
            // An unaccepted Claim is not a collected stamina reward.
            RawImageData postClaim = emuManager.captureScreen(EMULATOR_NUMBER);
            StorehouseStaminaClaimRecovery.Outcome rejection =
                    StorehouseStaminaClaimRecovery.dismissUnacceptedClaim(
                            new StorehouseStaminaClaimRecovery.Actions() {
                                private boolean closeVerified;

                                @Override
                                public boolean rewardTitleVisible() {
                                    return matchesPostClaim(postClaim,
                                            TemplatesEnum.STOREHOUSE_STAMINA_REWARD_TITLE,
                                            STAMINA_REWARD_TITLE_AREA, REWARD_TITLE_THRESHOLD);
                                }

                                @Override
                                public boolean claimButtonVisible() {
                                    return matchesPostClaim(postClaim,
                                            TemplatesEnum.STOREHOUSE_STAMINA_CLAIM_TEXT,
                                            STAMINA_CLAIM_BUTTON_AREA, CLAIM_BUTTON_THRESHOLD);
                                }

                                @Override
                                public void dismiss() {
                                    logWarning("Storehouse stamina Claim was not accepted; "
                                            + "closing the identified reward overlay without crediting stamina.");
                                    captureInterfaceFailure("stamina-claim-not-accepted");
                                    closeVerified = closeStaminaRewardDialog();
                                }

                                @Override
                                public boolean cityVisible() {
                                    // Dialog disappearance is the actual postcondition.
                                    // The city HUD anchor may be hidden or delayed.
                                    return closeVerified;
                                }
                            });
            if (rejection != StorehouseStaminaClaimRecovery.Outcome.NOT_PRESENT) {
                if (rejection == StorehouseStaminaClaimRecovery.Outcome.CLOSE_UNCONFIRMED) {
                    captureInterfaceFailure("stamina-claim-dialog-close-unconfirmed");
                }
                pendingAgnesStamina = null;
                // When Claim remains visible, the game refused the action.
                // A full stamina bar can cause this without a recognizable
                // max-capacity toast. Never count or immediately reattempt it.
                return rejection == StorehouseStaminaClaimRecovery.Outcome.DISMISSED
                        ? StorehouseVisitFlow.CollectionState.DEFERRED_UNACCEPTED
                        : StorehouseVisitFlow.CollectionState.UNKNOWN;
            }
            StorehouseVisitFlow.CollectionState confirmation = confirmBubbleDisappeared(
                    candidate, StorehouseBubbleDetector.Kind.STAMINA);
            if (confirmation == StorehouseVisitFlow.CollectionState.CONFIRMED) {
                StaminaService.getServices().addExternalStamina(profile.getId(), BASE_STOREHOUSE_STAMINA);
                if (pendingAgnesStamina != null && pendingAgnesStamina > 0) {
                    StaminaService.getServices().addExternalStamina(profile.getId(), pendingAgnesStamina);
                    logInfo(String.format("Confirmed stamina collection: %d base + %d Agnes bonus.",
                            BASE_STOREHOUSE_STAMINA, pendingAgnesStamina));
                } else {
                    logInfo("Confirmed stamina collection: " + BASE_STOREHOUSE_STAMINA + " base stamina.");
                }
            }
            pendingAgnesStamina = null;
            return confirmation;
        }

        @Override
        public StorehouseVisitFlow.CooldownRead readCooldown() {
            return StorehouseChestRoutine.this.readCooldown();
        }

        @Override
        public void onPhase(StorehouseVisitFlow.ActivityPhase phase) {
            setActivityPhase(phase);
            if (phase == StorehouseVisitFlow.ActivityPhase.READING_COOLDOWN) {
                setVisitState(StorehouseVisitFlow.VisitState.WAITING_COOLDOWN,
                        "Both reward types were absent on reliable city scans");
            }
        }
    }

    private StorehouseVisitFlow.CollectionState confirmBubbleDisappeared(
            StorehouseBubbleDetector.Candidate clicked, StorehouseBubbleDetector.Kind kind) {
        AreaData checkArea = expandedArea(clicked.bounds(), BUBBLE_CONFIRMATION_MARGIN);
        StorehouseVisitFlow.Observation<StorehouseBubbleDetector.Candidate> observation = observeBubble(kind, checkArea);
        if (observation.state() == StorehouseVisitFlow.ObservationState.ABSENT) {
            logInfo("Confirmed " + kind.name().toLowerCase(Locale.ROOT)
                    + " bubble disappeared after collection.");
            return StorehouseVisitFlow.CollectionState.CONFIRMED;
        }
        captureInterfaceFailure(kind.name().toLowerCase(Locale.ROOT) + "-collection-unconfirmed");
        return observation.state() == StorehouseVisitFlow.ObservationState.UNKNOWN
                ? StorehouseVisitFlow.CollectionState.UNKNOWN
                : StorehouseVisitFlow.CollectionState.UNCONFIRMED;
    }

    private StorehouseVisitFlow.Observation<StorehouseBubbleDetector.Candidate> observeBubble(
            StorehouseBubbleDetector.Kind kind, AreaData area) {
        boolean captureFailed = false;
        boolean cityConfirmed = false;
        // One screenshot per attempt for both the City anchor and bubble.
        // Previously the city anchor was searched with a separate ADB round
        // before another six screenshots, including on empty Storehouses.
        for (int attempt = 1; attempt <= BUBBLE_SEARCH_ATTEMPTS; attempt++) {
            try {
                RawImageData capture = emuManager.captureScreen(EMULATOR_NUMBER);
                ImageSearchResultData city = emuManager.locatePattern(EMULATOR_NUMBER,
                        capture, TemplatesEnum.SERVE_EMU_CITY_ANCHOR,
                        new PointData(590, 1145), new PointData(715, 1275), 90);
                if (city == null || !city.isFound()) {
                    logWarning("Storehouse bubble scan is not on verified City HUD");
                    return StorehouseVisitFlow.Observation.unknown();
                }
                cityConfirmed = true;
                BufferedImage frame = ImageConverter.toBufferedImage(capture);
                List<StorehouseBubbleDetector.Candidate> candidates =
                        StorehouseBubbleDetector.locate(frame, area);
                for (StorehouseBubbleDetector.Candidate candidate : candidates) {
                    if (candidate.kind() == kind) {
                        logDebug("Storehouse " + kind.name().toLowerCase(Locale.ROOT) + " bubble found");
                        return StorehouseVisitFlow.Observation.found(candidate);
                    }
                }
            } catch (RuntimeException failure) {
                TaskControlSignals.rethrowControlSignal(failure);
                captureFailed = true;
                logWarning("Storehouse bubble scan failed: " + failure.getClass().getSimpleName());
            }
            if (attempt < BUBBLE_SEARCH_ATTEMPTS) sleepTask(BUBBLE_SEARCH_DELAY_MILLIS);
        }
        if (captureFailed || !cityConfirmed) {
            captureInterfaceFailure(kind.name().toLowerCase(Locale.ROOT) + "-scan-unknown");
            return StorehouseVisitFlow.Observation.unknown();
        }
        logDebug("Storehouse " + kind.name().toLowerCase(Locale.ROOT)
                + " bubble absent on verified City HUD");
        return StorehouseVisitFlow.Observation.absent();
    }

    private boolean isStaminaCapacityBlocked() {
        for (int attempt = 0; attempt < 2; attempt++) {
            RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
            ImageSearchResultData notice = emuManager.locatePattern(EMULATOR_NUMBER, frame,
                    TemplatesEnum.STOREHOUSE_STAMINA_CAPACITY_MAXED,
                    STAMINA_CAPACITY_NOTICE_AREA.topLeft(),
                    STAMINA_CAPACITY_NOTICE_AREA.bottomRight(), STAMINA_CAPACITY_NOTICE_THRESHOLD);
            if (notice != null && notice.isFound()) return true;
            // If the dialog already disappeared, do not spend a second capture
            // seeking a toast: the normal post-claim check will verify it.
            ImageSearchResultData title = emuManager.locatePattern(EMULATOR_NUMBER, frame,
                    TemplatesEnum.STOREHOUSE_STAMINA_REWARD_TITLE,
                    STAMINA_REWARD_TITLE_AREA.topLeft(),
                    STAMINA_REWARD_TITLE_AREA.bottomRight(), REWARD_TITLE_THRESHOLD);
            if (title == null || !title.isFound()) return false;
            if (attempt == 0) sleepTask(250);
        }
        return false;
    }

    private boolean matchesPostClaim(
            RawImageData frame, TemplatesEnum template, AreaData area, int threshold) {
        ImageSearchResultData result = locateStaminaDialogTemplate(frame, template, area, threshold);
        return result != null && result.isFound();
    }

    private boolean closeStaminaRewardDialog() {
        RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
        ImageSearchResultData close = emuManager.locatePattern(EMULATOR_NUMBER, frame,
                TemplatesEnum.STOREHOUSE_STAMINA_REWARD_CLOSE,
                STAMINA_DIALOG_CLOSE_AREA.topLeft(),
                STAMINA_DIALOG_CLOSE_AREA.bottomRight(), STAMINA_DIALOG_CLOSE_THRESHOLD);
        if (close == null || !close.isFound()) {
            logWarning("Stamina reward dialog close icon was not verified.");
            return false;
        }
        // The detected X has transparent corners; tap its narrow center,
        // not a randomized point anywhere in its full template rectangle.
        tapNear(close.getPoint(), 3);
        sleepTask(CLAIM_CLOSE_SETTLE_MILLIS);
        for (int attempt = 0; attempt < 2; attempt++) {
            RawImageData after = emuManager.captureScreen(EMULATOR_NUMBER);
            ImageSearchResultData title = emuManager.locatePattern(EMULATOR_NUMBER, after,
                    TemplatesEnum.STOREHOUSE_STAMINA_REWARD_TITLE,
                    STAMINA_REWARD_TITLE_AREA.topLeft(),
                    STAMINA_REWARD_TITLE_AREA.bottomRight(), REWARD_TITLE_THRESHOLD);
            if (title != null && title.isFound()) {
                logWarning("Stamina reward dialog remained open after its verified close control.");
                return false;
            }
            if (attempt == 0) sleepTask(150);
        }
        logInfo("Storehouse reward dialog closed and title disappearance verified.");
        return true;
    }

    private ImageSearchResultData awaitStaminaClaimButton() {
        StorehouseStaminaClaimFlow.Result result = StorehouseStaminaClaimFlow.awaitClaimButton(
                new StorehouseStaminaClaimFlow.Actions() {
                    private RawImageData pollFrame;

                    @Override
                    public void beginObservation() {
                        pollFrame = emuManager.captureScreen(EMULATOR_NUMBER);
                    }

                    @Override
                    public boolean isRewardDialogVisible() {
                        ImageSearchResultData title = locateStaminaDialogTemplate(pollFrame,
                                TemplatesEnum.STOREHOUSE_STAMINA_REWARD_TITLE,
                                STAMINA_REWARD_TITLE_AREA,
                                REWARD_TITLE_THRESHOLD);
                        return title != null && title.isFound();
                    }

                    @Override
                    public ImageSearchResultData findClaimButton() {
                        return locateStaminaDialogTemplate(pollFrame,
                                TemplatesEnum.STOREHOUSE_STAMINA_CLAIM_TEXT,
                                STAMINA_CLAIM_BUTTON_AREA,
                                CLAIM_BUTTON_THRESHOLD);
                    }

                    @Override
                    public boolean isTooltipVisible() {
                        return isStaminaTooltipVisible(pollFrame);
                    }

                    @Override
                    public boolean isBackAtCity() {
                        ImageSearchResultData city = locateStaminaDialogTemplate(pollFrame,
                                TemplatesEnum.GAME_HOME_FURNACE,
                                AreaData.of(580, 1120, 720, 1280), 90);
                        return city != null && city.isFound();
                    }

                    @Override
                    public void pressBack() {
                        logInfo("Stamina tooltip confirmed after opening the Storehouse reward; dismissing it once with Back.");
                        pollFrame = null;
                        StorehouseChestRoutine.this.pressBack();
                    }

                    @Override
                    public void waitAfterBack() {
                        sleepTask(CLAIM_CLOSE_SETTLE_MILLIS);
                    }

                    @Override
                    public void waitForNextPoll() {
                        pollFrame = null;
                        sleepTask(CLAIM_BUTTON_POLL_INTERVAL_MILLIS);
                    }
                }, Duration.ofMillis(CLAIM_BUTTON_TIMEOUT_MILLIS), System::nanoTime);
        if (result.claimButton() != null) {
            return result.claimButton();
        }
        logWarning("Storehouse stamina reward was not verified with a Claim button"
                + (result.rewardDialogSeen() ? "; reward dialog was visible." : "; reward dialog title was not visible."));
        return null;
    }

    private ImageSearchResultData locateStaminaDialogTemplate(
            RawImageData frame, TemplatesEnum template, AreaData area, int threshold) {
        // All checks in one poll must use the exact same captured game state.
        // Independently capturing for title, button, tooltip and city consumed
        // the claim timeout on slow remote ADB before a valid Claim was acted on.
        return emuManager.locatePattern(EMULATOR_NUMBER, frame, template,
                area.topLeft(), area.bottomRight(), threshold);
    }

    private boolean isStaminaTooltipVisible(RawImageData frame) {
        ImageSearchResultData tooltipTitle = locateStaminaDialogTemplate(frame,
                TemplatesEnum.STOREHOUSE_STAMINA_TOOLTIP_TITLE,
                STAMINA_TOOLTIP_TITLE_AREA,
                TOOLTIP_TITLE_THRESHOLD);
        return tooltipTitle != null && tooltipTitle.isFound();
    }

    private Integer readAgnesBonus() {
        return integerHelper.attemptRecognition(
                STAMINA_AMOUNT_TOP_LEFT,
                STAMINA_AMOUNT_BOTTOM_RIGHT,
                TIMER_OCR_MAX_ATTEMPTS,
                200L,
                STAMINA_OCR_SETTINGS,
                text -> RegexNumberParser.conformsTo(text, Pattern.compile(".*?(\\d+).*")),
                text -> RegexNumberParser.extractByPattern(text, Pattern.compile(".*?(\\d+).*")));
    }

    private StorehouseVisitFlow.CooldownRead readCooldown() {
        logInfo("Neither Storehouse reward bubble is present; reading the cooldown.");
        String timer = readBuildingCountdown(buildingCountdownSettings());
        if (timer == null) {
            timer = readBuildingCountdown(buildingCountdownWhiteSettings());
        }
        if (timer == null) {
            logWarning("Storehouse cooldown OCR was unreadable; using the one-hour fallback.");
            return StorehouseVisitFlow.CooldownRead.unreadable("OCR returned no accepted countdown");
        }

        logInfo("Storehouse cooldown OCR read: '" + timer + "'.");
        try {
            Duration cooldown = GameTimeUtils.parseDuration(timer);
            if (cooldown.isZero() || cooldown.isNegative()) {
                return StorehouseVisitFlow.CooldownRead.invalid("Countdown was not positive: " + timer);
            }
            return StorehouseVisitFlow.CooldownRead.valid(cooldown);
        } catch (RuntimeException invalid) {
            return StorehouseVisitFlow.CooldownRead.invalid("Countdown could not be parsed: " + timer);
        }
    }

    private String readBuildingCountdown(OcrSettingsData settings) {
        return textHelper.attemptRecognition(
                FALLBACK_TIMER_TOP_LEFT,
                FALLBACK_TIMER_BOTTOM_RIGHT,
                TIMER_OCR_MAX_ATTEMPTS,
                200L,
                settings,
                GameTimeUtils::isAcceptedFormat,
                text -> text);
    }

    private void captureInterfaceFailure(String situation) {
        String snapshot = TaskDiagnosticSnapshots.capture(
                emuManager, EMULATOR_NUMBER, "storehousechest", situation);
        logWarning("Storehouse interface outcome uncertain; diagnostic snapshot: " + snapshot);
    }

    private void setVisitState(StorehouseVisitFlow.VisitState next, String reason) {
        if (visitState != next) {
            logInfo("Storehouse visit state: " + visitState + " -> " + next + " (" + reason + ")");
        } else {
            logInfo("Storehouse visit state remains " + next + " (" + reason + ")");
        }
        visitState = next;
    }

    private void setActivityPhase(StorehouseVisitFlow.ActivityPhase next) {
        if (activityPhase != next) {
            logInfo("Storehouse activity phase: " + activityPhase + " -> " + next);
            activityPhase = next;
        }
    }

    private AreaData expandedArea(AreaData area, int margin) {
        RawImageData raw = emuManager.captureScreen(EMULATOR_NUMBER);
        BufferedImage frame = ImageConverter.toBufferedImage(raw);
        int left = Math.max(0, area.topLeft().getX() - margin);
        int top = Math.max(0, area.topLeft().getY() - margin);
        int right = Math.min(frame.getWidth() - 1, area.bottomRight().getX() + margin);
        int bottom = Math.min(frame.getHeight() - 1, area.bottomRight().getY() + margin);
        return new AreaData(new PointData(left, top), new PointData(right, bottom));
    }

    static OcrSettingsData buildingCountdownSettings() {
        return buildingCountdownSettings(BUILDING_TIMER_GREEN);
    }

    static OcrSettingsData buildingCountdownWhiteSettings() {
        return buildingCountdownSettings(Color.WHITE);
    }

    private static OcrSettingsData buildingCountdownSettings(Color textColor) {
        return OcrSettingsData.assembler()
                .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
                .stripBackground(true)
                .setTextColor(textColor)
                .charWhitelist(BUILDING_COUNTDOWN_WHITELIST)
                .build();
    }

    @Override
    protected LaunchPoint getRequiredStartLocation() {
        return LaunchPoint.HOME;
    }

    @Override
    public boolean provideDailyMissionProgress() {
        return confirmedDailyMissionProgress;
    }

    static boolean hasConfirmedProgress(int confirmedChestCollections, int confirmedStaminaCollections) {
        return confirmedChestCollections > 0 || confirmedStaminaCollections > 0;
    }

    static boolean shouldPropagateUnknownOutcome(
            StorehouseVisitFlow.VisitState state, boolean confirmedProgress) {
        return state == StorehouseVisitFlow.VisitState.RETRY_ON_ERROR && !confirmedProgress;
    }
}
