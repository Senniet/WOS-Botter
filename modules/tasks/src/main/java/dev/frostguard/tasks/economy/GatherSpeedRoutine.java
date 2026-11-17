package dev.frostguard.tasks.economy;

import java.time.LocalDateTime;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
	private static final PointData CITY_BONUS_HUD_SHORTCUT = new PointData(30, 142);
	private static final PointData GROWTH_TAB = new PointData(530, 122);
	private static final PointData GATHERING_SPEED_SECTION = new PointData(657, 407);

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

        // Gathering Speed automation uses only the explicitly permitted
        // 8-hour option. A legacy stored 24h setting must not buy 600-gem
        // items without separate authorization.
        this.boostType = BoostType.BOOST_8H;

		logDebug(String.format("Configuration loaded - Boost type: %s",
				boostType.getName()));
	}

	@Override
	protected void execute() {
		loadConfiguration();

		logInfo(String.format("Starting gather speed boost (%s).", boostType.getName()));

		if (!navigateToGatheringSpeed()) {
            markWorkIncomplete("Gather Speed: actual Growth/Gathering Speed screen not verified");
            logWarning("Failed to navigate to gathering speed menu. Retrying in one hour.");
            closeMenuAndReturn();
            rescheduleDefault();
			return;
		}

		if (!activateBoost()) {
            markWorkIncomplete("Gather Speed: boost activation not verified");
            logWarning("Boost activation not verified. Check retained purchase cooldown before retry.");
            closeMenuAndReturn();
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
    private boolean cityBonusVisible() {
        return emuManager.locatePattern(EMULATOR_NUMBER,
                emuManager.captureScreen(EMULATOR_NUMBER),
                dev.frostguard.api.configs.TemplatesEnum.SERVE_EMU_CITY_BONUS_TITLE_2026,
                new PointData(75, 0), new PointData(315, 90), 90).isFound();
    }

    private boolean navigateToGatheringSpeed() {
        if (!cityBonusVisible()) {
            // Live Serve-EMU 720x1280: City Bonus opens from the small
            // arrow at the left of the City HUD, not the Chief portrait.
            tapNear(CITY_BONUS_HUD_SHORTCUT);
            sleepTask(450);
        }
        if (!cityBonusVisible()) {
            logWarning("City Bonus title not confirmed; refusing unrelated taps");
            return false;
        }
        tapNear(GROWTH_TAB);
        sleepTask(400);
        String growth=readStringValue(new PointData(385, 75),
                new PointData(670, 145), null);
        String section=readStringValue(new PointData(145, 335),
                new PointData(610, 468), null);
        if (!GatherBoostScreenEvidence.isGrowthTitle(growth)
                || !GatherBoostScreenEvidence.isGatheringSpeedScreen(section)) {
            logWarning("City Bonus Growth/Gathering Speed not verified");
            return false;
        }
        // Opens the Gathering Speed accordion. This is not the Use action.
        tapNear(GATHERING_SPEED_SECTION);
        sleepTask(350);
        String options=readStringValue(new PointData(155, 490),
                new PointData(695, 785), null);
        if (!GatherBoostScreenEvidence.isGatheringSpeedScreen(options)
                || !options.toLowerCase(java.util.Locale.ROOT).contains("8h")) {
            logWarning("8h Gathering Speed option not confirmed");
            return false;
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

        logInfo("Attempting verified 8h Gathering Speed boost; at most one 250-gem purchase per 24 hours");

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
        String heading=readStringValue(new PointData(215, 107),
                new PointData(490, 175), null);
        if (!GatherBoostScreenEvidence.isObtainDialog(heading)) {
            logDebug("No Obtain dialog detected; checking owned boost use");
            return true;
        }
        String product=readStringValue(new PointData(170, 195),
                new PointData(510, 310), null);
        String offer=readStringValue(new PointData(465, 345),
                new PointData(683, 437), null);
        if (!GatherBoostScreenEvidence.isEightHourProduct(product)
                || !GatherBoostScreenEvidence.isAllowedPurchase(offer)) {
            logWarning("Obtain dialog product or 250-gem price not verified; no purchase");
            return false;
        }
        if (!reservePurchaseWindow()) {
            logInfo("8h gem spending cooldown is active; purchase skipped");
            rescheduleForBoostDuration();
            return false;
        }
        logInfo("Verified Gather Speed 8h Buy & Use for 250 gems; one purchase reserved");
        tapNear(OBTAIN_BUY_BUTTON);
        sleepTask(650);
        return handlePurchaseConfirmation();
    }

    private boolean reservePurchaseWindow() {
        // Durable across restarts and forced manual task starts. Write BEFORE
        // the purchasing tap to prevent retries after uncertain outcomes.
        // At most 250 gems per account in a rolling 24-hour period.
        try {
            Path directory=Path.of(System.getProperty("user.home"),
                    ".survivalops", "gather-boost-purchases");
            Files.createDirectories(directory);
            Path stamp=directory.resolve("profile-"+profile.getId()+".timestamp");
            synchronized (GatherSpeedRoutine.class) {
                if (Files.exists(stamp)) {
                    Instant last=Instant.parse(Files.readString(stamp).trim());
                    if (last.plusSeconds(24*3600).isAfter(Instant.now())) return false;
                }
                Files.writeString(stamp, Instant.now().toString(),
                        StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
                return true;
            }
        } catch (Exception error) {
            logWarning("Cannot verify persistent gem spending limit: "+error.getMessage());
            return false;
        }
    }

	/**
	 * Rejects purchase confirmations: unattended gem spending is disabled.
	 * 
	 * @return true if dialog was handled or didn't appear, false if error occurred
	 */
    private boolean handlePurchaseConfirmation() {
        // Buying may immediately apply the boost, or show a confirmation.
        // Never press an unverified confirmation or accept a higher price.
        String confirmation=readStringValue(new PointData(185, 400),
                new PointData(620, 810), null);
        if (GatherBoostScreenEvidence.isPurchaseConfirmation(confirmation)) {
            tapNear(PURCHASE_CONFIRM_BUTTON);
            sleepTask(600);
            return true;
        }
        // No reliable confirmation: leave cooldown reserved and do not retry
        // a potentially completed spend blindly.
        logInfo("No 250-gem confirmation verified; checking boost result");
        return true;
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
        // Never spin on a shop failure every five minutes. A fresh,
        // unverified attempt waits one hour; a purchase attempt has its
        // separate durable 24-hour expenditure cooldown.
        LocalDateTime nextSchedule = LocalDateTime.now().plusHours(1);
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
