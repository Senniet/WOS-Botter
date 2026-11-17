package dev.frostguard.tasks.alliance;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.engine.helper.TemplateSearchHelper;
import dev.frostguard.engine.error.HomeNotFoundException;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import java.awt.image.BufferedImage;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.vision.convert.GameTimeUtils;
import java.time.LocalDateTime;

public class AllianceChestRoutine extends DelayedTask {

private static final int TAB_CHANGE_WAIT_TIME_MS = 500;

private static final int CLAIM_WAIT_TIME_MS = 1500;

private static final int SHORT_WAIT_TIME_MS = 300;

public AllianceChestRoutine(AccountDescriptor profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

@Override
	protected void execute() {

		if (!reachAllianceScreen()) {
			deferAndExit("Failed to navigate to alliance screen");
			return;
		}

		if (!openUpAllianceChestScreen()) {
			deferAndExit("Failed to open alliance chest screen");
			return;
		}


        if (!gatherLootChests()) {
            deferAndExit("Loot Claim All outcome not confirmed");
            return;
        }
        if (!gatherAllianceGifts()) {
            deferAndExit("Alliance Gift claim not confirmed");
            return;
        }
        gatherHonorChest();
        if (!restoreHomeScreen()) {
            deferAndExit("Return Home not verified");
            return;
        }
        queueNextRun();
	}

private String routineLogAllianceChestLine(String note) {
        return "AllianceChestRoutine | " + note;
    }

private boolean openUpAllianceChestScreen() {
		ImageSearchResultData allianceChestResult = templateSearchHelper.locatePattern(
				TemplatesEnum.ALLIANCE_CHEST_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);
		if (!allianceChestResult.isFound()) {
			logWarning(routineLogAllianceChestLine("Alliance chest button not detected."));
			return false;
		}

		tapInside(allianceChestResult);
		sleepTask(TAB_CHANGE_WAIT_TIME_MS);
		return true;
	}

private BufferedImage currentFrame() {
        checkPreemption();
        return ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER));
    }

    // Alliance Gift has individual green Claim controls, but no Claim All.
    // A claimed row is removed/reordered; the next green button can occupy
    // the SAME coordinates. Verify the independent key counter changed.
    private boolean gatherIndividualGifts() {
        int claimed = 0;
        for (int i=0; i<35; i++) {
            BufferedImage before = currentFrame();
            var button = AllianceChestLiveFrame.findGiftClaim(before);
            if (!button.isFound()) {
                logInfo(routineLogAllianceChestLine("No more green gift Claims; "
                        + claimed + " validated"));
                return true;
            }
            sleepTask(160);
            var stable = AllianceChestLiveFrame.findGiftClaim(currentFrame());
            if (!RecognitionConsensus.agrees(button, stable, 12)) return false;
            tapInside(stable);
            sleepTask(560);
            boolean confirmed = AllianceChestLiveFrame.keyProgressChanged(before, currentFrame());
            if (!confirmed) {
                sleepTask(330);
                confirmed = AllianceChestLiveFrame.keyProgressChanged(before, currentFrame());
            }
            if (!confirmed) {
                logWarning(routineLogAllianceChestLine("Gift key progress unchanged; "
                        + TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER,
                                "alliance-chest", "gift-claim-unconfirmed")));
                return false;
            }
            claimed++;
        }
        logWarning(routineLogAllianceChestLine("Gift batch limit 35 reached; returning for next pass"));
        return false;
    }

    // Only Loot Claim All opens a full-screen Rewards overlay in the observed
    // 2026 UI. Dismiss once after checking the actual Rewards title.
    private boolean dismissVerifiedLootRewards() {
        AreaData title = AreaData.of(65,245,680,415);
        var search = TemplateSearchHelper.SearchConfig.builder()
                .withMaxAttempts(2).withDelay(170).withThreshold(90)
                .withArea(title).build();
        if (!templateSearchHelper.locatePattern(TemplatesEnum.SHARED_REWARDS_BANNER,search).isFound())
            return false;
        tapNear(new PointData(360,1188));
        sleepTask(450);
        return !templateSearchHelper.locatePattern(TemplatesEnum.SHARED_REWARDS_BANNER,
                TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(1).withThreshold(90).withArea(title).build()).isFound();
    }

private boolean reachAllianceScreen() {
		logInfo(routineLogAllianceChestLine("Moving to alliance screen"));
		tapInside(new PointData(493, 1187), new PointData(561, 1240));
		sleepTask(3000);


		ImageSearchResultData allianceVerification = templateSearchHelper.locatePattern(
				TemplatesEnum.ALLIANCE_CHEST_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);
		return allianceVerification.isFound();
	}

private boolean gatherAllianceGifts() {
        tapInside(new PointData(410,375),new PointData(626,420));
        sleepTask(330);
        // No Claim All on Alliance Gift: skip that obsolete search entirely.
        return gatherIndividualGifts();
    }

private boolean restoreHomeScreen() {
        try {
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME, this::checkPreemption);
            return true;
        } catch (HomeNotFoundException missing) {
            logWarning(routineLogAllianceChestLine("Home not confirmed: " + missing.getMessage()));
            return false;
        }
    }

private boolean gatherLootChests() {
        tapInside(new PointData(56,375),new PointData(320,420));
        sleepTask(320);
        var before = AllianceChestLiveFrame.findLootClaimAll(currentFrame());
        if (!before.isFound()) {
            logInfo(routineLogAllianceChestLine("Loot Claim All disabled/absent; no tap"));
            return true;
        }
        sleepTask(160);
        var stable = AllianceChestLiveFrame.findLootClaimAll(currentFrame());
        if (!RecognitionConsensus.agrees(before,stable,12)) return false;
        tapInside(stable);
        sleepTask(450);
        boolean dismissed = dismissVerifiedLootRewards();
        if (!dismissed)
            logWarning(routineLogAllianceChestLine("Loot Rewards not verified; no repeated tap"));
        return dismissed;
    }

private void gatherHonorChest() {
        Boolean enabled = profile.getConfig(ConfigurationKeyEnum.ALLIANCE_HONOR_CHEST_BOOL,
                Boolean.class);
        if (!Boolean.TRUE.equals(enabled)) return;
        // 2026 Serve-EMU's top chest is a progress display, not an available
        // free Claim at 68,990/150,000. No blind click on this UI.
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            logInfo(routineLogAllianceChestLine("Honor Chest claim unconfirmed; skipping"));
            return;
        }
        var action = templateSearchHelper.locateAction(TemplatesEnum.ALLIANCE_HONOR_CHEST,
                TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(1).withThreshold(93)
                        .withArea(AreaData.of(260,130,470,300)).build());
        if (action.isFound()) tapInside(action);
    }

private void queueNextRun() {
        Integer configured = profile.getConfig(ConfigurationKeyEnum.ALLIANCE_CHESTS_OFFSET_INT,
                Integer.class);
        int offsetMinutes = safeChestInterval(configured);
        LocalDateTime nextExecutionTime = LocalDateTime.now().plusMinutes(offsetMinutes);
		nextExecutionTime = nextExecutionTime.isAfter(GameTimeUtils.dailyResetTime()) ? GameTimeUtils.dailyResetTime()
				: nextExecutionTime;
		reschedule(nextExecutionTime);
		logInfo(routineLogAllianceChestLine("Alliance chest task completed. Next run at: " + nextExecutionTime.format(DATETIME_FORMATTER)));
	}

    static int safeChestInterval(Integer minutes) {
        return minutes != null && minutes > 0 ? minutes : 60;
    }

private void deferAndExit(String reason) {
        markWorkIncomplete("Alliance Chests: " + reason);
		logWarning(routineLogAllianceChestLine(reason + ". Planning next run task to run in 5 minutes."));
		LocalDateTime nextExecutionTime = LocalDateTime.now().plusMinutes(5);
		reschedule(nextExecutionTime);
	}
}
