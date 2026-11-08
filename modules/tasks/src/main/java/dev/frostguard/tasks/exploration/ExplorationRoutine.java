package dev.frostguard.tasks.exploration;

import java.time.LocalDateTime;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.SizeData;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;

public class ExplorationRoutine extends DelayedTask {

	private static final PointData EXPLORATION_ENTRY_TOP_LEFT = new PointData(40, 1190);
	private static final PointData EXPLORATION_ENTRY_BOTTOM_RIGHT = new PointData(100, 1250);
	private static final PointData REWARD_POPUP_TOP_LEFT = new PointData(230, 890);
	private static final PointData REWARD_POPUP_BOTTOM_RIGHT = new PointData(490, 960);
	private static final int MAX_OPEN_ATTEMPTS = 2;
	private static final int UNKNOWN_RETRY_MINUTES = 10;

	public ExplorationRoutine(AccountDescriptor profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	protected LaunchPoint getRequiredStartLocation() {
		return LaunchPoint.WORLD;
	}

	@Override
	protected void execute() {
		logInfo(routineLogExplorationLine("Opening Exploration screen from world view."));
		boolean opened = openExplorationScreen();
		ExplorationClaimOutcome claimOutcome = opened
				? claimAvailableRewards()
				: ExplorationClaimOutcome.UNKNOWN;
		if (claimOutcome == ExplorationClaimOutcome.CLAIMED) {
			logInfo(routineLogExplorationLine("Exploration rewards claimed."));
		} else if (claimOutcome == ExplorationClaimOutcome.NONE_AVAILABLE) {
			logInfo(routineLogExplorationLine("No exploration rewards to claim."));
		} else {
			logWarning(routineLogExplorationLine(
					"Exploration claim outcome is unknown. Scheduling a bounded retry instead of normal completion."));
		}

		rescheduleFromConfiguredOffset(claimOutcome);
		pressBack();
		sleepTask(500);
	}

	private String routineLogExplorationLine(String note) {
		return "ExplorationRoutine | " + note;
	}

	private boolean openExplorationScreen() {
		for (int attempt = 1; attempt <= MAX_OPEN_ATTEMPTS; attempt++) {
			tapInside(EXPLORATION_ENTRY_TOP_LEFT, EXPLORATION_ENTRY_BOTTOM_RIGHT);
			sleepTask(1000);

			if (isExplorationScreenVisible()) {
				logDebug(routineLogExplorationLine("Exploration screen confirmed on attempt " + attempt + "."));
				return true;
			}

			logDebug(routineLogExplorationLine("Exploration screen not confirmed on attempt " + attempt + "."));
		}
		return false;
	}

	private boolean isExplorationScreenVisible() {
		if (locateEnabledClaimButton().isFound()) {
			return true;
		}

		if (locateDisabledClaimButton().isFound()) {
			return true;
		}

		ImageSearchResultData explorationButton = templateSearchHelper.locatePattern(
				TemplatesEnum.EXPLORATION_BUTTON, SearchConfigConstants.SINGLE_WITH_2_RETRIES);
		return explorationButton.isFound();
	}

	private ExplorationClaimOutcome claimAvailableRewards() {
		ImageSearchResultData claimResult = locateEnabledClaimButton();
		if (!claimResult.isFound()) {
			logDebug(routineLogExplorationLine("Enabled claim button not detected."));
			return ExplorationClaimOutcome.NONE_AVAILABLE;
		}

		logInfo(routineLogExplorationLine(String.format(
				"Enabled claim button detected at %s with score %.2f. Pressing once.",
				claimResult.getPoint(), claimResult.getMatchPercentage())));
		tapClaimButton(claimResult);
		sleepTask(800);
		dismissRewardPopup();

		ImageSearchResultData disabledClaim = locateDisabledClaimButton();
		if (disabledClaim.isFound()) {
			logInfo(routineLogExplorationLine(String.format(
					"Disabled claim button detected at %s with score %.2f; reward claim succeeded.",
					disabledClaim.getPoint(), disabledClaim.getMatchPercentage())));
			return ExplorationClaimOutcome.CLAIMED;
		}

		ImageSearchResultData remainingEnabledClaim = locateEnabledClaimButton();
		if (!remainingEnabledClaim.isFound()) {
			logInfo(routineLogExplorationLine("Claim button no longer visible after the tap; reward claim succeeded."));
			return ExplorationClaimOutcome.CLAIMED;
		}

		logWarning(routineLogExplorationLine(
				"Enabled claim button remained after the claim tap; the result is unknown."));
		return ExplorationClaimOutcome.UNKNOWN;
	}

	private ImageSearchResultData locateEnabledClaimButton() {
		return templateSearchHelper.locatePattern(
				TemplatesEnum.EXPLORATION_CLAIM, SearchConfigConstants.STRICT_MATCHING);
	}

	private ImageSearchResultData locateDisabledClaimButton() {
		return templateSearchHelper.locatePattern(
				TemplatesEnum.EXPLORATION_CLAIM_DISABLED, SearchConfigConstants.SINGLE_WITH_2_RETRIES);
	}

	private void tapClaimButton(ImageSearchResultData claimResult) {
		SizeData templateSize = claimResult.getTemplateSize();
		if (templateSize == null || templateSize.getWidth() <= 0 || templateSize.getHeight() <= 0) {
			tapInside(claimResult);
			return;
		}

		PointData center = claimResult.getPoint();
		PointData topLeft = new PointData(
				center.getX() - templateSize.getWidth() / 2,
				center.getY() - templateSize.getHeight() / 2);
		PointData bottomRight = new PointData(
				center.getX() + templateSize.getWidth() / 2,
				center.getY() + templateSize.getHeight() / 2);
		tapInside(topLeft, bottomRight);
	}

	private void dismissRewardPopup() {
		tapInside(REWARD_POPUP_TOP_LEFT, REWARD_POPUP_BOTTOM_RIGHT);
		sleepTask(500);
	}

	private void rescheduleFromConfiguredOffset(ExplorationClaimOutcome outcome) {
		Integer configuredOffset = profile.getConfig(
				ConfigurationKeyEnum.INT_EXPLORATION_CHEST_OFFSET, Integer.class);
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime nextSchedule = ExplorationSchedulePolicy.nextRun(
				now, configuredOffset, UNKNOWN_RETRY_MINUTES, outcome);
		reschedule(nextSchedule);
		if (outcome == ExplorationClaimOutcome.UNKNOWN) {
			logWarning(routineLogExplorationLine("Unknown result; retry scheduled at "
					+ nextSchedule.format(DATETIME_FORMATTER) + " (" + UNKNOWN_RETRY_MINUTES + " minutes)."));
		} else {
			logInfo(routineLogExplorationLine("Next execution scheduled at: "
					+ nextSchedule.format(DATETIME_FORMATTER) + " (" + configuredOffset + " minutes)."));
		}
	}

	enum ExplorationClaimOutcome {
		CLAIMED,
		NONE_AVAILABLE,
		UNKNOWN
	}

}
