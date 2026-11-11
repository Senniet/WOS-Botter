package dev.frostguard.tasks.exploration;

import java.time.LocalDateTime;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.SizeData;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;

public class ExplorationRoutine extends DelayedTask {

	private static final PointData EXPLORATION_ENTRY_TOP_LEFT = new PointData(40, 1190);
	private static final PointData EXPLORATION_ENTRY_BOTTOM_RIGHT = new PointData(100, 1250);
	private static final PointData REWARD_POPUP_TOP_LEFT = new PointData(230, 890);
	private static final PointData REWARD_POPUP_BOTTOM_RIGHT = new PointData(490, 960);
	private static final int MAX_OPEN_ATTEMPTS = 2;

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
		boolean claimedAnyReward = opened && claimAvailableRewards();
		if (claimedAnyReward) {
			logInfo(routineLogExplorationLine("Exploration rewards claimed."));
		} else if (!opened) {
			logWarning(routineLogExplorationLine("Exploration screen could not be confirmed; " +
					captureDiagnostic("screen-not-confirmed")));
		} else {
			logInfo(routineLogExplorationLine("No confirmed exploration reward claim."));
		}

		if (opened && (claimedAnyReward || locateDisabledClaimButton().isFound())) {
			rescheduleFromConfiguredOffset();
		} else {
			// A missing button is not proof that a reward was collected or exhausted.
			// Retry later instead of silently waiting for the normal reward interval.
			reschedule(LocalDateTime.now().plusMinutes(10));
		}
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

		if (locateDisabledClaimButton().isFound()
				&& !locateEnabledClaimButton().isFound()) {
			return true;
		}

		ImageSearchResultData explorationButton = templateSearchHelper.locatePattern(
				TemplatesEnum.EXPLORATION_BUTTON, SearchConfigConstants.SINGLE_WITH_2_RETRIES);
		return explorationButton.isFound();
	}

	private boolean claimAvailableRewards() {
		ImageSearchResultData claim = locateEnabledClaimButton();
		if (!claim.isFound()) {
			if (!locateDisabledClaimButton().isFound()) {
				logWarning(routineLogExplorationLine("Neither enabled nor disabled claim state found; "
						+ captureDiagnostic("claim-state-unknown")));
			}
			return false;
		}

		logInfo(routineLogExplorationLine(String.format(
				"Enabled claim detected at %s (score %.2f). Tapping once and verifying state.",
				claim.getPoint(), claim.getMatchPercentage())));
		tapClaimButton(claim);
		sleepTask(800);
		if (locateDisabledClaimButton().isFound()
				&& !locateEnabledClaimButton().isFound()) {
			return true;
		}

		// The reward overlay often hides the claim controls. Preserve the existing
		// dismissal step, but never infer success from a vanished button alone.
		dismissRewardPopup();
		boolean disabled = locateDisabledClaimButton().isFound();
		boolean enabled = locateEnabledClaimButton().isFound();
		if (claimConfirmed(disabled, enabled)) {
			logInfo(routineLogExplorationLine("Claim verified by disabled claim control."));
			return true;
		}
		logWarning(routineLogExplorationLine("Claim outcome unknown (disabled=" + disabled
				+ ", enabled=" + enabled + "); no repeated tap; "
				+ captureDiagnostic("claim-unverified")));
		return false;
	}

	static boolean claimConfirmed(boolean disabledVisible, boolean enabledVisible) {
		return disabledVisible && !enabledVisible;
	}

	private String captureDiagnostic(String kind) {
		return TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER, "exploration-chest", kind);
	}

    private static final GreenLabelActionDetector.Region EXPLORATION_CLAIM_REGION =
            new GreenLabelActionDetector.Region(535, 830, 701, 909);

	private ImageSearchResultData locateEnabledClaimButton() {
        // The saved 2026-11-11 frame scored 94.1%, just shy of the old 95%.
        // Restrict the modest threshold adjustment to the observed location.
		ImageSearchResultData matched = templateSearchHelper.locatePattern(
				TemplatesEnum.EXPLORATION_CLAIM,
                dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig.builder()
                    .withMaxAttempts(2).withDelay(250).withThreshold(92)
                    .withArea(AreaData.of(525, 804, 710, 926)).build());
        if (matched.isFound()) return matched;
        // No blind coordinate taps: require green body and white label on two
        // independently captured frames of the verified Exploration screen.
        ImageSearchResultData first = GreenLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)), EXPLORATION_CLAIM_REGION);
        if (!first.isFound()) return ImageSearchResultData.miss();
        sleepTask(250);
        ImageSearchResultData second = GreenLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)), EXPLORATION_CLAIM_REGION);
        return RecognitionConsensus.agrees(first, second, 8) ? second : ImageSearchResultData.miss();
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

	private void rescheduleFromConfiguredOffset() {
		Integer minutes = profile.getConfig(ConfigurationKeyEnum.INT_EXPLORATION_CHEST_OFFSET, Integer.class);
		LocalDateTime nextSchedule = LocalDateTime.now().plusMinutes(minutes);
		reschedule(nextSchedule);
		logInfo(routineLogExplorationLine("Next execution scheduled at: "
				+ nextSchedule.format(DATETIME_FORMATTER) + " (" + minutes + " minutes)."));
	}

}
