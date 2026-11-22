package dev.frostguard.tasks.pets;

import dev.frostguard.vision.convert.GameTimeUtils;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.engine.nav.SidebarDestination;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.api.domain.PointData;

import java.time.LocalDateTime;

/**
 * Task that claims alliance treasures from Pet Adventure.
 * 
 * <p>
 * <b>Execution Flow:</b>
 * <ol>
 * <li>Navigate to Pets menu</li>
 * <li>Open Beast Cage</li>
 * <li>Open Alliance Treasure Map screen</li>
 * <li>Open Ally Treasure screen</li>
 * <li>Claim available treasures</li>
 * </ol>
 * 
 * <p>
 * <b>Scheduling:</b>
 * <ul>
 * <li>Success: Reschedules to next game reset</li>
 * <li>No treasure available: Reschedules to next game reset</li>
 * <li>Navigation failure: Retries in 5 minutes</li>
 * </ul>
 */
public class PetAllianceTreasuresRoutine extends DelayedTask {

	// ========================================================================
	// NAVIGATION CONSTANTS
	// ========================================================================

	/**
	 * Delay in minutes before retrying after navigation failure.
	 */
	private static final int RETRY_DELAY_MINUTES = 5;

	// ========================================================================
	// CONSTRUCTOR
	// ========================================================================

	/**
	 * Constructs a new PetAllianceTreasuresRoutine.
	 * 
	 * @param profile     The profile this task will execute for
	 * @param tpDailyTask The task type enum
	 */
	public PetAllianceTreasuresRoutine(AccountDescriptor profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	// ========================================================================
	// TASK CONFIGURATION
	// ========================================================================

	/**
	 * This task can start from any screen location.
	 * 
	 * @return ANY - works from both home and world screens
	 */
	@Override
    public LaunchPoint getRequiredStartLocation() {
        // The Daily sidebar requires a verified City/World root; ANY allows
        // an unknown modal and triggered real navigation failures on Nov 20.
        return LaunchPoint.HOME;
    }

	// ========================================================================
	// MAIN EXECUTION
	// ========================================================================

	/**
	 * Executes the alliance treasure claiming process.
	 * 
	 * <p>
	 * Navigates through Pets â†’ Beast Cage â†’ Alliance Treasures and attempts
	 * to claim available rewards. Reschedules appropriately based on outcome.
	 */
	@Override
	protected void execute() {

        if (!navigateToPetAdventure()) {
            markWorkIncomplete("Pet Alliance Treasure sidebar navigation failed");
            rescheduleForRetry();
            return;
        }

        if (!openAllyTreasureScreen()) {
            markWorkIncomplete("Ally Treasure panel not opened");
            rescheduleForRetry();
            return;
        }
        try {
            claimTreasureIfAvailable();
        } finally {
            // The Claim All action leaves an Ally Treasure modal open; on 20 Nov
            // that blocked Chief Order and Initialize for minutes. Close only
            // the positively identified panel X, never blind Back from unknown UI.
            if (!closeVerifiedAllyTreasurePanel()) {
                markWorkIncomplete("Ally Treasure panel could not be safely closed");
                rescheduleForRetry();
            }
        }
    }

    private boolean closeVerifiedAllyTreasurePanel() {
        var first = PetAllyTreasureExitFrame.verifiedClose(
                emuManager.captureScreen(EMULATOR_NUMBER));
        if (!first.isFound()) {
            logWarning("Ally Treasure close not positively identified; no blind tap");
            return false;
        }
        sleepTask(180);
        var confirm = PetAllyTreasureExitFrame.verifiedClose(
                emuManager.captureScreen(EMULATOR_NUMBER));
        if (!RecognitionConsensus.agrees(first, confirm, 12)) {
            logWarning("Ally Treasure X moved between screenshots; refusing tap");
            return false;
        }
        tapInside(confirm);
        sleepTask(350);
        boolean gone = !PetAllyTreasureExitFrame.verifiedClose(
                emuManager.captureScreen(EMULATOR_NUMBER)).isFound();
        if (gone) logInfo("Ally Treasure modal closed using verified X; Pet Adventure map restored");
        else logWarning("Ally Treasure modal X remained after close; marked incomplete");
        return gone;
    }

	// ========================================================================
	// NAVIGATION METHODS
	// ========================================================================

	/**
	 * Navigates from current screen to the Beast Cage menu.
	 * 
	 * <p>
	 * <b>Steps:</b>
	 * <ol>
	 * <li>Search for Pets button (with retries for reliability)</li>
	 * <li>Tap to open Pets menu</li>
	 * <li>Search for Beast Cage button</li>
	 * <li>Tap to open Beast Cage</li>
	 * </ol>
	 * 
	 * @return true if navigation succeeded, false if any step failed
	 */
	private boolean navigateToPetAdventure() {
		logDebug("Opening Pet Adventure through the Daily sidebar");
		return navigationHelper.navigateToSidebarDestination(SidebarDestination.PET_ADVENTURE);
	}

	/**
	 * Opens the alliance treasure screens within Beast Cage.
	 * 
	 * <p>
	 * <b>Navigation sequence:</b>
	 * <ol>
	 * <li>Tap Alliance Treasure Map button (opens treasure map view)</li>
	 * <li>Tap Ally Treasure button (opens claim screen)</li>
	 * </ol>
	 * 
	 * <p>
	 * This method assumes Pet Adventure is open.
	 */
	private boolean openAllyTreasureScreen() {
		ImageSearchResultData allyTreasure = templateSearchHelper.locatePattern(
				TemplatesEnum.PETS_ALLY_TREASURE,
				SearchConfigConstants.SINGLE_WITH_RETRIES);
		if (!allyTreasure.isFound()) {
			logWarning("Ally Treasure control not found on Pet Adventure screen");
			return false;
		}

		logDebug("Opening Ally Treasure screen");
		tapInside(allyTreasure);
		sleepTask(1000);
		return true;
	}

	// ========================================================================
	// CLAIM LOGIC
	// ========================================================================

	/**
	 * Attempts to claim alliance treasure if the claim button is available.
	 * 
	 * <p>
	 * <b>Behavior:</b>
	 * <ul>
	 * <li>If claim button found: Claims treasure, reschedules to next game
	 * reset</li>
	 * <li>If claim button not found: Already claimed, reschedules to next game
	 * reset</li>
	 * </ul>
	 * 
	 * <p>
	 * Always navigates back to previous screen after checking, regardless of
	 * outcome.
	 */
    static boolean remainingClaimsExhausted(String text) {
        return text != null && text.replaceAll("\\s+", " ")
                .matches("(?is).*remaining\\s*claims\\s*today\\s*:\\s*[0o]\\b.*");
    }

    private ImageSearchResultData detectClaimAll() {
        // Serve-EMU 720x1280: Claim All is at the bottom of the Ally Treasure
        // modal, separate from the individual Claim rows. Verify both the
        // button colour and its label before dispatching a single tap.
        String label = readStringValue(new PointData(205, 1010), new PointData(520, 1140), null);
        if (label == null || !label.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z]", "").contains("claimall")) {
            return ImageSearchResultData.miss();
        }
        var region = new GreenLabelActionDetector.Region(210, 1010, 515, 1145);
        var first = GreenLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)), region);
        if (!first.isFound()) return ImageSearchResultData.miss();
        sleepTask(150);
        var second = GreenLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)), region);
        return RecognitionConsensus.agrees(first, second, 15) ? second : ImageSearchResultData.miss();
    }

    private void claimTreasureIfAvailable() {
        // The game keeps green individual Claim buttons visible even after
        // Remaining claims today reaches zero. Never click them in that state.
        String quota = readStringValue(new PointData(180, 265), new PointData(530, 335), null);
        if (remainingClaimsExhausted(quota)) {
            logInfo("Ally Treasure daily claim quota exhausted; skipping still-visible Claim rows");
            rescheduleToGameReset("Ally Treasure remaining claims today 0");
            return;
        }
        ImageSearchResultData claimAll = detectClaimAll();
        if (claimAll.isFound()) {
            logInfo("Verified Ally Treasure Claim All label and green button; tapping once");
            tapInside(claimAll);
            sleepTask(550);
            // The game opens a Rewards overlay. Dismiss it once, then
            // confirm that daily claims were consumed before daily reset.
            tapNear(new PointData(355, 1175));
            sleepTask(350);
            String remaining = readStringValue(new PointData(180, 265), new PointData(530, 335), null);
            if (remaining != null && remainingClaimsExhausted(remaining)) {
                rescheduleToGameReset("Ally Treasure Claim All confirmed: remaining claims today 0");
            } else {
                markWorkIncomplete("Ally Treasure Claim All: remaining claims not verified after Rewards overlay");
                rescheduleForRetry();
            }
            return;
        }
        ImageSearchResultData claimButton = templateSearchHelper.locatePattern(
				TemplatesEnum.PETS_BEAST_ALLIANCE_CLAIM,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (claimButton.isFound()) {
			logInfo("Claim button found - claiming alliance treasure");
            tapInside(claimButton);
            sleepTask(500);
            // A dispatched click is not a successful claim. If the exact
            // Claim button remains, retry later rather than hiding the miss
            // until tomorrow's reset.
            if (templateSearchHelper.locatePattern(
                    TemplatesEnum.PETS_BEAST_ALLIANCE_CLAIM,
                    SearchConfigConstants.DEFAULT_SINGLE).isFound()) {
                markWorkIncomplete("Pet Alliance Treasure: Claim button remained after tap");
                rescheduleForRetry();
                return;
            }
            rescheduleToGameReset("Alliance treasure Claim disappeared after tap");
        } else {
            // A missing Claim template is not evidence of exhausted daily
            // quota. Only an OCR-confirmed zero may defer until reset.
            markWorkIncomplete("Ally Treasure: no claim control and daily quota not confirmed exhausted");
            logWarning("No claim control visible and quota is not confirmed zero; retrying safely");
            rescheduleForRetry();
        }
	}

	// ========================================================================
	// RESCHEDULING METHODS
	// ========================================================================

	/**
	 * Reschedules the task to the next game reset (00:00 UTC).
	 * 
	 * <p>
	 * Used when treasure is successfully claimed or when no treasure
	 * is available (indicating it was already claimed today).
	 * 
	 * @param reason Descriptive reason for logging purposes
	 */
	private void rescheduleToGameReset(String reason) {
		LocalDateTime nextReset = GameTimeUtils.dailyResetTime();
		reschedule(nextReset);
		logInfo(reason + ". Rescheduling to next game reset: " +
				nextReset.format(DATETIME_FORMATTER));
	}

	/**
	 * Reschedules the task to retry in 5 minutes.
	 * 
	 * <p>
	 * Used when navigation fails (Pets button or Beast Cage not found).
	 * This handles temporary UI issues or feature unavailability.
	 */
	private void rescheduleForRetry() {
		LocalDateTime retryTime = LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES);
		reschedule(retryTime);
		logWarning("Navigation failed. Retrying at " + retryTime.format(TIME_FORMATTER));
	}
}
