package dev.frostguard.tasks.economy;

import java.time.LocalDateTime;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;

/**
 * Task responsible for activating gathering speed boost bonuses.
 * 
 * <p>
 * This task:
 * <ul>
 * <li>Opens the Growth menu</li>
 * <li>Navigates to Gathering Speed section</li>
 * <li>Applies configured boost type (8h or 24h)</li>
 * <li>Handles purchase dialogs if boost items are not in inventory</li>
 * <li>Handles boost replacement confirmations</li>
 * <li>Reschedules based on boost duration</li>
 * </ul>
 * 
 * <p>
 * <b>Boost Types:</b>
 * <ul>
 * <li>8h Boost: 250 gems, reschedules in 8 hours</li>
 * <li>24h Boost: 600 gems, reschedules in 24 hours</li>
 * </ul>
 * 
 * <p>
 * <b>Important:</b>
 * This task should run BEFORE GatherRoutine to ensure
 * gathering marches benefit from the speed boost.
 */
public class GatherSpeedRoutine extends DelayedTask {

	// ========== Navigation Coordinates ==========
	private static final PointData PROFILE_MENU_BUTTON = new PointData(40, 118);
	private static final PointData GROWTH_TAB = new PointData(530, 122);
	private static final PointData GATHERING_SPEED_SECTION = new PointData(313, 406);

	// ========== Boost Button Coordinates ==========
	private static final PointData BOOST_8H_USE_BUTTON = new PointData(578, 568);
	private static final PointData BOOST_24H_USE_BUTTON = new PointData(578, 718);

	// ========== Obtain Dialog Coordinates ==========
	private static final PointData OBTAIN_DIALOG_TEXT_TOP_LEFT = new PointData(267, 126);
	private static final PointData OBTAIN_DIALOG_TEXT_BOTTOM_RIGHT = new PointData(370, 161);
	private static final PointData OBTAIN_BUY_BUTTON = new PointData(580, 387);

	// ========== Purchase Confirmation Dialog ==========
	private static final PointData PURCHASE_DIALOG_TEXT_TOP_LEFT = new PointData(287, 427);
	private static final PointData PURCHASE_DIALOG_TEXT_BOTTOM_RIGHT = new PointData(433, 465);
	private static final PointData PURCHASE_DONT_SHOW_CHECKBOX = new PointData(210, 712);
	private static final PointData PURCHASE_CONFIRM_BUTTON = new PointData(370, 790);

	// ========== Boost Replacement Dialog ==========
	private static final PointData BOOST_REPLACE_TEXT_TOP_LEFT = new PointData(235, 549);
	private static final PointData BOOST_REPLACE_TEXT_BOTTOM_RIGHT = new PointData(580, 584);
	private static final PointData BOOST_REPLACE_CONFIRM_BUTTON = new PointData(505, 777);

	// ========== Constants ==========
	private static final int BOOST_24H_DURATION_HOURS = 24;
	private static final int BOOST_8H_DURATION_HOURS = 8;
	private static final int BOOST_24H_GEM_COST = 600;
	private static final int BOOST_8H_GEM_COST = 250;

	// ========== Configuration (loaded in loadConfiguration()) ==========
	private BoostType boostType;

	public GatherSpeedRoutine(AccountDescriptor profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

	/**
	 * Loads task configuration from profile.
	 */
	private void loadConfiguration() {
		String boostTypeValue = profile.getConfig(
				ConfigurationKeyEnum.GATHER_SPEED_BOOST_TYPE_STRING, String.class);

		// Parse boost type from ComboBox value (e.g., "8h (250 gems)" or "24h (600
		// gems)")
		if (boostTypeValue != null && boostTypeValue.startsWith("24h")) {
			this.boostType = BoostType.BOOST_24H;
		} else {
			this.boostType = BoostType.BOOST_8H; // Default
		}

		logDebug(String.format("Configuration loaded - Boost type: %s",
				boostType.getName()));
	}

	@Override
	protected void execute() {
		loadConfiguration();

		logInfo(String.format("Starting gather speed boost (%s).", boostType.getName()));

		if (!navigateToGatheringSpeed()) {
            markWorkIncomplete("Gather Speed: actual Growth/Gathering Speed screen not verified");
			logWarning("Failed to navigate to gathering speed menu. Rescheduling in 5 minutes.");
			rescheduleDefault();
			return;
		}

		if (!activateBoost()) {
            markWorkIncomplete("Gather Speed: boost activation not verified");
			logWarning("Failed to activate boost. Rescheduling in 5 minutes.");
			rescheduleDefault();
			return;
		}

		closeMenuAndReturn();

		rescheduleForBoostDuration();
	}

	/**
	 * Navigates to the Gathering Speed section in the Growth menu.
	 * 
	 * <p>
	 * Navigation flow:
	 * <ol>
	 * <li>Opens profile menu</li>
	 * <li>Switches to Growth tab</li>
	 * <li>Opens Gathering Speed section</li>
	 * </ol>
	 * 
	 * @return true if navigation succeeded, false otherwise
	 */
	private boolean navigateToGatheringSpeed() {
		logDebug("Opening profile menu");
		tapNear(PROFILE_MENU_BUTTON);
		sleepTask(1000); // Wait for profile menu to open

		logDebug("Switching to Growth tab");
		tapNear(GROWTH_TAB);
		sleepTask(1000); // Wait for tab to load

        // On current Serve-EMU, the legacy blind Frostguard coordinates can
        // open only the Chief HUD. Do not continue to a Use or gem button
        // until the actual Growth context has been observed.
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            String growth=readStringValue(new PointData(170,68),new PointData(660,250),null);
            if (!GatherBoostScreenEvidence.isGrowthTitle(growth)) {
                logWarning("Gather Speed Growth title not confirmed; "
                        + dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots.capture(
                           emuManager,EMULATOR_NUMBER,"gather-boost","growth-screen-unknown"));
                return false;
            }
        }
        logDebug("Opening Gathering Speed section");
		tapNear(GATHERING_SPEED_SECTION);
		sleepTask(1000); // Wait for section to open
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            String section=readStringValue(new PointData(90,145),new PointData(620,690),null);
            if (!GatherBoostScreenEvidence.isGatheringSpeedScreen(section)) {
                logWarning("Gathering Speed page not text-verified; "
                        + dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots.capture(
                           emuManager,EMULATOR_NUMBER,"gather-boost","boost-section-unverified"));
                return false;
            }
        }
		return true;
	}

	/**
	 * Activates the configured boost type and handles any dialogs.
	 * 
	 * @return true if boost was activated, false otherwise
	 */
	private boolean activateBoost() {
		PointData useButton = (boostType == BoostType.BOOST_8H)
				? BOOST_8H_USE_BUTTON
				: BOOST_24H_USE_BUTTON;

        logInfo(String.format("Attempting owned %s boost; purchase is disabled (listed cost %d gems)",
                boostType.getName(), boostType.getGemCost()));

		tapNear(useButton);
		sleepTask(500); // Wait for dialog to appear

		// Handle potential "Obtain" dialog (if boost item not in inventory)
		if (!handleObtainDialog()) {
			logWarning("Failed to handle obtain dialog");
			return false;
		}

		// Do not automatically replace an existing boost. Replacing it can
        // consume an inventory item and is not a free harmless screen tap.
        if (!handleBoostReplacementDialog()) return false;

        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            String state=readStringValue(new PointData(120,265),new PointData(660,1000),null);
            if (!GatherBoostScreenEvidence.isAppliedBoost(state)) {
                logWarning("Gather Speed use clicked, but no confirmed active boost text; "
                        + dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots.capture(
                           emuManager,EMULATOR_NUMBER,"gather-boost","boost-outcome-unknown"));
                return false;
            }
        }
		return true;
	}

	/**
	 * Handles the "Obtain" dialog that appears when boost item is not in inventory.
	 * Detects a missing inventory item and defers; never purchases with gems.
	 * 
	 * @return true if dialog was handled or didn't appear, false if error occurred
	 */
	private boolean handleObtainDialog() {
		logDebug("Checking for Obtain dialog");

		String obtainText = readStringValue(
				OBTAIN_DIALOG_TEXT_TOP_LEFT,
				OBTAIN_DIALOG_TEXT_BOTTOM_RIGHT,
				null);

		if (obtainText == null || obtainText.isEmpty()) {
			logDebug("No Obtain dialog detected");
			return true;
		}

		String cleanText = obtainText.toLowerCase().trim();
		logDebug("Obtain dialog OCR: '" + obtainText + "'");

		if (!cleanText.contains("obtain")) {
			logDebug("Obtain dialog not present");
			return true;
		}

		// Never auto-spend gems in task QA. An Obtain dialog only proves the
        // inventory item is absent; it is NOT a successful boost activation.
        logWarning("Gather Speed requires an item purchase; no gems spent automatically");
        markWorkIncomplete("Gather Speed: Obtain dialog needs purchase, not authorized");
        return false;

	}

	/**
	 * Rejects purchase confirmations: unattended gem spending is disabled.
	 * 
	 * @return true if dialog was handled or didn't appear, false if error occurred
	 */
    private boolean handlePurchaseConfirmation() {
        // Purchasing was removed from unattended boost automation. Never
        // confirm a gem purchase via fixed screen coordinates.
        markWorkIncomplete("Gather Speed: gem purchase disabled");
        return false;
    }

	/**
	 * Refuses automatic replacement of an already active boost; replacement
	 * can waste inventory items and is not a verified free action.
	 */
	private boolean handleBoostReplacementDialog() {
		logDebug("Checking for boost replacement dialog");

		String confirmText = readStringValue(
				BOOST_REPLACE_TEXT_TOP_LEFT,
				BOOST_REPLACE_TEXT_BOTTOM_RIGHT,
				null);

		if (confirmText == null || confirmText.isEmpty()) {
			logDebug("No boost replacement dialog");
			return true;
		}

		String cleanText = confirmText.toLowerCase().trim();
		logDebug("Boost replacement OCR: '" + confirmText + "'");

		// Check for key phrases
		boolean hasActivating = cleanText.contains("activating");
		boolean hasAnother = cleanText.contains("another");
		boolean hasBonus = cleanText.contains("bonus");

		if (hasActivating || hasAnother || hasBonus) {
            logWarning("An existing boost would be replaced; refusing an unverified resource-spending confirmation");
            markWorkIncomplete("Gather Speed: replacement confirmation not authorized");
            return false;
        }
        return true;
	}

	/**
	 * Closes the Growth menu and returns to home.
	 */
	private void closeMenuAndReturn() {
		logDebug("Closing Growth menu");
		pressBack();
		sleepTask(500); // Wait for menu to close
	}

	/**
	 * Reschedules the task based on the boost duration.
	 */
	private void rescheduleForBoostDuration() {
		int hours = boostType.getDurationHours();
		LocalDateTime nextSchedule = LocalDateTime.now().plusHours(hours);

		reschedule(nextSchedule);

		logInfo(String.format("Rescheduled for %d hours later at: %s",
				hours, nextSchedule.format(DATETIME_FORMATTER)));
	}

	/**
	 * Reschedules the task with default timing (5 minutes) in case of errors.
	 */
	private void rescheduleDefault() {
		LocalDateTime nextSchedule = LocalDateTime.now().plusMinutes(5);
		reschedule(nextSchedule);
	}

	@Override
	protected LaunchPoint getRequiredStartLocation() {
		return LaunchPoint.HOME;
	}

	@Override
	public boolean provideDailyMissionProgress() {
		return false;
	}

	/**
	 * Enum representing the two boost types available.
	 */
	private enum BoostType {
		BOOST_24H("24h", BOOST_24H_DURATION_HOURS, BOOST_24H_GEM_COST),
		BOOST_8H("8h", BOOST_8H_DURATION_HOURS, BOOST_8H_GEM_COST);

		private final String name;
		private final int durationHours;
		private final int gemCost;

		BoostType(String name, int durationHours, int gemCost) {
			this.name = name;
			this.durationHours = durationHours;
			this.gemCost = gemCost;
		}

		public String getName() {
			return name;
		}

		public int getDurationHours() {
			return durationHours;
		}

		public int getGemCost() {
			return gemCost;
		}
	}
}
