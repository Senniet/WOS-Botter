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
    @Override
    protected boolean acceptsInjections() { return false; }

	private static final PointData EXPLORATION_ENTRY_TOP_LEFT = new PointData(40, 1190);
	private static final PointData EXPLORATION_ENTRY_BOTTOM_RIGHT = new PointData(100, 1250);
    private static final PointData REWARD_POPUP_TOP_LEFT = new PointData(340, 1125);
    private static final PointData REWARD_POPUP_BOTTOM_RIGHT = new PointData(385, 1185);
    // 2026-11-11: the outer Claim opens a second "Idle Income" window.
    // Its inner, centered Claim must be recognised and pressed before Rewards appears.
    private static final GreenLabelActionDetector.Region IDLE_INCOME_CLAIM_REGION =
            new GreenLabelActionDetector.Region(213, 875, 507, 972);
    private static final PointData IDLE_INCOME_CLOSE_TOP_LEFT = new PointData(613, 275);
    private static final PointData IDLE_INCOME_CLOSE_BOTTOM_RIGHT = new PointData(643, 316);
    private static final dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig IDLE_INCOME_TITLE_SEARCH =
            dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig.builder()
                    .withMaxAttempts(1).withThreshold(88)
                    .withArea(AreaData.of(140, 220, 550, 370)).build();

	private static final int MAX_OPEN_ATTEMPTS = 2;

	public ExplorationRoutine(AccountDescriptor profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	protected LaunchPoint getRequiredStartLocation() {
		// An interrupted claim may have left the Idle Income window open.
		return LaunchPoint.ANY;
	}

	@Override
	protected void execute() {
        if (!isExplorationScreenVisible()) {
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.WORLD);
        }
		logInfo(routineLogExplorationLine("Opening or resuming Exploration rewards."));
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
        if (idleIncomeVisible()) return true;
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

    private boolean isExplorationMainScreenVisible() {
        return templateSearchHelper.locatePattern(TemplatesEnum.EXPLORATION_SCREEN_TITLE,
                dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig.builder()
                    .withMaxAttempts(1).withThreshold(90)
                    .withArea(AreaData.of(70, 5, 365, 90)).build()).isFound();
    }

    private boolean claimAvailableRewards() {
        // A previously-open Idle Income panel can survive a failed run.
        if (idleIncomeVisible()) return claimIdleIncome();

        ImageSearchResultData outer = locateEnabledClaimButton();
        if (!outer.isFound()) {
            if (!locateDisabledClaimButton().isFound()) {
                logWarning(routineLogExplorationLine("No outer Claim or Idle Income found; "
                        + captureDiagnostic("claim-state-unknown")));
            }
            return false;
        }
        logInfo(routineLogExplorationLine(String.format(
                "Outer Claim found at %s (score %.2f). Opening Idle Income.",
                outer.getPoint(), outer.getMatchPercentage())));
        tapClaimButton(outer);
        sleepTask(700);

        // The outer button does not claim the loot: it opens a second Claim.
        if (idleIncomeVisible()) return claimIdleIncome();
        // Some UI versions may go straight to Rewards; still require evidence.
        if (rewardOverlayAppeared()) return finishConfirmedRewards();
        logWarning(routineLogExplorationLine("Outer Claim did not lead to Idle Income or Rewards; "
                + captureDiagnostic("outer-claim-unconfirmed")));
        return false;
    }

    private boolean claimIdleIncome() {
        if (!idleIncomeVisible()) return false;
        ImageSearchResultData first = detectIdleIncomeClaim();
        if (!first.isFound()) {
            logWarning(routineLogExplorationLine("Idle Income opened but inner green Claim absent; "
                    + captureDiagnostic("inner-claim-missing")));
            closeIdleIncomeIfVisible();
            return false;
        }
        sleepTask(250);
        ImageSearchResultData second = detectIdleIncomeClaim();
        if (!RecognitionConsensus.agrees(first, second, 8) || !idleIncomeVisible()) {
            logWarning(routineLogExplorationLine("Inner Claim could not be verified in two frames; "
                    + captureDiagnostic("inner-claim-unstable")));
            closeIdleIncomeIfVisible();
            return false;
        }
        logInfo(routineLogExplorationLine("Idle Income Claim verified; tapping once at " + second.getPoint()));
        tapInside(second);
        sleepTask(650);
        if (rewardOverlayAppeared()) return finishConfirmedRewards();
        logWarning(routineLogExplorationLine("Inner Claim tapped but Rewards screen not confirmed; "
                + captureDiagnostic("inner-claim-unconfirmed")));
        closeIdleIncomeIfVisible();
        return false;
    }

    private ImageSearchResultData detectIdleIncomeClaim() {
        return GreenLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)),
                IDLE_INCOME_CLAIM_REGION);
    }

    private boolean idleIncomeVisible() {
        return templateSearchHelper.locatePattern(TemplatesEnum.EXPLORATION_IDLE_INCOME_TITLE,
                IDLE_INCOME_TITLE_SEARCH).isFound();
    }

    private boolean finishConfirmedRewards() {
        logInfo(routineLogExplorationLine("Rewards banner confirmed: claim succeeded."));
        dismissRewardPopup();
        if (rewardsOverlayVisible()) {
            logWarning(routineLogExplorationLine("Rewards confirmed, but overlay did not close; "
                    + captureDiagnostic("reward-overlay-still-open")));
            return true; // Rewards received, do not falsely schedule another immediate claim.
        }
        closeIdleIncomeIfVisible();
        return true;
    }

    private void closeIdleIncomeIfVisible() {
        if (!idleIncomeVisible()) return;
        tapInside(IDLE_INCOME_CLOSE_TOP_LEFT, IDLE_INCOME_CLOSE_BOTTOM_RIGHT);
        sleepTask(450);
        if (idleIncomeVisible()) logWarning(routineLogExplorationLine(
                "Idle Income remained open after close; " + captureDiagnostic("idle-income-still-open")));
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

    private boolean rewardsOverlayVisible() {
        if (templateSearchHelper.locatePattern(TemplatesEnum.SHARED_REWARDS_BANNER_CURRENT,
                dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(1).withThreshold(92)
                        .withArea(AreaData.of(50, 245, 670, 400)).build()).isFound()) return true;
        return templateSearchHelper.locatePattern(TemplatesEnum.SHARED_REWARDS_BANNER,
                dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(1).withThreshold(85)
                        .withArea(AreaData.of(30, 245, 690, 400)).build()).isFound();
    }

    private boolean rewardOverlayAppeared() {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (rewardsOverlayVisible()) return true;
            if (attempt < 2) sleepTask(300);
        }
        return false;
    }

	private void dismissRewardPopup() {
        // The Rewards panel explicitly says "Tap anywhere to exit". Tap its
        // empty lower area *only after* its banner has been identified.
        tapInside(REWARD_POPUP_TOP_LEFT, REWARD_POPUP_BOTTOM_RIGHT);
        sleepTask(750);
	}

	private void rescheduleFromConfiguredOffset() {
		Integer minutes = profile.getConfig(ConfigurationKeyEnum.INT_EXPLORATION_CHEST_OFFSET, Integer.class);
		LocalDateTime nextSchedule = LocalDateTime.now().plusMinutes(minutes);
		reschedule(nextSchedule);
		logInfo(routineLogExplorationLine("Next execution scheduled at: "
				+ nextSchedule.format(DATETIME_FORMATTER) + " (" + minutes + " minutes)."));
	}

}
