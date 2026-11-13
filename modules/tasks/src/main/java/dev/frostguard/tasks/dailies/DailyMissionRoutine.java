package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.engine.service.StatisticsService;
import dev.frostguard.vision.convert.GameTimeUtils;
import java.time.LocalDateTime;

public class DailyMissionRoutine extends DelayedTask {

private static final int FINAL_CHECK_BEFORE_RESET_MINUTES_VALUE = 2;

private static final int SAFETY_RESCHEDULE_MINUTES_VALUE = 30;

private static final int UNVERIFIED_RETRY_MINUTES = 5;

private static final int POPUP_DISMISS_TAP_COUNT_VALUE = 3;

private static final int MAX_INDIVIDUAL_CLAIMS = 20;

private static final int CLAIM_PROGRESS_TOLERANCE_PIXELS = 20;

private static final PointData DAILY_MISSIONS_BUTTON_VALUE = new PointData(50, 1050);

private static final PointData POPUP_DISMISS_MIN_VALUE = new PointData(10, 100);

private static final PointData POPUP_DISMISS_MAX_VALUE = new PointData(600, 120);

private static final SearchConfig DAILY_SCREEN_TITLE_SEARCH = SearchConfig.builder()
		.withMaxAttempts(3)
		.withDelay(300)
		.withThreshold(87)
		.withArea(new AreaData(new PointData(180, 70), new PointData(540, 170)))
		.build();

private static final SearchConfig DAILY_TAB_BUTTON_SEARCH = SearchConfig.builder()
		.withMaxAttempts(3)
		.withDelay(300)
		.withThreshold(90)
		.withArea(new AreaData(new PointData(300, 1050), new PointData(720, 1200)))
		.build();

private static final GreenLabelActionDetector.Region DAILY_CLAIM_ALL_REGION =
        new GreenLabelActionDetector.Region(230, 985, 495, 1070);
private static final GreenLabelActionDetector.Region DAILY_CLAIM_REGION =
        new GreenLabelActionDetector.Region(515, 528, 692, 634);

private boolean autoScheduleEnabled;

private int checkOffsetMinutes;

public DailyMissionRoutine(AccountDescriptor profile, TpDailyTaskEnum dailyMission) {
		super(profile, dailyMission);
	}

@Override
	protected void execute() {

		hydrateTaskConfiguration();
		reachDailyMissions();
		boolean dailyScreenReached = switchToDailyMissionsTabFlow();
		boolean claimFlowCompleted = dailyScreenReached && redeemAllRewards();
		if (claimFlowCompleted) {
			StatisticsService.obtain().addToCounter(profile, "Daily Missions Claimed", 1);
		}
        // Never press Android Back when the Daily Missions screen was never
        // confirmed. On a City/World HUD that can open the game's Quit prompt.
        if (dailyScreenReached) {
            dismissInterface();
        } else {
            logWarning(routineLogDailyMissionLine(
                    "Daily Missions was not confirmed; skipping blind Back cleanup"));
        }

		configureRecurringBehaviorFlow();
        if (!dailyScreenReached || !claimFlowCompleted) {
            LocalDateTime retryAt = nextUnverifiedRetry(LocalDateTime.now());
            reschedule(retryAt);
            logWarning(routineLogDailyMissionLine("Daily mission result unverified; "
                    + "no completion recorded. Retrying at "
                    + retryAt.format(DATETIME_FORMATTER)));
            return;
        }
		queueNextExecution();
	}

@Override
	protected LaunchPoint getRequiredStartLocation() {
		return LaunchPoint.ANY;
	}

private void configureRecurringBehaviorFlow() {
		boolean shouldRecur = !autoScheduleEnabled;
		setRecurring(shouldRecur);

		logInfo(routineLogDailyMissionLine(String.format("Task recurring: %s (auto-schedule: %s)",
				shouldRecur, autoScheduleEnabled)));
	}

private boolean switchToDailyMissionsTabFlow() {
		ImageSearchResultData dailyScreenTitle = currentDailyTitle();

		if (dailyScreenTitle.isFound()) {
			logDebug(routineLogDailyMissionLine("Daily missions screen already selected"));
			return true;
		}

        ImageSearchResultData dailyTabButton = templateSearchHelper.locateAction(
                TemplatesEnum.DAILY_MISSION_DAILY_TAB_BUTTON,
                DAILY_TAB_BUTTON_SEARCH,
                TemplatesEnum.SERVE_EMU_DAILY_TAB_BUTTON);

		if (!dailyTabButton.isFound()) {
			logWarning(routineLogDailyMissionLine("Daily tab button not detected. Skipping claims; "
                    + captureDailyDiagnostic("daily-tab-unrecognized")));
			return false;
		}

		logInfo(routineLogDailyMissionLine("Switching to daily missions tab at " + dailyTabButton.getPoint()));
		tapInside(dailyTabButton);
		sleepTask(500);

		dailyScreenTitle = currentDailyTitle();
		if (!dailyScreenTitle.isFound()) {
			logWarning(routineLogDailyMissionLine("Daily missions title not detected after tab switch. Skipping claims; "
                    + captureDailyDiagnostic("daily-title-unrecognized")));
			return false;
		}

		logInfo(routineLogDailyMissionLine("Daily missions screen confirmed"));
		return true;
	}

private ImageSearchResultData currentDailyTitle() {
        ImageSearchResultData original = templateSearchHelper.locatePattern(
                TemplatesEnum.DAILY_MISSION_SCREEN_TITLE, DAILY_SCREEN_TITLE_SEARCH);
        if (original.isFound()) return original;
        return templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_DAILY_TITLE,
                SearchConfig.builder().withMaxAttempts(2).withDelay(150)
                        .withThreshold(90).withArea(AreaData.of(170, 105, 545, 210)).build());
    }

private ImageSearchResultData seekForIndividualClaimButton() {
		ImageSearchResultData claimButton = templateSearchHelper.locatePattern(
				TemplatesEnum.DAILY_MISSION_CLAIM_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (!claimButton.isFound()) {
            return verifiedGreenAction(DAILY_CLAIM_REGION);
		}

		ImageSearchResultData disabledClaimButton = templateSearchHelper.locatePattern(
				TemplatesEnum.DAILY_MISSION_CLAIM_BUTTON_DISABLED,
				SearchConfigConstants.DEFAULT_SINGLE);
		if (disabledClaimButton.isFound()
				&& sameClaimTarget(claimButton.getPoint(), disabledClaimButton.getPoint())) {
			logDebug(routineLogDailyMissionLine("Ignoring disabled Claim button at " + claimButton.getPoint()));
			return ImageSearchResultData.miss();
		}

		return claimButton;
	}

private boolean sameClaimTarget(PointData first, PointData second) {
		return first.manhattanDistanceTo(second) <= CLAIM_PROGRESS_TOLERANCE_PIXELS;
	}

private String routineLogDailyMissionLine(String note) {
        return "DailyMissionRoutine | " + note;
    }

private LocalDateTime queueFinalCheckBeforeReset(LocalDateTime gameReset) {
		LocalDateTime finalCheck = gameReset.minusMinutes(FINAL_CHECK_BEFORE_RESET_MINUTES_VALUE);
		logInfo(routineLogDailyMissionLine("Scheduling final check before reset at: " +
				finalCheck.format(DATETIME_FORMATTER)));
		return finalCheck;
	}

private boolean redeemRewardsIndividually() {
		logWarning(routineLogDailyMissionLine("'Claim All' button not detected; "
                + captureDailyDiagnostic("claim-all-missing") + ". Collecting individually."));

		int claimedCount = 0;

		while (claimedCount < MAX_INDIVIDUAL_CLAIMS) {
			ImageSearchResultData claimResult = seekForIndividualClaimButton();
			if (!claimResult.isFound()) {
				logInfo(routineLogDailyMissionLine("Individual collecting complete. Claimed " + claimedCount + " rewards"));
				return true;
			}

			claimedCount++;
			PointData claimPoint = claimResult.getPoint();
			logDebug(routineLogDailyMissionLine("Collecting individual reward #" + claimedCount + " at " + claimPoint));

			tapNear(claimPoint);
			dismissRewardPopupsFlow();
			sleepTask(500);

			ImageSearchResultData nextClaim = seekForIndividualClaimButton();
			if (nextClaim.isFound() && sameClaimTarget(claimPoint, nextClaim.getPoint())) {
				sleepTask(500);
				nextClaim = seekForIndividualClaimButton();
				if (nextClaim.isFound() && sameClaimTarget(claimPoint, nextClaim.getPoint())) {
					logWarning(routineLogDailyMissionLine("Claim button remained at " + claimPoint
							+ " after tapping. Stopping because no visual progress was detected"));
					return false;
				}
			}
		}

		logWarning(routineLogDailyMissionLine("Stopped individual claims at safety limit "
				+ MAX_INDIVIDUAL_CLAIMS));
		return false;
	}

private void hydrateTaskConfiguration() {
		this.autoScheduleEnabled = profile.getConfig(
				ConfigurationKeyEnum.DAILY_MISSION_AUTO_SCHEDULE_BOOL,
				Boolean.class);

		this.checkOffsetMinutes = profile.getConfig(
				ConfigurationKeyEnum.DAILY_MISSION_OFFSET_INT,
				Integer.class);

		logInfo(routineLogDailyMissionLine(String.format("Configuration - Auto-schedule: %s, Check offset: %d minutes",
				autoScheduleEnabled, checkOffsetMinutes)));
	}

private void dismissRewardPopupsFlow() {
		tapInside(
				POPUP_DISMISS_MIN_VALUE,
				POPUP_DISMISS_MAX_VALUE,
				POPUP_DISMISS_TAP_COUNT_VALUE,
				150

		);
	}

private boolean redeemAllRewardsAtOnce(ImageSearchResultData claimAllResult) {
		logInfo(routineLogDailyMissionLine("'Claim All' button detected. Collecting all rewards at once"));
		tapInside(claimAllResult);
        sleepTask(750);
		dismissRewardPopupsFlow();
        sleepTask(400);
        if (seekForClaimAllButton().isFound()) {
            logWarning(routineLogDailyMissionLine("Claim All is still visible after tapping; "
                    + captureDailyDiagnostic("claim-all-unverified")));
            return false;
        }
        logInfo(routineLogDailyMissionLine("Claim All no longer visible after tap."));
        return true;
	}

private ImageSearchResultData seekForClaimAllButton() {
        ImageSearchResultData match = templateSearchHelper.locatePattern(
                TemplatesEnum.DAILY_MISSION_CLAIMALL_BUTTON,
                SearchConfigConstants.DEFAULT_SINGLE);
        return match.isFound() ? match : verifiedGreenAction(DAILY_CLAIM_ALL_REGION);
    }

    private ImageSearchResultData verifiedGreenAction(GreenLabelActionDetector.Region region) {
        ImageSearchResultData first = GreenLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)), region);
        if (!first.isFound()) return ImageSearchResultData.miss();
        sleepTask(250);
        ImageSearchResultData second = GreenLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)), region);
        return RecognitionConsensus.agrees(first, second, 8) ? second : ImageSearchResultData.miss();
    }

    private String captureDailyDiagnostic(String kind) {
        return TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER, "daily-missions", kind);
    }

private void reachDailyMissions() {
		logInfo(routineLogDailyMissionLine("Moving to daily missions interface"));

        if (currentDailyTitle().isFound()) return;
        tapNear(DAILY_MISSIONS_BUTTON_VALUE);
        // The next step verifies the title/tab, so a long fixed pause is unnecessary.
        sleepTask(450);

	}

static LocalDateTime nextUnverifiedRetry(LocalDateTime now) {
        return now.plusMinutes(UNVERIFIED_RETRY_MINUTES);
    }

private void queueNextExecution() {
		if (isRecurring()) {
			queueManualMode();
		} else {
			queueAutoMode();
		}
	}

private LocalDateTime queueAtOffsetTime(LocalDateTime proposedTime, LocalDateTime gameReset,
			boolean beforeFinalCheckWindow) {
		LocalDateTime cappedTime = gameReset.minusMinutes(FINAL_CHECK_BEFORE_RESET_MINUTES_VALUE);

		if (beforeFinalCheckWindow && proposedTime.isAfter(cappedTime)) {
			logInfo(routineLogDailyMissionLine("Proposed time exceeds reset window. Capping at: " +
					cappedTime.format(DATETIME_FORMATTER)));
			return cappedTime;
		}

		return proposedTime;
	}

private boolean redeemAllRewards() {
		logInfo(routineLogDailyMissionLine("Scanning for claim buttons"));

		ImageSearchResultData claimAllResult = seekForClaimAllButton();

		if (claimAllResult.isFound()) {
			return redeemAllRewardsAtOnce(claimAllResult);
		} else {
			return redeemRewardsIndividually();
		}
	}

private void dismissInterface() {
		pressBack();
		sleepTask(500);

	}

private void queueManualMode() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime gameReset = GameTimeUtils.dailyResetTime();
		LocalDateTime proposedTime = now.plusMinutes(checkOffsetMinutes);
		LocalDateTime finalCheckTime = gameReset.minusMinutes(FINAL_CHECK_BEFORE_RESET_MINUTES_VALUE);
		boolean beforeFinalCheckWindow = now.isBefore(finalCheckTime);

		LocalDateTime nextExecution;

		if (beforeFinalCheckWindow && proposedTime.isAfter(gameReset)) {
			nextExecution = queueFinalCheckBeforeReset(gameReset);
		} else {
			nextExecution = queueAtOffsetTime(proposedTime, gameReset, beforeFinalCheckWindow);
		}

		reschedule(nextExecution);
		logInfo(routineLogDailyMissionLine("Next execution scheduled for: " + nextExecution.format(DATETIME_FORMATTER) +
				" (Manual mode)"));
	}

private void queueAutoMode() {
		LocalDateTime safetyTime = LocalDateTime.now().plusMinutes(SAFETY_RESCHEDULE_MINUTES_VALUE);
		reschedule(safetyTime);

		logInfo(routineLogDailyMissionLine("Auto-schedule mode - safety reschedule at: " +
				safetyTime.format(DATETIME_FORMATTER)));
	}
}
