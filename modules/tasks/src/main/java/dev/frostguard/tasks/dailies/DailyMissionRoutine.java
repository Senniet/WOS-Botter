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

// More than one reward overlay can appear after Claim All (e.g. milestone chest).
// Each tap is gated by an actual recognized Rewards banner.
private static final int MAX_VERIFIED_REWARD_DISMISSALS = 4;
private static final AreaData REWARD_EXIT_FOOTER = AreaData.of(290, 1140, 430, 1205);

private static final int MAX_INDIVIDUAL_CLAIMS = 20;

private static final int CLAIM_PROGRESS_TOLERANCE_PIXELS = 20;

private static final PointData DAILY_MISSIONS_BUTTON_VALUE = new PointData(50, 1050);

private static final SearchConfig DAILY_SCREEN_TITLE_SEARCH = SearchConfig.builder()
		.withMaxAttempts(1)
		.withDelay(0)
		.withThreshold(87)
		.withArea(new AreaData(new PointData(120, 80), new PointData(600, 225)))
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

private boolean claimActionObserved;

private boolean autoScheduleEnabled;

private int checkOffsetMinutes;

public DailyMissionRoutine(AccountDescriptor profile, TpDailyTaskEnum dailyMission) {
		super(profile, dailyMission);
	}

@Override
	protected void execute() {

        claimActionObserved = false;
		hydrateTaskConfiguration();
		reachDailyMissions();
		boolean dailyScreenReached = switchToDailyMissionsTabFlow();
		boolean claimFlowCompleted = dailyScreenReached && redeemAllRewards();
        if (shouldCountClaim(claimFlowCompleted, claimActionObserved)) {
            StatisticsService.obtain().addToCounter(profile, "Daily Missions Claimed", 1);
        }
        // Never press Android Back when the Daily Missions screen was never
        // confirmed. On a City/World HUD that can open the game's Quit prompt.
        boolean returnedHome = !dailyScreenReached || dismissInterface();
        if (dailyScreenReached && !returnedHome) {
            markWorkIncomplete("Daily Missions rewards handled but Daily dialog did not close to City/World");
        }
        if (!dailyScreenReached) {
            logWarning(routineLogDailyMissionLine(
                    "Daily Missions was not confirmed; skipping blind Back cleanup"));
        }

		configureRecurringBehaviorFlow();
        if (!dailyScreenReached || !claimFlowCompleted || !returnedHome) {
            LocalDateTime retryAt = nextUnverifiedRetry(LocalDateTime.now());
            markWorkIncomplete("Daily Missions: screen or reward claim not verified");
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

void configureRecurringBehaviorFlow() {
        // Auto-schedule changes the timing, not whether this task is recurring.
        // TaskQueue permanently drops non-recurring tasks after each execution.
        setRecurring(true);
        logInfo(routineLogDailyMissionLine("Daily Mission remains scheduled (auto="
                + autoScheduleEnabled + ")"));
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
        // Task Coach 20261116-182434: current original title reached 100% on
        // the real Daily page, while legacy SERVE_EMU_DAILY_TITLE missed all
        // 12 times (15-43%) and repeated costly multi/mono fallback probes.
        // Serve-EMU uses the one proven source; other backends keep their
        // original alternative for backwards compatibility.
        ImageSearchResultData original = templateSearchHelper.locatePattern(
                TemplatesEnum.DAILY_MISSION_SCREEN_TITLE, DAILY_SCREEN_TITLE_SEARCH);
        if (original.isFound() || emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) return original;
        return templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_DAILY_TITLE,
                SearchConfig.builder().withMaxAttempts(1)
                        .withThreshold(90).withArea(AreaData.of(120, 80, 600, 225)).build());
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
                claimActionObserved = claimedCount > 0;
                // Milestone boxes open automatically once enough points are earned.
                return settleAutomaticMilestoneRewards();
			}

			claimedCount++;
			PointData claimPoint = claimResult.getPoint();
			logDebug(routineLogDailyMissionLine("Collecting individual reward #" + claimedCount + " at " + claimPoint));

			tapNear(claimPoint);
            if (!dismissRewardPopupsFlow()) return false;
            sleepTask(350);

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

void hydrateTaskConfiguration() {
        this.autoScheduleEnabled = Boolean.TRUE.equals(profile.getConfig(
                ConfigurationKeyEnum.DAILY_MISSION_AUTO_SCHEDULE_BOOL, Boolean.class));
        Integer configuredOffset = profile.getConfig(
                ConfigurationKeyEnum.DAILY_MISSION_OFFSET_INT, Integer.class);
        int defaultOffset = Integer.parseInt(
                ConfigurationKeyEnum.DAILY_MISSION_OFFSET_INT.getDefaultValue());
        this.checkOffsetMinutes = configuredOffset == null
                ? defaultOffset : Math.max(1, configuredOffset);

		logInfo(routineLogDailyMissionLine(String.format("Configuration - Auto-schedule: %s, Check offset: %d minutes",
				autoScheduleEnabled, checkOffsetMinutes)));
	}

private boolean dismissRewardPopupsFlow() {
        for (int dismissal = 0; dismissal < MAX_VERIFIED_REWARD_DISMISSALS; dismissal++) {
            if (currentDailyTitle().isFound()) return true;
            SearchConfig bannerArea = SearchConfig.builder().withMaxAttempts(1)
                    .withThreshold(90).withArea(AreaData.of(40, 245, 680, 425)).build();
            ImageSearchResultData rewards = templateSearchHelper.locateAction(
                    TemplatesEnum.SERVE_EMU_REWARDS_BANNER, bannerArea,
                    TemplatesEnum.SHARED_REWARDS_BANNER_CURRENT);
            if (!rewards.isFound()) {
                sleepTask(300);
                if (currentDailyTitle().isFound()) return true;
                logWarning(routineLogDailyMissionLine("Neither Daily Missions nor Rewards was recognized; "
                        + captureDailyDiagnostic("daily-reward-transition-unknown")));
                return false;
            }
            logInfo(routineLogDailyMissionLine("Dismissing verified Rewards overlay "
                    + (dismissal + 1) + "/" + MAX_VERIFIED_REWARD_DISMISSALS));
            tapInside(REWARD_EXIT_FOOTER);
            sleepTask(650);
        }
        if (currentDailyTitle().isFound()) return true;
        logWarning(routineLogDailyMissionLine("Reward overlays exceeded safe dismissal limit; "
                + captureDailyDiagnostic("daily-rewards-remain-open")));
        return false;
    }

private boolean redeemAllRewardsAtOnce(ImageSearchResultData claimAllResult) {
		logInfo(routineLogDailyMissionLine("'Claim All' button detected. Collecting all rewards at once"));
		tapInside(claimAllResult);
        sleepTask(750);
        if (!dismissRewardPopupsFlow()) return false;
        sleepTask(250);
        if (seekForClaimAllButton().isFound()) {
            logWarning(routineLogDailyMissionLine("Claim All is still visible after tapping; "
                    + captureDailyDiagnostic("claim-all-unverified")));
            return false;
        }
        logInfo(routineLogDailyMissionLine("Claim All no longer visible after tap."));
        // Point milestone boxes open automatically; never tap the box.
        return settleAutomaticMilestoneRewards();
	}

private boolean settleAutomaticMilestoneRewards() {
        // The 160-point milestone (and other point milestones) opens itself
        // only when enough points have been collected. No chest interaction.
        // Give the game a moment to show any automatically triggered Rewards
        // overlay, then dismiss only visually verified overlays.
        sleepTask(700);
        if (!dismissRewardPopupsFlow()) return false;
        logInfo(routineLogDailyMissionLine(
                "Automatic point milestone rewards settled; no chest tap necessary"));
        return currentDailyTitle().isFound();
    }

private ImageSearchResultData seekForClaimAllButton() {
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            // On the known current 2026 layout, one current frame is enough
            // to reject an absent green Claim All. Old full-screen template
            // + multiscale/mono retries were slow on normal empty days.
            var first=DailyClaimAllFrame.ready(emuManager.captureScreen(EMULATOR_NUMBER));
            if (!first.isFound())return ImageSearchResultData.miss();
            sleepTask(120);
            var second=DailyClaimAllFrame.ready(emuManager.captureScreen(EMULATOR_NUMBER));
            return RecognitionConsensus.agrees(first,second,8)
                    ? second : ImageSearchResultData.miss();
        }
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
        boolean claimed = claimAllResult.isFound()
                ? redeemAllRewardsAtOnce(claimAllResult)
                : redeemRewardsIndividually();
        if (claimed && !currentDailyTitle().isFound()) {
            logWarning(routineLogDailyMissionLine("Claim flow ended outside verified Daily Missions screen; "
                    + captureDailyDiagnostic("daily-postclaim-screen-unverified")));
            return false;
        }
        if (claimed && claimAllResult.isFound()) {
            claimActionObserved = true;
        }
        return claimed;
    }

    static boolean shouldCountClaim(boolean completed, boolean actionObserved) {
        return completed && actionObserved;
    }

private boolean dismissInterface() {
        // Use exact Daily title + current top-right X from the same frame.
        // The old fixed click y133..186 lay BELOW the real X near (690,124).
        var first=emuManager.captureScreen(EMULATOR_NUMBER);
        var x=DailyMissionsCurrentFrame.close(first);
        if (!x.isFound()) {
            logWarning(routineLogDailyMissionLine("Daily header X unverified; refusing blind cleanup tap"));
            return false;
        }
        sleepTask(170);
        var second=DailyMissionsCurrentFrame.close(emuManager.captureScreen(EMULATOR_NUMBER));
        if (!RecognitionConsensus.agrees(x,second,12)) {
            logWarning(routineLogDailyMissionLine("Daily close icon moved or disappeared; no tap"));
            return false;
        }
        PointData p=second.getPoint();
        tapInside(new PointData(p.getX()-7,p.getY()-7),
                new PointData(p.getX()+7,p.getY()+7));
        sleepTask(420);
        // Closing Daily can animate over several frames. Never treat a
        // transient absent City anchor as a permanent failure or tap blindly.
        for (int attempt = 0; attempt < 3; attempt++) {
            var after=emuManager.captureScreen(EMULATOR_NUMBER);
            if (!DailyMissionsCurrentFrame.title(after)) {
                boolean city=emuManager.locatePattern(EMULATOR_NUMBER,after,
                        TemplatesEnum.SERVE_EMU_CITY_ANCHOR,
                        new PointData(590,1145),new PointData(715,1275),90).isFound();
                if (city) {
                    logInfo(routineLogDailyMissionLine("Daily Missions closed, City HUD confirmed"));
                    return true;
                }
            }
            if (attempt < 2) sleepTask(450);
        }
        logWarning(routineLogDailyMissionLine("Daily closed but City HUD not verified; "
                + captureDailyDiagnostic("daily-home-unverified")));
        return false;
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
