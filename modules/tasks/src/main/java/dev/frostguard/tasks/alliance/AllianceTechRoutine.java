package dev.frostguard.tasks.alliance;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.engine.helper.NavigationHelper;
import dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.engine.schedule.TaskQueue;
import java.time.LocalDateTime;

public class AllianceTechRoutine extends DelayedTask {

private static final AreaData DONATION_BUTTON_AREA_VALUE = new AreaData(
			new PointData(450, 1000),
			new PointData(580, 1050));

private static final AreaData POPUP_CLOSE_AREA_VALUE = new AreaData(
			new PointData(270, 30),
			new PointData(280, 80));

private static final AreaData ALLIANCE_COINS_BUTTON_AREA_VALUE = new AreaData(
			new PointData(580, 30),
			new PointData(670, 50));

// Live Tech HUD: coin balance is at the upper right, not the old
// central 2024 dialog position (272..443, 257..285).
private static final AreaData COIN_COUNT_OCR_AREA_VALUE = AreaData.of(585,14,677,59);
private static final AreaData FREE_ATTEMPTS_OCR_AREA = AreaData.of(389,929,655,986);
private static final OcrSettingsData FREE_ATTEMPTS_SETTINGS = OcrSettingsData.configurator()
        .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
        .allowedGlyphs("0123456789/").build();

private static final PointData TREE_RESET_SWIPE_START_VALUE = new PointData(360, 600);

private static final PointData TREE_RESET_SWIPE_END_VALUE = new PointData(360, 984);

private static final PointData TREE_SCROLL_SWIPE_START_VALUE = new PointData(360, 900);

private static final PointData TREE_SCROLL_SWIPE_END_VALUE = new PointData(360, 540);

private static final int TREE_RESET_SWIPE_COUNT_VALUE = 5;

private static final int TREE_SCROLL_ATTEMPT_COUNT_VALUE = 5;

private static final long TREE_SCROLL_SETTLE_MILLIS_VALUE = 500;

private static final int DONATION_TAP_COUNT_VALUE = 25;

private static final int MIN_OFFSET_MINUTES_FLOOR = 10;

private static final int DEFAULT_OFFSET_MINUTES_VALUE = Integer.parseInt(
			ConfigurationKeyEnum.ALLIANCE_TECH_OFFSET_INT.getDefaultValue());

private static final int ERROR_RETRY_MINUTES_VALUE = 10;

private static final OcrSettingsData COIN_COUNT_OCR_SETTINGS_VALUE = OcrSettingsData.assembler()
			.charWhitelist("0123456789")
			.textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
			.build();

private int offsetMinutes;

private boolean donationsSuccessful;

private enum DonationStatus { CONFIRMED, NONE_AVAILABLE, UNVERIFIED }

public AllianceTechRoutine(AccountDescriptor profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

@Override
	protected void execute() {

		donationsSuccessful = false;
		hydrateConfiguration();

		if (!reachTechMenu()) {
			manageTaskFailure("Failed to navigate to Alliance Tech menu");
			return;
		}

        boolean preempted = false;
        try {
            if (!locateDonationButton()) {
                manageTaskFailure("Thumbs-up donation button not found");
                return;
            }

            DonationStatus outcome=performDonationsFlow();
            if (outcome==DonationStatus.UNVERIFIED) {
                markWorkIncomplete("Alliance Tech: free contribution not confirmed; no repeated blind taps");
                logWarning("Alliance Tech free donation not confirmed; deferring without blind retries");
                reschedule(LocalDateTime.now().plusMinutes(ERROR_RETRY_MINUTES_VALUE));
                return;
            }
            donationsSuccessful=outcome==DonationStatus.CONFIRMED;
            if (outcome==DonationStatus.NONE_AVAILABLE)
                logInfo("Alliance Tech free contribution attempts exhausted; waiting for refill");

            inspectAndTriggerAllianceShop();

            queueNextRun();
        } catch (dev.frostguard.engine.error.TaskPreemptedException
                 | dev.frostguard.engine.error.StopExecutionException control) {
            preempted = true;
            throw control;
        } finally {
            if (!preempted && !Thread.currentThread().isInterrupted()) {
                returnToHomeAfterAllianceContributions();
            }
        }
	}

@Override
	public boolean provideDailyMissionProgress() {
		return donationsSuccessful;
	}

private void returnToHomeAfterAllianceContributions() {
        // No OCR of a title that has a proven 2026 visual template. The
        // pop-up close X is (639,198); old blind Back and (270,50) were wrong.
        try {
            var first=emuManager.captureScreen(EMULATOR_NUMBER);
            if (AllianceContributionFrame.popup(first)) {
                logInfo("Verified Alliance contribution dialog; closing its header X once");
                tapInside(AreaData.of(626,183,653,213));
                sleepTask(350);
                if (AllianceContributionFrame.popup(emuManager.captureScreen(EMULATOR_NUMBER))) {
                    markWorkIncomplete("Alliance Tech: contribution dialog did not close");
                    logWarning("Alliance contribution dialog remained after verified close");
                    return;
                }
            }
            var scoped=AlliancePageExit.returnHome(
                    ()->{checkPreemption();return emuManager.captureScreen(EMULATOR_NUMBER);},
                    point->{checkPreemption();tapNear(point);},
                    ()->sleepTask(350));
            if(scoped==AlliancePageExit.Result.HOME){
                logInfo("Alliance Tech -> Alliance -> verified City via direct headers");
                return;
            }
            if(scoped==AlliancePageExit.Result.UNVERIFIED){
                markWorkIncomplete("Alliance Tech: direct return Home failed");
                logWarning("Alliance Tech page changed during return; no blind Back");
                return;
            }
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME,this::checkPreemption);
        } catch(dev.frostguard.engine.error.HomeNotFoundException missing) {
            markWorkIncomplete("Alliance Tech: City Home not verified");
            logWarning("Alliance Tech return Home failed: "+missing.getMessage());
        }
    }

private void inspectAndTriggerAllianceShop() {
        boolean shopEnabled = Boolean.TRUE.equals(profile.getConfig(
                ConfigurationKeyEnum.ALLIANCE_SHOP_ENABLED_BOOL, Boolean.class));

		if (!shopEnabled) {
			logDebug(routineLogAllianceTechLine("Alliance Shop is disabled. Skipping shop check."));
			return;
		}

		logInfo(routineLogAllianceTechLine("Alliance Shop enabled. Inspecting current coins."));

		reachCoinsDisplay();

		Integer currentCoins = readNumberValue(COIN_COUNT_OCR_AREA_VALUE.topLeft(), COIN_COUNT_OCR_AREA_VALUE.bottomRight(),
				COIN_COUNT_OCR_SETTINGS_VALUE);

		if (currentCoins == null) {
			logWarning(routineLogAllianceTechLine("Could not read current alliance coins. Skipping shop trigger."));
			return;
		}

		Integer minCoins = profile.getConfig(
				ConfigurationKeyEnum.ALLIANCE_SHOP_MIN_COINS_TO_ACTIVATE_INT,
				Integer.class);

		logInfo(routineLogAllianceTechLine("Current alliance coins: " + currentCoins + ", Minimum required: " + minCoins));

        if (minCoins != null && currentCoins > minCoins) {
			triggerAllianceShopRoutineFlow();
		} else {
			logInfo(routineLogAllianceTechLine("Insufficient coins to trigger Alliance Shop task"));
		}
	}

private void triggerAllianceShopRoutineFlow() {
		TaskQueue queue = dev.frostguard.engine.service.ScheduleService.obtain().getCoordinator().getQueue(profile.getId());

		if (queue == null) {
			logError(routineLogAllianceTechLine("Could not retrieve task queue for profile. Cannot trigger shop task."));
			return;
		}

		logInfo(routineLogAllianceTechLine("Triggering Alliance Shop task to execute immediately"));
		queue.runNow(TpDailyTaskEnum.ALLIANCE_SHOP, true);
	}

private String routineLogAllianceTechLine(String note) {
        return "AllianceTechRoutine | " + note;
    }

private DonationStatus performDonationsFlow() {
        if (emuManager.getSelectedEmulatorType()
                != dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU) {
            // Preserve the legacy non-Serve-EMU route until live visual
            // evidence is available for those entirely different emulators.
            tapInside(DONATION_BUTTON_AREA_VALUE.topLeft(),
                    DONATION_BUTTON_AREA_VALUE.bottomRight(),DONATION_TAP_COUNT_VALUE,150);
            return DonationStatus.CONFIRMED;
        }
        RawImageData current=emuManager.captureScreen(EMULATOR_NUMBER);
        if (!AllianceContributionFrame.popup(current)){
            logWarning("Alliance Tech resource donation dialog not verified");
            return DonationStatus.UNVERIFIED;
        }
        int available=readFreeAttemptCount();
        if(available<0){
            logWarning("Cannot read Alliance free contribution attempts; no button tapped");
            return DonationStatus.UNVERIFIED;
        }
        if(available==0)return DonationStatus.NONE_AVAILABLE;
        int completed=0;
        while(available>0 && completed<DONATION_TAP_COUNT_VALUE){
            checkPreemption();
            current=emuManager.captureScreen(EMULATOR_NUMBER);
            var free=AllianceContributionFrame.freeResourceButton(current);
            if(!free.isFound()){
                logWarning("Alliance blue resource CTA disappeared; never tap orange 10-gem contribution");
                return DonationStatus.UNVERIFIED;
            }
            // Exactly ONE resource donation at a time. Previous 25 taps at
            // 150 ms were faster than the game could process and silently
            // dropped actions while still reporting task success.
            tapNear(free.getPoint());
            int after=-1;
            for(int observed=0;observed<4;observed++){
                sleepTask(350);
                after=readFreeAttemptCount();
                if(after==available-1)break;
            }
            if(after!=available-1){
                logWarning("Alliance donation unverified: attempts expected "
                        +(available-1)+" but read "+after+"; no duplicate tap");
                return DonationStatus.UNVERIFIED;
            }
            completed++;
            available=after;
            if(completed==1 || completed%5==0 || available==0)
                logInfo("Alliance resource donations confirmed: "+completed
                        +"; free attempts remaining "+available);
        }
        return completed>0 ? DonationStatus.CONFIRMED : DonationStatus.NONE_AVAILABLE;
    }

    static int parseFreeAttemptCount(String raw){
        if(raw==null)return -1;
        var matcher=java.util.regex.Pattern.compile("(\\d{1,2})\\s*/\\s*25")
                .matcher(raw.replace('O','0'));
        if(!matcher.find())return -1;
        int count=Integer.parseInt(matcher.group(1));
        return count>=0 && count<=25 ? count : -1;
    }

    private int readFreeAttemptCount(){
        try{
            String raw=emuManager.readText(EMULATOR_NUMBER,
                    FREE_ATTEMPTS_OCR_AREA.topLeft(),FREE_ATTEMPTS_OCR_AREA.bottomRight(),
                    FREE_ATTEMPTS_SETTINGS,false);
            return parseFreeAttemptCount(raw);
        }catch(java.io.IOException | dev.frostguard.vision.ocr.OcrException failure){
            if(Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Alliance attempt OCR interrupted");
            logDebug("Alliance free attempt OCR unavailable: "+failure.getClass().getSimpleName());
            return -1;
        }
    }

private boolean locateDonationButton() {
		logDebug(routineLogAllianceTechLine("Scanning for thumbs-up donation button"));

		ImageSearchResultData thumbsUpResult = findThumbsUp(SearchConfigConstants.SINGLE_WITH_RETRIES);

		if (!thumbsUpResult.isFound()) {
			logInfo(routineLogAllianceTechLine(
					"Thumbs-up marker is not visible at the opened position. Searching all Tech trees."));
			thumbsUpResult = searchTechTrees();
		}

		if (thumbsUpResult.isFound()) {
			logInfo(routineLogAllianceTechLine("Thumbs-up donation button detected. Proceeding with donations."));
			tapInside(thumbsUpResult);
			sleepTask(500);

			return true;
		}

		logWarning(routineLogAllianceTechLine("Thumbs-up donation button not detected after scanning all Tech trees"));
		return false;
	}

private ImageSearchResultData searchTechTrees() {
		ImageSearchResultData lastResult = null;
		for (TechCategory category : TechCategory.values()) {
			checkPreemption();
			selectTechCategory(category);
			lastResult = findThumbsUp(SearchConfigConstants.QUICK_SEARCH);
			if (lastResult.isFound()) {
				logInfo(routineLogAllianceTechLine("Found the thumbs-up marker at the remembered '"
						+ category.label + "' tree position."));
				return lastResult;
			}

			logInfo(routineLogAllianceTechLine("Searching the '" + category.label + "' Tech tree."));
			resetTreeToTop();

			for (int viewport = 0; viewport <= TREE_SCROLL_ATTEMPT_COUNT_VALUE; viewport++) {
				checkPreemption();
				lastResult = findThumbsUp(SearchConfigConstants.QUICK_SEARCH);
				if (lastResult.isFound()) {
					logInfo(routineLogAllianceTechLine("Found the thumbs-up marker in '"
							+ category.label + "' at viewport " + (viewport + 1) + "."));
					return lastResult;
				}

				if (viewport < TREE_SCROLL_ATTEMPT_COUNT_VALUE) {
					swipe(TREE_SCROLL_SWIPE_START_VALUE, TREE_SCROLL_SWIPE_END_VALUE);
					sleepTask(TREE_SCROLL_SETTLE_MILLIS_VALUE);
				}
			}

			logDebug(routineLogAllianceTechLine(
					"No thumbs-up marker found in the bounded '" + category.label + "' tree scan."));
		}
		return lastResult;
	}

private void selectTechCategory(TechCategory category) {
		tapInside(category.tabArea.topLeft(), category.tabArea.bottomRight());
		sleepTask(TREE_SCROLL_SETTLE_MILLIS_VALUE);
	}

private void resetTreeToTop() {
		for (int swipeAttempt = 0; swipeAttempt < TREE_RESET_SWIPE_COUNT_VALUE; swipeAttempt++) {
			checkPreemption();
			swipe(TREE_RESET_SWIPE_START_VALUE, TREE_RESET_SWIPE_END_VALUE);
			sleepTask(TREE_SCROLL_SETTLE_MILLIS_VALUE);
		}
	}

private ImageSearchResultData findThumbsUp(SearchConfig searchConfig) {
		ImageSearchResultData current = templateSearchHelper.locatePattern(
				TemplatesEnum.ALLIANCE_TECH_THUMB_UP_CURRENT,
				searchConfig);
		return current.isFound() ? current : templateSearchHelper.locatePattern(
				TemplatesEnum.ALLIANCE_TECH_THUMB_UP,
				searchConfig);
	}

private enum TechCategory {
		GROWTH("Growth", new AreaData(new PointData(22, 236), new PointData(244, 316))),
		TERRITORY("Territory", new AreaData(new PointData(251, 236), new PointData(470, 316))),
		BATTLE("Battle", new AreaData(new PointData(477, 236), new PointData(698, 316)));

		private final String label;
		private final AreaData tabArea;

		TechCategory(String label, AreaData tabArea) {
			this.label = label;
			this.tabArea = tabArea;
		}
	}

private void hydrateConfiguration() {
        Integer rawOffset = profile.getConfig(
                ConfigurationKeyEnum.ALLIANCE_TECH_OFFSET_INT, Integer.class);

		if (rawOffset == null || rawOffset < MIN_OFFSET_MINUTES_FLOOR) {
			logWarning(routineLogAllianceTechLine("Invalid offset configured: " + rawOffset +
					" minutes. Must be at least " + MIN_OFFSET_MINUTES_FLOOR +
					". Using default: " + DEFAULT_OFFSET_MINUTES_VALUE));
			offsetMinutes = DEFAULT_OFFSET_MINUTES_VALUE;
		} else {
			offsetMinutes = rawOffset;
		}

		logInfo(routineLogAllianceTechLine("Configuration loaded - Offset: " + offsetMinutes + " minutes"));
	}

private void reachCoinsDisplay() {
        // The 2026 Tech page already shows alliance coins in the upper-right
        // HUD. The old routine tapped the donation dialog at (270,50) three
        // times, then clicked the plus/store area; both actions are obsolete
        // and can open an unrelated purchase screen. Close only the known
        // contribution modal and read the existing HUD, with zero store taps.
        if(AllianceContributionFrame.popup(emuManager.captureScreen(EMULATOR_NUMBER))){
            tapInside(AreaData.of(626,183,653,213));
            sleepTask(300);
        }
    }

private void manageTaskFailure(String reason) {
        markWorkIncomplete("Alliance Tech: " + reason);
		logWarning(routineLogAllianceTechLine("Routine pass did not complete: " + reason));

		LocalDateTime retryTime = LocalDateTime.now().plusMinutes(ERROR_RETRY_MINUTES_VALUE);
		reschedule(retryTime);

		logInfo(routineLogAllianceTechLine("Task rescheduled to retry in " + ERROR_RETRY_MINUTES_VALUE + " minutes"));
	}

private boolean reachTechMenu() {
		logDebug(routineLogAllianceTechLine("Moving to Alliance Tech menu"));

		boolean success = navigationHelper.navigateToAllianceMenu(NavigationHelper.AllianceMenu.TECH);

		if (success) {
			logDebug(routineLogAllianceTechLine("Successfully navigated to Alliance Tech menu"));
		} else {
			logError(routineLogAllianceTechLine("Could not navigate to Alliance Tech menu"));
		}

		return success;
	}

private void queueNextRun() {
		LocalDateTime nextExecutionTime = LocalDateTime.now().plusMinutes(offsetMinutes);
		reschedule(nextExecutionTime);

		logInfo(routineLogAllianceTechLine("Alliance Tech task finished cleanly. Next execution in " +
				offsetMinutes + " minutes"));
	}
}
