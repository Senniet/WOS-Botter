package dev.frostguard.tasks.pets;

import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import dev.frostguard.engine.nav.SidebarDestination;
import dev.frostguard.vision.convert.GameTimeUtils;
import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import java.util.Objects;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

/**
 * Task that manages Pet Adventure chests.
 * 
 * <p>
 * <b>Execution Flow:</b>
 * <ol>
 * <li>Navigate to Pet Adventures (Pets â†’ Beast Cage â†’ Adventure Map)</li>
 * <li>Claim all completed adventure chests (up to 3 visible)</li>
 * <li>Start new adventures with available chests</li>
 * <li>Repeat until no more chests or attempts exhausted</li>
 * </ol>
 * 
 * <p>
 * <b>Game Mechanics:</b>
 * <ul>
 * <li>Maximum 3 chests visible on screen at once</li>
 * <li>Each chest adventure costs 10 stamina</li>
 * <li>4 daily attempts available (resets at game reset)</li>
 * <li>Chests respawn after claiming rewards</li>
 * <li>Completed chests can be shared with alliance</li>
 * </ul>
 * 
 * <p>
 * <b>Scheduling Strategy:</b>
 * <ul>
 * <li>After starting chests (attempts remaining): 2 hours (for completion)</li>
 * <li>After exhausting attempts: Next game reset</li>
 * <li>Navigation failure: 15 minutes retry</li>
 * </ul>
 * 
 * @author WoS Bot
 */
public class PetAdventureChestRoutine extends DelayedTask {

	// ========================================================================
	// GAME MECHANICS CONSTANTS
	// ========================================================================

	private static final int STAMINA_PER_CHEST = 10;
	private static final int MIN_STAMINA_REQUIRED = 10;
	private static final int TARGET_STAMINA_FOR_REFRESH = 40;
	private static final int MAX_CHEST_START_ITERATIONS = 12;

	private boolean confirmedDailyMissionProgress;

	private static final SearchConfig CHEST_CANDIDATES = SearchConfig.builder()
			.withMaxAttempts(3)
			.withThreshold(90)
			.withDelay(200L)
			.withMaxResults(3)
			.build();

	private static final SearchConfig ADVENTURE_TIMERS = SearchConfig.builder()
			.withMaxAttempts(1)
			.withThreshold((int) PetAdventureDecisions.TIMER_THRESHOLD)
			.withMaxResults(5)
			.build();

	// ========================================================================
	// NAVIGATION CONSTANTS
	// ========================================================================

	/**
	 * Area to tap for skipping reward popups after claiming chests.
	 * Taps on empty screen space to dismiss overlays.
	 */
	private static final PointData CHEST_CLAIM_POINT = new PointData(370, 800);

	// ========================================================================
	// RETRY CONSTANTS
	// ========================================================================

	private static final int NAVIGATION_RETRY_MINUTES = 15;
	private static final int CHEST_COMPLETION_HOURS = 2;

	// ========================================================================
	// CHEST PRIORITY ORDER
	// ========================================================================

	/**
	 * Chest priority order: Red (highest quality) â†’ Purple â†’ Blue (lowest).
	 */
	private static final List<TemplatesEnum> CHEST_PRIORITY = List.of(
			TemplatesEnum.PETS_CHEST_RED,
			TemplatesEnum.PETS_CHEST_PURPLE,
			TemplatesEnum.PETS_CHEST_BLUE);

	// ========================================================================
	// CONSTRUCTOR
	// ========================================================================

	/**
	 * Constructs a new PetAdventureChestRoutine.
	 * 
	 * @param profile The profile this task will execute for
	 * @param tpTask  The task type enum
	 */
	public PetAdventureChestRoutine(AccountDescriptor profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

	// ========================================================================
	// TASK CONFIGURATION
	// ========================================================================

    // Pet Adventure sidebar opens only from a verified City/World root.
    // ANY can leave us on an unknown modal and SidebarNavigator refuses to tap.
    @Override
    protected LaunchPoint getRequiredStartLocation() {
        return LaunchPoint.HOME;
    }

	@Override
	protected boolean consumesStamina() {
		return true;
	}

	@Override
	public boolean provideDailyMissionProgress() {
		return confirmedDailyMissionProgress;
	}

	// ========================================================================
	// MAIN EXECUTION
	// ========================================================================

	/**
	 * Executes the pet adventure chest management process.
	 * 
	 * <p>
	 * <b>Process:</b>
	 * <ol>
	 * <li>Validate stamina availability</li>
	 * <li>Navigate to Pet Adventures</li>
	 * <li>Claim completed chests</li>
	 * <li>Start new adventures until attempts exhausted or no chests found</li>
	 * </ol>
	 */
	@Override
	protected void execute() {

		confirmedDailyMissionProgress = false;

		// Collecting finished adventures is free. Never skip available
        // claims just because stamina is too low to START a new adventure.
        if (!navigateToPetAdventures()) {
            markWorkIncomplete("Pet Adventure sidebar navigation failed");
            rescheduleForNavigationRetry();
            return;
        }
        try {
        if (!claimCompletedChests()) {
            markWorkIncomplete("Pet Adventure claim action not confirmed; no new chest taps");
            return;
        }
        if (observeOnlyVisit()) {
            // Recover an unverified Start without ever tapping the original
            // pin again: the original location needs a confirmed running timer
            // on TWO separate map frames. After recovery, continue scanning
            // other idle red/purple/blue pins; don't defer all of them 2h.
            PointData pending = pendingStartLocation();
            if (pending != null && startedChestTimerConfirmed(pending)) {
                logInfo("Previously uncertain Pet Adventure Start verified by two-frame "
                        + "timer at " + pending + ". Clearing observe-only and "
                        + "continuing with OTHER available chests.");
                confirmedDailyMissionProgress = true;
                clearPendingStart();
            } else {
                Instant since = pendingStartInstant();
                if (pending == null) {
                    if (!verifiedMapTwice()) {
                        scheduleUnknownChestRetry("orphaned observer state; map not confirmed",
                                "orphaned");
                        return;
                    }
                    logWarning("Pet Adventure orphaned observer flag cleared on confirmed map");
                    clearPendingStart();
                } else if (PetAdventureDecisions.pendingHasExpired(since, Instant.now())
                        && verifiedMapTwice()) {
                    logInfo("Pet Adventure unconfirmed Start expired after "
                            + PetAdventureDecisions.PENDING_MAX_AGE.toMinutes()
                            + " minutes without a running timer. Rescanning for idle chests.");
                    clearPendingStart();
                } else {
                    if (since == null && pending != null) {
                        // Migrate legacy x,y-only pending records exactly
                        // once; do not refresh their age on each revisit.
                        writeProfileSetting(ConfigurationKeyEnum.PET_ADVENTURE_PENDING_START_STRING,
                                encodeTimedPendingStart(pending, Instant.now()));
                        logInfo("Pet Adventure migrated legacy pending position");
                    }
                    LocalDateTime next = unverifiedStartRecheck(LocalDateTime.now());
                    markWorkIncomplete("Pet Adventure earlier Start still unverified; "
                            + "timed observe-only safeguard remains active");
                    logWarning("Pet Adventure pending Start not proven at "
                            + pending + "; no duplicate Start. Next observation "
                            + next.format(DATETIME_FORMATTER));
                    reschedule(next);
                    return;
                }
            }
        }
        // Only new adventures consume stamina; check after free claims and
        // the observe-only guard, avoiding a redundant initial check.
        if (!staminaHelper.checkStaminaOrReschedule(
                MIN_STAMINA_REQUIRED, TARGET_STAMINA_FOR_REFRESH, this)) {
            markWorkIncomplete("Pet Adventure has insufficient stamina for new chest starts");
            return;
        }
		startAvailableChests();
        } finally {
            // Never leave the Pet Adventure map over the next unrelated task.
            // A true map title on two fresh frames is required to authorize
            // exactly one in-game Back; no generic screen dismissal.
            returnFromVerifiedPetAdventureMap();
        }
	}

    private void returnFromVerifiedPetAdventureMap() {
        try {
            if (!verifiedMapTwice()) {
                var panel = PetAdventureSelectPetFrame.closeIfVisible(
                        () -> {checkPreemption(); return emuManager.captureScreen(EMULATOR_NUMBER);},
                        target -> {checkPreemption(); tapNear(target);},
                        () -> sleepTask(600));
                if (panel == PetAdventureSelectPetFrame.CloseResult.CLOSED_TO_MAP) {
                    logInfo("Select Pet closed via twice-verified title and X; "
                            + "Treasure Spot also closed if present; adventure map restored");
                } else {
                    markWorkIncomplete("Pet Adventure exit: Select Pet/map not positively closed");
                    scheduleUnknownChestRetry("unable to verify Select Pet exit / map", "select-pet-exit");
                    return;
                }
            }
            if (!verifiedMapTwice()) {
                markWorkIncomplete("Pet Adventure map not stable after closing Select Pet");
                scheduleUnknownChestRetry("map not stable after Select Pet exit", "map-exit");
                return;
            }
            pressBack();
            sleepTask(450);
            if (PetAdventureMapFrame.isMap(emuManager.captureScreen(EMULATOR_NUMBER))) {
                markWorkIncomplete("Pet Adventure map remained open after verified Back");
                scheduleUnknownChestRetry("map remained open after Back", "map-still-open");
            } else {
                logInfo("Pet Adventure map closed via verified Back; next task can start at Home/World");
            }
        } catch (dev.frostguard.engine.error.TaskPreemptedException ex) {
            throw ex;
        } catch (dev.frostguard.engine.error.StopExecutionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            logWarning("Pet Adventure exit validation unavailable: " + ex.getMessage());
        }
    }

	// ========================================================================
	// NAVIGATION METHODS
	// ========================================================================

	/**
	 * Navigates from current screen to the Pet Adventures map.
	 * 
	 * <p>
	 * <b>Steps:</b>
	 * <ol>
	 * <li>Open the Daily sidebar</li>
	 * <li>Locate the Pet Adventure row</li>
	 * <li>Tap its detected Go control</li>
	 * </ol>
	 * 
	 * @return true if navigation succeeded, false if any step failed
	 */
	private boolean navigateToPetAdventures() {
		logDebug("Opening Pet Adventure through the Daily sidebar");
		return navigationHelper.navigateToSidebarDestination(SidebarDestination.PET_ADVENTURE);
	}

	// ========================================================================
	// CLAIMING METHODS
	// ========================================================================

	/**
	 * Claims all completed adventure chests visible on the screen.
	 * 
	 * <p>
	 * Searches for up to 3 completed chests (maximum visible at once)
	 * and claims each one. After claiming, shares the chest with alliance
	 * if the share button is available.
	 * 
	 * <p>
	 * Performs 2 search attempts to ensure all completed chests are claimed,
	 * as new chests may become visible after claiming previous ones.
	 * 
	 * <p>
	 * <b>Process per chest:</b>
	 * <ol>
	 * <li>Tap completed chest</li>
	 * <li>Skip reward popups by tapping empty screen area</li>
	 * <li>Share with alliance if button available</li>
	 * <li>Return to adventure map</li>
	 * </ol>
	 */
    private boolean claimCompletedChests() {
        // The 2026 map repositions treasure spots after a claim. Never
        // iterate over a stale list of chest coordinates.
        for (int i = 0; i < 6; i++) {
            List<ImageSearchResultData> completed = templateSearchHelper.locateAllPatterns(
                    TemplatesEnum.PETS_CHEST_COMPLETED,
                    SearchConfigConstants.MULTIPLE_RESULTS);
            if (completed == null || completed.isEmpty()) return true;
            ImageSearchResultData next = completed.stream()
                    .filter(ImageSearchResultData::isFound).findFirst().orElse(null);
            if (next == null) return true;
            if (!claimSingleChest(next)) {
                markWorkIncomplete("Pet Adventure: completed chest did not disappear after claim");
                return false;
            }
        }
        logInfo("Pet Adventure claim iteration capped; recheck remaining rewards on next visit");
        return true;
    }

    static boolean sameChestLocation(PointData a, PointData b) {
        return Math.abs(a.getX() - b.getX()) <= 45
                && Math.abs(a.getY() - b.getY()) <= 45;
    }

    private boolean claimSingleChest(ImageSearchResultData chest) {
        tapInside(chest.getPoint(), chest.getPoint());
        sleepTask(550);
        // Live ADB: tapping a completed chest opens Treasure Spot; tapping
        // the chest opens Rewards; dismissing Rewards leaves Treasure Spot.
        tapNear(CHEST_CLAIM_POINT);
        sleepTask(1200);
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            tapNear(new PointData(350, 1175)); // Dismiss Rewards overlay
            sleepTask(450);
            tapNear(new PointData(643, 241)); // Close Treasure Spot dialog
            sleepTask(450);
        } else {
            pressBack();
            sleepTask(500);
            pressBack();
            sleepTask(500);
        }
        List<ImageSearchResultData> remaining = templateSearchHelper.locateAllPatterns(
                TemplatesEnum.PETS_CHEST_COMPLETED,
                SearchConfigConstants.MULTIPLE_RESULTS);
        if (remaining == null) return false;
        return remaining.stream().filter(ImageSearchResultData::isFound)
                .noneMatch(r -> sameChestLocation(chest.getPoint(), r.getPoint()));
    }

	// ========================================================================
	// CHEST STARTING METHODS
	// ========================================================================

	/**
	 * Starts new adventure chests until no more are available or attempts
	 * exhausted.
	 * 
	 * <p>
	 * <b>Strategy:</b>
	 * <ul>
	 * <li>Searches for chests in priority order (Red â†’ Purple â†’ Blue)</li>
	 * <li>Starts each found chest immediately</li>
	 * <li>Repeats search after each start (UI updates with new chests)</li>
	 * <li>Stops when no chests found or attempts exhausted</li>
	 * <li>Has safety limit to prevent infinite loops</li>
	 * </ul>
	 * 
	 * <p>
	 * <b>Exit conditions:</b>
	 * <ol>
	 * <li>No more chests found on screen</li>
	 * <li>Daily attempts exhausted (detected by template)</li>
	 * <li>Safety iteration limit reached</li>
	 * </ol>
	 */
	private void startAvailableChests() {
        if (PetAdventureAttemptsGate.blockedUntilReset(
                profile.getConfig(ConfigurationKeyEnum.PET_ADVENTURE_ATTEMPTS_EXHAUSTED_UNTIL_STRING,
                        String.class), LocalDateTime.now())) {
            logInfo("Pet Adventure attempts already verified exhausted. "
                    + "Skipping new starts; completed chest claims were still checked");
            rescheduleToGameReset();
            return;
        }
		logDebug("Starting available adventure chests");

		int iterationCount = 0;
		boolean foundAnyChest;

		do {
			foundAnyChest = false;
			iterationCount++;

			// Safety check to prevent infinite loops
			if (iterationCount > MAX_CHEST_START_ITERATIONS) {
				scheduleUnknownChestRetry("chest scan reached its iteration limit", "iteration-limit");
				return;
			}

			// Try each chest type in priority order
			for (TemplatesEnum chestTemplate : CHEST_PRIORITY) {
				ChestStartResult result = attemptToStartChest(chestTemplate);

				if (countsAsProgress(result)) {
					confirmedDailyMissionProgress = true;
					foundAnyChest = true;
					break;
				} else if (result == ChestStartResult.NO_ATTEMPTS) {
					return; // Task already rescheduled
				} else if (result == ChestStartResult.UNKNOWN) {
					return;
				}
			}

		} while (foundAnyChest);

		// No more chests found - reschedule for later
		logInfo("No more available chests found. Rescheduling for chest completion check");
		rescheduleForChestCompletion();
	}

	/**
	 * Attempts to start a single chest adventure.
	 * 
	 * <p>
	 * <b>Process:</b>
	 * <ol>
	 * <li>Search for chest of specified type (with retries)</li>
	 * <li>Tap chest to open detail screen</li>
	 * <li>Tap Select button</li>
	 * <li>Check for Start button vs No Attempts message</li>
	 * <li>Skip pins whose countdown pill is already running</li>
	 * <li>If Start remains visible, retry shortly without recording a start</li>
	 * <li>If Start disappears and In Adventure is present, count the start and keep scanning</li>
	 * <li>If Start disappears without In Adventure, re-read on the next visit and do not tap Start again</li>
	 * <li>If No Attempts: reschedule and exit task</li>
	 * </ol>
	 * 
	 * @param chestTemplate The chest template to search for
	 * @return STARTED, NOT_FOUND, NO_ATTEMPTS, or UNKNOWN
	 */
	private ChestStartResult attemptToStartChest(TemplatesEnum chestTemplate) {
		logDebug("Searching for " + chestTemplate);

		List<ImageSearchResultData> chests = templateSearchHelper.locateAllPatterns(
				chestTemplate,
				CHEST_CANDIDATES);
		List<ImageSearchResultData> timers = templateSearchHelper.locateAllPatterns(
				TemplatesEnum.PETS_CHEST_ADVENTURE_TIMER,
				ADVENTURE_TIMERS);

		if (chests == null || chests.isEmpty()) {
			return ChestStartResult.NOT_FOUND;
		}

		for (ImageSearchResultData chestResult : chests) {
			if (!chestResult.isFound()) {
				continue;
			}
			if (PetAdventureDecisions.occupiedByTimer(chestResult.getPoint(), timers)) {
				logInfo("Skipping " + chestTemplate + " already in adventure at "
						+ chestResult.getPoint());
				continue;
			}
			return startIdleChest(chestTemplate, chestResult);
		}

		return ChestStartResult.NOT_FOUND;
	}

	private ChestStartResult startIdleChest(TemplatesEnum chestTemplate, ImageSearchResultData chestResult) {
		logInfo("Found " + chestTemplate + ". Attempting to start adventure");

		tapInside(chestResult.getPoint(), chestResult.getPoint());
		sleepTask(500);

		ImageSearchResultData selectButton = templateSearchHelper.locatePattern(
				TemplatesEnum.PETS_CHEST_SELECT,
				SearchConfigConstants.DEFAULT_SINGLE);

        if (!selectButton.isFound()) {
            markWorkIncomplete("Pet Adventure chest Select control missing");
            scheduleUnknownChestRetry("Select control was missing for " + chestTemplate, "select-control");
			pressBack();
			return ChestStartResult.UNKNOWN;
		}

		tapInside(selectButton);
		sleepTask(500);

		ImageSearchResultData startButton = templateSearchHelper.locatePattern(
				TemplatesEnum.PETS_CHEST_START,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (startButton.isFound()) {
            tapInside(startButton);
            sleepTask(350);
            // The 20 Nov real emu proved: Start succeeded, remaining attempts
            // fell to 3 and the originally purple chest gained a timer while
            // its sprite changed to GOLD. The old code only looked for
            // In Adventure after 1 s, missed it, and deferred for 2 hours.
            StartConfirmation confirmed = confirmStartAfterTap(chestResult.getPoint());
            if (confirmed != StartConfirmation.UNKNOWN) {
                staminaHelper.subtractStamina(STAMINA_PER_CHEST, false);
                confirmedDailyMissionProgress = true;
                clearPendingStart();
                logInfo("Started " + chestTemplate + " adventure, verified " + confirmed
                        + " for chest at " + chestResult.getPoint()
                        + " (cost " + STAMINA_PER_CHEST + " stamina)");
                if (confirmed == StartConfirmation.IN_ADVENTURE_OVERLAY) {
                    pressBack(); // Close the exact confirmed occupied-chest dialog
                    sleepTask(400);
                } // TIMER_ON_MAP: already on map, Back would incorrectly leave it.
                return ChestStartResult.STARTED;
            }

            ImageSearchResultData stillStart = templateSearchHelper.locatePattern(
                    TemplatesEnum.PETS_CHEST_START,
                    SearchConfigConstants.DEFAULT_SINGLE);
            if (stillStart.isFound()) {
                scheduleUnknownChestRetry("Start button remained visible; game did not acknowledge tap",
                        "start-unconfirmed");
                markWorkIncomplete("Pet Adventure Start remained visible; no confirmed adventure");
                pressBack();
                return ChestStartResult.UNKNOWN;
            }

            rememberPendingStart(chestResult.getPoint());
            LocalDateTime next = unverifiedStartRecheck(LocalDateTime.now());
            String snapshot = TaskDiagnosticSnapshots.capture(
                    emuManager, EMULATOR_NUMBER, "petadventurechest", "start-outcome");
            markWorkIncomplete("Pet Adventure Start not confirmed; safe observer recheck scheduled");
            logWarning("Start tap was sent but neither In Adventure nor timer beneath the "
                    + "original chest was confirmed. No duplicate Start; next observation "
                    + next.format(DATETIME_FORMATTER) + "; " + snapshot + ".");
            reschedule(next);
            // May already be on map; only go Back when an actual Start control
            // proves a dialog is still present.
            return ChestStartResult.UNKNOWN;
        }

		ImageSearchResultData noAttemptsMessage = templateSearchHelper.locatePattern(
				TemplatesEnum.PETS_CHEST_ATTEMPT,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (noAttemptsMessage.isFound()) {
			logInfo("No more adventure attempts available. Rescheduling for next game reset");
			rescheduleToGameReset();
			return ChestStartResult.NO_ATTEMPTS;
		}

        markWorkIncomplete("Pet Adventure: neither Start nor No Attempts was detected");
		logWarning("Could not determine chest start status for " + chestTemplate);
		scheduleUnknownChestRetry("neither Start nor No Attempts was detected", "start-status");
		pressBack();
		return ChestStartResult.UNKNOWN;
	}

    enum StartConfirmation { IN_ADVENTURE_OVERLAY, TIMER_ON_MAP, UNKNOWN }

    private static final int START_VERIFY_FRAMES = 7;
    private static final int START_VERIFY_INTERVAL_MS = 260;

    private StartConfirmation confirmStartAfterTap(PointData originalChest) {
        for (int i = 0; i < START_VERIFY_FRAMES; i++) {
            checkPreemption();
            RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
            if (frame != null) {
                StartConfirmation candidate = inspectStartFrame(originalChest, frame);
                if (candidate != StartConfirmation.UNKNOWN) {
                    sleepTask(180);
                    StartConfirmation next = inspectStartFrame(originalChest,
                            emuManager.captureScreen(EMULATOR_NUMBER));
                    if (next == candidate) return candidate;
                }
            }
            sleepTask(START_VERIFY_INTERVAL_MS);
        }
        return StartConfirmation.UNKNOWN;
    }

    private StartConfirmation inspectStartFrame(PointData previous, RawImageData frame) {
        if (frame == null || !frame.isValid()) return StartConfirmation.UNKNOWN;
        ImageSearchResultData overlay = emuManager.locatePattern(
                EMULATOR_NUMBER, frame, TemplatesEnum.PETS_CHEST_IN_ADVENTURE, 90);
        if (PetAdventureDecisions.inAdventureOverlay(overlay)
                || emuManager.locatePattern(EMULATOR_NUMBER, frame,
                        TemplatesEnum.PETS_CHEST_IN_ADVENTURE_NOV_2026,
                        new PointData(240, 272), new PointData(480, 370), 89).isFound())
            return StartConfirmation.IN_ADVENTURE_OVERLAY;

        List<ImageSearchResultData> timers = emuManager.locateAllPatterns(
                EMULATOR_NUMBER, frame, TemplatesEnum.PETS_CHEST_ADVENTURE_TIMER,
                new PointData(0, 0), new PointData(720, 1280),
                PetAdventureDecisions.TIMER_THRESHOLD, 5);
        return PetAdventureDecisions.startedChestHasTimer(previous, timers)
                ? StartConfirmation.TIMER_ON_MAP : StartConfirmation.UNKNOWN;
    }

    private boolean startedChestTimerConfirmed(PointData previous) {
        for (int i = 0; i < 3; i++) {
            RawImageData first = emuManager.captureScreen(EMULATOR_NUMBER);
            if (inspectStartFrame(previous, first) == StartConfirmation.TIMER_ON_MAP) {
                sleepTask(190);
                if (inspectStartFrame(previous, emuManager.captureScreen(EMULATOR_NUMBER))
                        == StartConfirmation.TIMER_ON_MAP) return true;
            }
            sleepTask(200);
        }
        return false;
    }

    private boolean verifiedMapTwice() {
        if (!PetAdventureMapFrame.isMap(emuManager.captureScreen(EMULATOR_NUMBER)))
            return false;
        sleepTask(180);
        return PetAdventureMapFrame.isMap(emuManager.captureScreen(EMULATOR_NUMBER));
    }

    static String encodeTimedPendingStart(PointData location, Instant at) {
        return encodePendingStart(location) + "@" + at.getEpochSecond();
    }

    static Instant parsePendingStartInstant(String record) {
        if (record == null || !record.matches("[0-9]{1,3},[0-9]{1,4}@[0-9]{9,12}"))
            return null;
        try {
            long epoch=Long.parseLong(record.substring(record.indexOf('@')+1));
            return epoch > 1_700_000_000L ? Instant.ofEpochSecond(epoch) : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private Instant pendingStartInstant() {
        return parsePendingStartInstant(profile.getConfig(
                ConfigurationKeyEnum.PET_ADVENTURE_PENDING_START_STRING, String.class));
    }

    static String encodePendingStart(PointData location) {
        return location.getX() + "," + location.getY();
    }

    static PointData parsePendingStart(String value) {
        if (value == null || !value.matches("[0-9]{1,3},[0-9]{1,4}(@[0-9]{9,12})?")) return null;
        String[] parts = value.split("[,@]");
        int x = Integer.parseInt(parts[0]), y = Integer.parseInt(parts[1]);
        return (x >= 0 && x < 720 && y >= 0 && y < 1280)
                ? new PointData(x, y) : null;
    }

    private PointData pendingStartLocation() {
        return parsePendingStart(profile.getConfig(
                ConfigurationKeyEnum.PET_ADVENTURE_PENDING_START_STRING, String.class));
    }

    private void rememberPendingStart(PointData location) {
        writeProfileSetting(ConfigurationKeyEnum.PET_ADVENTURE_PENDING_START_STRING,
                encodeTimedPendingStart(location, Instant.now()));
        markObserveOnly();
    }

    private void clearPendingStart() {
        clearObserveOnly();
        writeProfileSetting(ConfigurationKeyEnum.PET_ADVENTURE_PENDING_START_STRING, "");
    }

	// ========================================================================
	// RESCHEDULING METHODS
	// ========================================================================

	/**
	 * Reschedules the task for chest completion check (2 hours).
	 * 
	 * <p>
	 * Used when chests have been started and we need to wait for them
	 * to complete before claiming rewards.
	 */
	private void rescheduleForChestCompletion() {
		LocalDateTime nextCheck = LocalDateTime.now().plusHours(CHEST_COMPLETION_HOURS);
		reschedule(nextCheck);
		logInfo("Rescheduled for chest completion check at " +
				nextCheck.format(TIME_FORMATTER));
	}

	/**
	 * Reschedules the task to the next game reset (00:00 UTC).
	 * 
	 * <p>
	 * Used when all daily attempts have been exhausted.
	 */
	private void rescheduleToGameReset() {
		LocalDateTime nextReset = GameTimeUtils.dailyResetTime();
        writeProfileSetting(ConfigurationKeyEnum.PET_ADVENTURE_ATTEMPTS_EXHAUSTED_UNTIL_STRING,
                nextReset.toString());
		reschedule(nextReset);
		logInfo("Rescheduled to next game reset: " +
				nextReset.format(DATETIME_FORMATTER));
	}

	/**
	 * Reschedules the task for navigation retry (15 minutes).
	 * 
	 * <p>
	 * Used when navigation to Pet Adventures fails.
	 */
	private void rescheduleForNavigationRetry() {
		LocalDateTime retryTime = LocalDateTime.now().plusMinutes(NAVIGATION_RETRY_MINUTES);
		reschedule(retryTime);
		logWarning("Navigation failed. Retrying at " + retryTime.format(TIME_FORMATTER));
	}

	static LocalDateTime unverifiedStartRecheck(LocalDateTime now) {
		return now.plusMinutes(20);
	}

	private boolean observeOnlyVisit() {
		return Boolean.TRUE.equals(profile.getConfig(
				ConfigurationKeyEnum.PET_ADVENTURE_OBSERVE_ONLY_BOOL, Boolean.class));
	}

	private void markObserveOnly() {
		writeProfileSetting(ConfigurationKeyEnum.PET_ADVENTURE_OBSERVE_ONLY_BOOL, true);
	}

	private void clearObserveOnly() {
		writeProfileSetting(ConfigurationKeyEnum.PET_ADVENTURE_OBSERVE_ONLY_BOOL, false);
	}

	private void scheduleUnknownChestRetry(String reason, String type) {
		LocalDateTime retryTime = LocalDateTime.now().plusMinutes(NAVIGATION_RETRY_MINUTES);
		String snapshot = TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER, "petadventurechest", type);
		logWarning("Pet Adventure outcome is unknown: " + reason + "; retrying at "
				+ retryTime.format(DATETIME_FORMATTER) + "; " + snapshot + ".");
		reschedule(retryTime);
	}

	// ========================================================================
	// HELPER ENUMS
	// ========================================================================

	static boolean countsAsProgress(ChestStartResult outcome) {
		return outcome == ChestStartResult.STARTED;
	}

	/**
	 * Result of attempting to start a chest adventure.
	 */
	enum ChestStartResult {
		/** Chest was idle and In Adventure confirmed the start. */
		STARTED,

		/** No daily attempts remaining (task rescheduled) */
		NO_ATTEMPTS,

		/** Chest not found or could not be started */
		NOT_FOUND,

		/** The screen or action outcome could not be verified. */
		UNKNOWN
	}
}
