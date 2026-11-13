package dev.frostguard.tasks.dailies;

import java.time.LocalDateTime;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.engine.helper.BlueLabelActionDetector;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;

public class ChiefOrderRoutine extends DelayedTask {

public enum ChiefOrderType {

		RUSH_JOB("Rush Job", TemplatesEnum.CHIEF_ORDER_RUSH_JOB, 24),


		URGENT_MOBILIZATION("Urgent Mobilization", TemplatesEnum.CHIEF_ORDER_URGENT_MOBILISATION, 8),


		PRODUCTIVITY_DAY("Productivity Day", TemplatesEnum.CHIEF_ORDER_PRODUCTIVITY_DAY, 12);

		private final String description;
		private final TemplatesEnum template;
		private final int cooldownHours;

		ChiefOrderType(String description, TemplatesEnum template, int cooldownHours) {
			this.description = description;
			this.template = template;
			this.cooldownHours = cooldownHours;
		}

		public String getDescription() {
			return description;
		}

		public TemplatesEnum getTemplate() {
			return template;
		}

		public int getCooldownHours() {
			return cooldownHours;
		}
	}

private static final int ERROR_RETRY_MINUTES_VALUE = 10;

private final ChiefOrderType chiefOrderType;

public ChiefOrderRoutine(AccountDescriptor profile, TpDailyTaskEnum tpTask, ChiefOrderType chiefOrderType) {
		super(profile, tpTask);
		this.chiefOrderType = chiefOrderType;
	}

@Override
	public LaunchPoint getRequiredStartLocation() {
		return LaunchPoint.HOME;
	}

@Override
	protected void execute() {
		logInfo(routineLogChiefOrderLine("Initiating Chief Order : " + chiefOrderType.getDescription() +
				" (Cooldown: " + chiefOrderType.getCooldownHours() + " hours)"));

		if (!openUpChiefOrderMenu()) {
			manageTaskFailure("Failed to open Chief Order menu");
			return;
		}

		if (!chooseOrderType()) {
			manageTaskFailure("Order type not available (likely on cooldown)");
			return;
		}

		if (!enactOrderFlow()) {
			manageTaskFailure("Failed to enact order");
			return;
		}

	}

private String routineLogChiefOrderLine(String note) {
        return "ChiefOrderRoutine | " + note;
    }

private boolean openUpChiefOrderMenu() {
		logInfo(routineLogChiefOrderLine("Looking for Chief Order menu access button"));

		ImageSearchResultData menuButton = templateSearchHelper.locatePattern(
				TemplatesEnum.CHIEF_ORDER_MENU_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (!menuButton.isFound()) {
			logError(routineLogChiefOrderLine("Chief Order menu button not detected"));
			return false;
		}

		logInfo(routineLogChiefOrderLine("Chief Order menu button detected. Pressing to open menu"));
		tapInside(menuButton);
		sleepTask(2000);


		return true;
	}

private void manageTaskFailure(String reason) {
		logWarning(routineLogChiefOrderLine("Routine pass did not complete: " + reason));

		LocalDateTime retryTime = LocalDateTime.now().plusMinutes(ERROR_RETRY_MINUTES_VALUE);
		reschedule(retryTime);

		logInfo(routineLogChiefOrderLine("Task rescheduled to retry in " + ERROR_RETRY_MINUTES_VALUE + " minutes"));
	}

private boolean chooseOrderType() {
		sleepTask(1500);


		logInfo(routineLogChiefOrderLine("Scanning for Chief Order type: " + chiefOrderType.getDescription()));

		ImageSearchResultData orderButton = templateSearchHelper.locatePattern(
				chiefOrderType.getTemplate(),
				SearchConfigConstants.DEFAULT_SINGLE);

		if (!orderButton.isFound()) {
			logWarning(routineLogChiefOrderLine(chiefOrderType.getDescription() +
					" button not detected or currently on cooldown"));
			return false;
		}

		logInfo(routineLogChiefOrderLine(chiefOrderType.getDescription() + " button detected. Pressing to activate"));
		tapInside(orderButton);
		sleepTask(1500);


		return true;
	}

private boolean enactOrderFlow() {
		sleepTask(1500);


		logInfo(routineLogChiefOrderLine("Scanning for Chief Order Enact button"));

		ImageSearchResultData enactButton = locateEnactAction();
		if (!enactButton.isFound()) {
			String snapshot = TaskDiagnosticSnapshots.capture(
					emuManager, EMULATOR_NUMBER, "chieforder", "enact-not-found");
			logWarning(routineLogChiefOrderLine("Enact button not detected; " + snapshot));
			return false;
		}

		logInfo(routineLogChiefOrderLine("Enact action verified; pressing once"));
		tapInside(enactButton);
		sleepTask(1300);
		// Never repeat an uncertain purchase-like action. Require an independent
		// visible transition before reporting success.
		if (locateEnactAction().isFound()) {
			String snapshot = TaskDiagnosticSnapshots.capture(
					emuManager, EMULATOR_NUMBER, "chieforder", "enact-not-confirmed");
			logWarning(routineLogChiefOrderLine("Enact still visible after tap; action unconfirmed; " + snapshot));
			return false;
		}
		logInfo(routineLogChiefOrderLine(
					"Enact button disappeared after single press; awaiting next-run cooldown confirmation"));
		// A changed screen alone does not prove spending was accepted.
		// Do not issue a second enact on this execution.
		queueNextRun();
		return true;
	}

private static final BlueLabelActionDetector.Region ENACT_ACTION =
        new BlueLabelActionDetector.Region(212, 881, 508, 965, 322, 893, 406, 919);

private ImageSearchResultData locateEnactAction() {
        ImageSearchResultData result = templateSearchHelper.locateAction(
                TemplatesEnum.CHIEF_ORDER_ENACT_BUTTON,
                SearchConfigConstants.SINGLE_WITH_RETRIES);
        if (result.isFound()) return result;
        try {
            var first = BlueLabelActionDetector.detect(
                    ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)), ENACT_ACTION);
            if (!first.isFound()) return ImageSearchResultData.miss();
            sleepTask(200);
            var second = BlueLabelActionDetector.detect(
                    ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)), ENACT_ACTION);
            if (RecognitionConsensus.agrees(first, second, 8)) {
                logInfo(routineLogChiefOrderLine("Enact confirmed by two-frame blue action/label geometry"));
                return second;
            }
        } catch (RuntimeException failure) {
            if (failure instanceof java.util.concurrent.CancellationException
                    || Thread.currentThread().isInterrupted()) throw failure;
            logWarning(routineLogChiefOrderLine("Enact visual fallback unavailable: "
                    + failure.getClass().getSimpleName()));
        }
        return ImageSearchResultData.miss();
}

private void queueNextRun() {
		LocalDateTime nextExecutionTime = LocalDateTime.now()
				.plusHours(chiefOrderType.getCooldownHours());

		reschedule(nextExecutionTime);

		logInfo(routineLogChiefOrderLine("Task completed finished cleanly. Next execution in " +
				chiefOrderType.getCooldownHours() + " hours"));
	}
}
