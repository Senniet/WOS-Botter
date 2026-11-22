package dev.frostguard.tasks.exploration;

import java.time.LocalDateTime;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.OcrSettingsData;
import java.util.Locale;
import dev.frostguard.api.domain.SizeData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.error.TaskOutcomeUnknownException;
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
    private static final int UNKNOWN_RETRY_MINUTES = 10;

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
        // Classify the actual page first. City/World use the same direct
        // bottom-left shortcut; never navigate via other menus or Explore.
        RawImageData startingFrame=emuManager.captureScreen(EMULATOR_NUMBER);
        boolean onExploration=ExplorationPageFrame.title(startingFrame);
        boolean onRoot=false;
        boolean onIdleIncome=false;
        if (!onExploration) {
            onRoot=ExplorationRootFrame.root(startingFrame)
                    !=ExplorationRootFrame.Root.UNKNOWN;
            if (!onRoot) {
                onIdleIncome=idleIncomeTitleOnFrame(startingFrame);
            }
        }
        if (!onExploration && !onRoot && !onIdleIncome) {
            // Only unknown pages need the slower OCR recovery.
            onIdleIncome=idleIncomeVisible();
        }
        if (!onExploration && !onRoot && !onIdleIncome) {
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME,this::checkPreemption);
            onRoot=ExplorationRootFrame.root(emuManager.captureScreen(EMULATOR_NUMBER))
                    !=ExplorationRootFrame.Root.UNKNOWN;
        }
        logInfo(routineLogExplorationLine("Opening or resuming Exploration rewards."));
        boolean opened=openExplorationScreen(onExploration,onIdleIncome,onRoot);
		boolean claimedAnyReward = opened && claimAvailableRewards(onIdleIncome);
		if (claimedAnyReward) {
			logInfo(routineLogExplorationLine("Exploration rewards claimed."));
		} else if (!opened) {
			logWarning(routineLogExplorationLine("Exploration screen could not be confirmed; " +
					captureDiagnostic("screen-not-confirmed")));
		} else {
			logInfo(routineLogExplorationLine("No confirmed exploration reward claim."));
		}

        boolean noRewardAvailable = opened && !claimedAnyReward && locateDisabledClaimButton().isFound();
        ExplorationClaimOutcome outcome = claimedAnyReward ? ExplorationClaimOutcome.CLAIMED
                : noRewardAvailable ? ExplorationClaimOutcome.NONE_AVAILABLE
                : ExplorationClaimOutcome.UNKNOWN;
        rescheduleFromConfiguredOffset(outcome);
        // Previous code tapped y78..135 even though the live Exploration
        // Back arrow is at y0..70. This sometimes hit scenery, leaving the
        // task on Exploration and still logging SUCCEEDED.
        if (!returnHomeAfterExploration()) {
            markWorkIncomplete("Exploration Chest: claim flow ended without verified City Home");
        }
        if (outcome == ExplorationClaimOutcome.UNKNOWN) {
            throw new TaskOutcomeUnknownException(
                    "Exploration reward outcome unverified; scheduled retry instead of reporting task success.");
        }
    }

	private String routineLogExplorationLine(String note) {
		return "ExplorationRoutine | " + note;
	}

    private boolean returnHomeAfterExploration() {
        var firstFrame=emuManager.captureScreen(EMULATOR_NUMBER);
        if (ExplorationPageFrame.title(firstFrame)) {
            var first=ExplorationPageFrame.verifiedBackCandidate(firstFrame);
            if(!first.isFound()) {
                logWarning(routineLogExplorationLine("Exploration Back arrow missing on known page"));
                return false;
            }
            sleepTask(180);
            var second=ExplorationPageFrame.verifiedBackCandidate(
                    emuManager.captureScreen(EMULATOR_NUMBER));
            if (!dev.frostguard.engine.helper.RecognitionConsensus.agrees(first,second,15)) {
                logWarning(routineLogExplorationLine("Exploration Back arrow changed; no stale tap"));
                return false;
            }
            tapNear(second.getPoint());
            sleepTask(550);
            if (ExplorationPageFrame.title(emuManager.captureScreen(EMULATOR_NUMBER))) {
                logWarning(routineLogExplorationLine("Exploration still open after verified Back; no blind retry"));
                return false;
            }
        }
        // Already on City: do not run the generic Home navigator, which can
        // interpret the bottom-left Explore shortcut as its next action.
        if (ExplorationRootFrame.root(emuManager.captureScreen(EMULATOR_NUMBER))
                == ExplorationRootFrame.Root.CITY) {
            logInfo(routineLogExplorationLine("City Home already verified; no extra Explore tap"));
            return true;
        }
        try {
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME,this::checkPreemption);
            logInfo(routineLogExplorationLine("City Home verified after claim/no-claim outcome"));
            return true;
        } catch (dev.frostguard.engine.error.HomeNotFoundException missing) {
            logWarning(routineLogExplorationLine("Unable to return Home safely: "+missing.getMessage()));
            return false;
        }
    }

    private boolean openExplorationScreen(boolean onExploration, boolean onIdleIncome,
                                          boolean rootVerified) {
        if(onExploration || onIdleIncome)return true;
        if(!rootVerified){
            logWarning(routineLogExplorationLine("No verified City/World shortcut; refusing blind entry tap"));
            return false;
        }
        for(int attempt=0;attempt<MAX_OPEN_ATTEMPTS;attempt++){
            checkPreemption();
            tapInside(EXPLORATION_ENTRY_TOP_LEFT,EXPLORATION_ENTRY_BOTTOM_RIGHT);
            // Observe the actual screen transition instead of a mandatory
            // 1-second sleep followed by multiple global template searches.
            // A single title template on each new screenshot is sufficient
            // to establish the destination before looking for Claim.
            for(int frame=0;frame<8;frame++){
                sleepTask(160);
                if(ExplorationPageFrame.title(emuManager.captureScreen(EMULATOR_NUMBER))) {
                    logDebug(routineLogExplorationLine("Exploration page ready after entry "+(attempt+1)));
                    return true;
                }
            }
            // Never tap the shortcut again on an unrelated game screen.
            if(ExplorationRootFrame.root(emuManager.captureScreen(EMULATOR_NUMBER))
                    ==ExplorationRootFrame.Root.UNKNOWN)break;
        }
        return false;
    }

    private boolean idleIncomeTitleOnFrame(RawImageData frame) {
        if(frame==null || !frame.isValid() || frame.getWidth()!=720 || frame.getHeight()!=1280)
            return false;
        return OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_IDLE_INCOME_TITLE_NOV_2026.getTemplate(),
                new PointData(210,233),new PointData(520,330),90).isFound();
    }

    private boolean claimAvailableRewards(boolean alreadyOnIdleIncome) {
        // We just confirmed either the Exploration page or Idle Income.
        // Do not OCR the absent Idle Income title on every valid Explore page.
        if (alreadyOnIdleIncome) return claimIdleIncome();

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
        // Live Income page: NOV_2026 matches 100% at the correct title ROI.
        // EXPLORATION_IDLE_INCOME_TITLE and SERVE_EMU_IDLE_INCOME_TITLE were
        // 21-29% on the same confirmed page, so scanning them only wastes time.
        if (templateSearchHelper.locatePattern(
                TemplatesEnum.SERVE_EMU_IDLE_INCOME_TITLE_NOV_2026,
                dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(1).withThreshold(90)
                        .withArea(AreaData.of(210, 233, 520, 330)).build()).isFound()) return true;
        // The 13-Nov serve-emu frame clearly displays Idle Income, but its
        // newer title artwork fails both legacy templates. Confirm the text
        // in the narrow title strip; never treat a generic green button as proof.
        try {
            String title = emuManager.readText(EMULATOR_NUMBER,
                    new PointData(235, 250), new PointData(495, 311),
                    OcrSettingsData.builder()
                            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build());
            return isIdleIncomeTitle(title);
        } catch (Exception failure) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException("Idle Income OCR interrupted");
            }
            logDebug("Idle Income title OCR unavailable: " + failure.getClass().getSimpleName());
            return false;
        }
    }

    static boolean isIdleIncomeTitle(String title) {
        if (title == null) return false;
        String normalized = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return normalized.contains("idleincome");
    }

    private boolean finishConfirmedRewards() {
        logInfo(routineLogExplorationLine("Rewards banner confirmed; verifying dismissal before completion"));
        if (!dismissRewardPopup()) {
            markWorkIncomplete("Exploration Chest reward received but Rewards window not closed");
            logWarning(routineLogExplorationLine("Rewards overlay did not close; "
                    + captureDiagnostic("reward-overlay-still-open")));
            return false;
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
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            // The old EXPLORATION_CLAIM crop includes "Claim" and can
            // still match the inactive grey button. Confirm *green fill* on
            // a verified page instead; absent buttons cost just one ADB frame.
            var first=ExplorationChestActionFrame.enabled(
                    emuManager.captureScreen(EMULATOR_NUMBER));
            if (!first.isFound())return ImageSearchResultData.miss();
            sleepTask(120);
            var second=ExplorationChestActionFrame.enabled(
                    emuManager.captureScreen(EMULATOR_NUMBER));
            return RecognitionConsensus.agrees(first,second,8)
                    ? second : ImageSearchResultData.miss();
        }
        ImageSearchResultData matched = templateSearchHelper.locatePattern(
                TemplatesEnum.EXPLORATION_CLAIM,
                dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig.builder()
                    .withMaxAttempts(2).withDelay(250).withThreshold(92)
                    .withArea(AreaData.of(525,804,710,926)).build());
        if (matched.isFound())return matched;
        var first=GreenLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)),
                EXPLORATION_CLAIM_REGION);
        if (!first.isFound())return ImageSearchResultData.miss();
        sleepTask(250);
        var second=GreenLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)),
                EXPLORATION_CLAIM_REGION);
        return RecognitionConsensus.agrees(first,second,8) ? second : ImageSearchResultData.miss();
    }

    private ImageSearchResultData locateDisabledClaimButton() {
        if (emuManager.getSelectedEmulatorType()
                == dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU)
            return ExplorationChestActionFrame.disabled(
                    emuManager.captureScreen(EMULATOR_NUMBER))
                    ? ImageSearchResultData.hit(622,865,90)
                    : ImageSearchResultData.miss();
        return templateSearchHelper.locatePattern(
                TemplatesEnum.EXPLORATION_CLAIM_DISABLED,
                SearchConfigConstants.SINGLE_WITH_2_RETRIES);
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
        // Detect the current Idle Income reward sheet in a single fresh frame.
        // Previous code spent seconds probing four unrelated banner templates
        // on each check even though this task has a dedicated visual detector.
        RawImageData frame=emuManager.captureScreen(EMULATOR_NUMBER);
        if (ExplorationIncomeRewardsFrame.visible(frame)) return true;
        if (ExplorationPageFrame.title(frame) || idleIncomeTitleOnFrame(frame)
                || ExplorationRootFrame.root(frame)!=ExplorationRootFrame.Root.UNKNOWN)
            return false;
        // Compatibility fallback for older reward artwork on an unknown page.
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

    private boolean dismissRewardPopup() {
        // The Rewards panel says "Tap anywhere to exit". Confirm it exists
        // on two fresh frames, click once and verify it has disappeared.
        if (!rewardsOverlayVisible()) return false;
        sleepTask(200);
        if (!rewardsOverlayVisible()) return false;
        tapInside(REWARD_POPUP_TOP_LEFT, REWARD_POPUP_BOTTOM_RIGHT);
        sleepTask(750);
        return !rewardsOverlayVisible();
    }

    private void rescheduleFromConfiguredOffset(ExplorationClaimOutcome outcome) {
        Integer minutes = profile.getConfig(ConfigurationKeyEnum.INT_EXPLORATION_CHEST_OFFSET, Integer.class);
        LocalDateTime nextSchedule = ExplorationSchedulePolicy.nextRun(LocalDateTime.now(),
                minutes == null ? 120 : minutes, UNKNOWN_RETRY_MINUTES, outcome);
        reschedule(nextSchedule);
        logInfo(routineLogExplorationLine("Next execution scheduled at: "
                + nextSchedule.format(DATETIME_FORMATTER) + "; outcome=" + outcome));
    }

    enum ExplorationClaimOutcome {
        CLAIMED, NONE_AVAILABLE, UNKNOWN
    }
}
