package dev.frostguard.tasks.dailies;

import java.time.LocalDateTime;
import java.time.Duration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.engine.emulator.EmulatorType;
import dev.frostguard.engine.error.HomeNotFoundException;
import dev.frostguard.engine.error.TaskPreemptedException;
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
private boolean enactDeferred;
private boolean returnedHomeDirectly;
private Long confirmedPointBalanceFirst, confirmedPointBalanceSecond;

private enum OrderSelection { SELECTED, ALREADY_USED, UNKNOWN }
    enum OrderGate { ALREADY_USED, TRY_ICON, UNKNOWN }

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
        boolean preempted = false;
        try {
            executeChiefOrderWork();
        } catch (TaskPreemptedException | java.util.concurrent.CancellationException stop) {
            preempted = true;
            throw stop;
        } finally {
            if (!preempted && !Thread.currentThread().isInterrupted()) {
                returnHomeAfterChiefOrderIfVisible();
            }
        }
    }

    private void executeChiefOrderWork() {
        enactDeferred = false;
        returnedHomeDirectly = false;
        confirmedPointBalanceFirst = null;
        confirmedPointBalanceSecond = null;
        logInfo(routineLogChiefOrderLine("Starting " + chiefOrderType.getDescription()));

        if (!openUpChiefOrderMenu()) {
            manageTaskFailure("Chief Order menu not verified");
            return;
        }
        OrderSelection selection = chooseOrderType();
        if (selection == OrderSelection.ALREADY_USED) return;
        if (selection != OrderSelection.SELECTED) {
            manageTaskFailure("Chief Order status or preview unavailable");
            return;
        }
        if (!enactOrderFlow()) {
            if (!enactDeferred) manageTaskFailure("Enact did not produce a verified transition");
            return;
        }
        // A points-based Enact may return to the Chief Orders LIST, with the
        // exact tile glowing ACTIVE and no generic Rewards overlay at all.
        // Verify that named tile twice before treating the order as complete.
        // Never retry Enact based on a disappearing button alone.
        if (returnedHomeDirectly || confirmActiveOrderAfterEnact()) {
            queueNextRun();
            return;
        }
        if (expectsRewards(chiefOrderType) && dismissVerifiedChiefOrderReward()) {
            queueNextRun();
            return;
        }
        manageTaskFailure(chiefOrderType == ChiefOrderType.RUSH_JOB
                ? "Neither verified Active state nor Rush Job Rewards completion"
                : "Order activation not verified Active or back at City Home; no Rewards expected");
        return;
    }

    private void returnHomeAfterChiefOrderIfVisible() {
        if (emuManager.getSelectedEmulatorType() == EmulatorType.SERVE_EMU) {
            var result = ChiefOrderExit.returnHome(
                    () -> {checkPreemption(); return emuManager.captureScreen(EMULATOR_NUMBER);},
                    point -> {checkPreemption(); tapNear(point);},
                    () -> sleepTask(260), chiefOrderType.getDescription());
            if (result == ChiefOrderExit.Result.HOME) {
                logInfo(routineLogChiefOrderLine("City Home verified after Chief Order; no global search"));
                return;
            }
            logWarning(routineLogChiefOrderLine("Scoped Chief Order exit " + result
                    + "; falling back to Home recovery"));
        }
        try {
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME, this::checkPreemption);
            logInfo(routineLogChiefOrderLine("Home verified after Chief Order"));
        } catch (HomeNotFoundException missing) {
            markWorkIncomplete("Chief Order: Home not verified");
            logWarning(routineLogChiefOrderLine("Failed to verify Home: " + missing.getMessage()));
        }
    }

    private boolean confirmActiveOrderAfterEnact() {
        var first=emuManager.captureScreen(EMULATOR_NUMBER);
        if (!ChiefOrderLiveFrame.isActiveForOrder(
                first, chiefOrderType.getDescription())) return false;
        sleepTask(240);
        var second=emuManager.captureScreen(EMULATOR_NUMBER);
        if (!ChiefOrderLiveFrame.isActiveForOrder(
                second, chiefOrderType.getDescription())) {
            logWarning(routineLogChiefOrderLine("Post-Enact Active status unstable; "
                    + "no second Enact"));
            return false;
        }
        logInfo(routineLogChiefOrderLine(chiefOrderType.getDescription()
                + " verified ACTIVE on two Chief Order list frames; "
                + "no Rewards screen required"));
        return true;
    }

    static boolean expectsRewards(ChiefOrderType type) {
        return type == ChiefOrderType.RUSH_JOB;
    }

    static boolean completedWithVerifiedRewards(ChiefOrderRewardFrame.Exit exit) {
        return exit == ChiefOrderRewardFrame.Exit.DISMISSED;
    }

    private boolean dismissVerifiedChiefOrderReward() {
        ChiefOrderRewardFrame.Exit result=ChiefOrderRewardFrame.dismissIfVisible(
                () -> {checkPreemption();return emuManager.captureScreen(EMULATOR_NUMBER);},
                target -> {checkPreemption();tapNear(target);},
                () -> sleepTask(430));
        if (completedWithVerifiedRewards(result)) {
            logInfo(routineLogChiefOrderLine("Verified Rewards dismissed in one safe tap; "
                    + "no unrelated global recovery scan needed"));
            return true;
        }
        logWarning(routineLogChiefOrderLine("Chief Order Rewards were " + result
                + "; outcome unknown; no duplicate tap"));
        return false;
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
        sleepTask(420);
        if (emuManager.getSelectedEmulatorType() == EmulatorType.SERVE_EMU
                && !ChiefOrderLiveFrame.isChiefOrderList(
                        emuManager.captureScreen(EMULATOR_NUMBER))) {
            logWarning(routineLogChiefOrderLine("Menu button tapped but actual Chief Order list is missing"));
            return false;
        }
        return true;
	}

private void manageTaskFailure(String reason) {
        markWorkIncomplete("Chief Order: " + reason);
		logWarning(routineLogChiefOrderLine("Routine pass did not complete: " + reason));

		LocalDateTime retryTime = LocalDateTime.now().plusMinutes(ERROR_RETRY_MINUTES_VALUE);
		reschedule(retryTime);

		logInfo(routineLogChiefOrderLine("Task rescheduled to retry in " + ERROR_RETRY_MINUTES_VALUE + " minutes"));
	}

private OrderSelection chooseOrderType() {
        if (emuManager.getSelectedEmulatorType() == EmulatorType.SERVE_EMU) {
            var initialFrame = emuManager.captureScreen(EMULATOR_NUMBER);
            var list = ChiefOrderLiveFrame.inspectList(initialFrame, chiefOrderType.getDescription());
            OrderGate gate = orderGate(list.state());
            sleepTask(200);
            var secondFrame = emuManager.captureScreen(EMULATOR_NUMBER);
            var second = ChiefOrderLiveFrame.inspectList(secondFrame, chiefOrderType.getDescription());
            OrderGate secondGate = orderGate(second.state());
            if (gate == OrderGate.ALREADY_USED || secondGate == OrderGate.ALREADY_USED) {
                if (gate != OrderGate.ALREADY_USED || secondGate != OrderGate.ALREADY_USED) {
                    logWarning(routineLogChiefOrderLine("Order status unstable "
                            + list.state() + " -> " + second.state() + "; no action"));
                    return OrderSelection.UNKNOWN;
                }
                Duration verifiedRemaining = confirmedCountdown(list.remaining(), second.remaining());
                LocalDateTime next = nextAvailableCheck(LocalDateTime.now(), verifiedRemaining);
                reschedule(next);
                logInfo(routineLogChiefOrderLine(chiefOrderType.getDescription() + " already used ("
                        + second.state() + ", timer="
                        + (verifiedRemaining == null ? "unverified, 30m fallback" : verifiedRemaining)
                        + "); no Enact/Claim action; next check " + next));
                return OrderSelection.ALREADY_USED;
            }
            // Tiny status text can OCR as UNKNOWN, even on an available
            // full-size tile. The distinctive ICON plus two confirmed board
            // frames and the exact order book later are sufficient to OPEN
            // a preview. This fallback never authorizes spending.
            if (!ChiefOrderLiveFrame.isChiefOrderList(initialFrame)
                    || !ChiefOrderLiveFrame.isChiefOrderList(secondFrame)
                    || !eligibleForIconSelection(gate, secondGate)) {
                logWarning(routineLogChiefOrderLine("Cannot verify order list state: "
                        + list + " -> " + second));
                return OrderSelection.UNKNOWN;
            }
            confirmedPointBalanceFirst = ChiefOrderLiveFrame.availableOrderPoints(initialFrame);
            confirmedPointBalanceSecond = ChiefOrderLiveFrame.availableOrderPoints(secondFrame);
            if (gate == OrderGate.UNKNOWN || secondGate == OrderGate.UNKNOWN) {
                logInfo(routineLogChiefOrderLine("Order status OCR uncertain; "
                        + "opening only the verified unique tile for exact book confirmation"));
            }
        }

        ImageSearchResultData orderButton = templateSearchHelper.locatePattern(
                chiefOrderType.getTemplate(), SearchConfigConstants.DEFAULT_SINGLE);
        if (!orderButton.isFound()) {
            logWarning(routineLogChiefOrderLine("Available order icon not positively matched"));
            return OrderSelection.UNKNOWN;
        }
        if (emuManager.getSelectedEmulatorType() == EmulatorType.SERVE_EMU) {
            sleepTask(190);
            var reconfirm = templateSearchHelper.locatePattern(
                    chiefOrderType.getTemplate(), SearchConfigConstants.DEFAULT_SINGLE);
            if (!RecognitionConsensus.agrees(orderButton, reconfirm, 14)) {
                logWarning(routineLogChiefOrderLine("Order tile icon unstable; no preview tap"));
                return OrderSelection.UNKNOWN;
            }
        }
        tapInside(orderButton);
        sleepTask(580);
        if (emuManager.getSelectedEmulatorType() == EmulatorType.SERVE_EMU
                && !ChiefOrderLiveFrame.isOrderBook(
                        emuManager.captureScreen(EMULATOR_NUMBER),
                        chiefOrderType.getDescription())) {
            logWarning(routineLogChiefOrderLine("Wrong/unverified order book; no Enact input"));
            return OrderSelection.UNKNOWN;
        }
        return OrderSelection.SELECTED;
    }

    static boolean eligibleForIconSelection(OrderGate first, OrderGate second) {
        // UNKNOWN status may OPEN the book only after distinct icon
        // confirmation. ACTIVATED / ON_COOLDOWN never do.
        return first != OrderGate.ALREADY_USED && second != OrderGate.ALREADY_USED;
    }

    static OrderGate orderGate(ChiefOrderLiveFrame.ListState state) {
        return switch (state) {
            case ON_COOLDOWN, ACTIVE -> OrderGate.ALREADY_USED;
            case AVAILABLE -> OrderGate.TRY_ICON;
            case UNKNOWN -> OrderGate.UNKNOWN;
        };
    }

    static Duration confirmedCountdown(Duration first, Duration second) {
        if (first == null || second == null || first.isNegative() || second.isNegative()
                || first.isZero() || second.isZero()
                || Math.abs(first.minus(second).toSeconds()) > 15) return null;
        return first.compareTo(second) < 0 ? first : second;
    }

    static LocalDateTime nextAvailableCheck(LocalDateTime now, Duration remaining) {
        if (remaining == null || remaining.isZero() || remaining.isNegative())
            return now.plusMinutes(30); // OCR failed: explicitly a fallback, not a validated timer.
        return now.plus(remaining).plusMinutes(1);
    }

private boolean enactOrderFlow() {
        // Chief Order points are an earned resource, not diamonds, and have
        // no arbitrary spending cap. Require the EXACT order
        // title, explicit Enact price and stable available balance twice.
        // Unknown price/identity or insufficient points is a safe defer.
        if (emuManager.getSelectedEmulatorType() == EmulatorType.SERVE_EMU) {
            ChiefOrderLiveFrame.Inspection first = ChiefOrderLiveFrame.inspect(
                    emuManager.captureScreen(EMULATOR_NUMBER),
                    chiefOrderType.getDescription());
            sleepTask(220);
            ChiefOrderLiveFrame.Inspection second = ChiefOrderLiveFrame.inspect(
                    emuManager.captureScreen(EMULATOR_NUMBER),
                    chiefOrderType.getDescription());
            if (!ChiefOrderSpendPolicy.allowed(first, second,
                    chiefOrderType.getDescription(),
                    confirmedPointBalanceFirst, confirmedPointBalanceSecond)) {
                enactDeferred = true;
                String why = "Chief Order not authorized: first=" + first.action()
                        + " cost=" + first.cost() + ", second=" + second.action()
                        + " cost=" + second.cost() + ", balances="
                        + confirmedPointBalanceFirst + "/" + confirmedPointBalanceSecond
                        + "; identity, price or available points not verified";
                markWorkIncomplete("Chief Order: " + why);
                LocalDateTime retry = LocalDateTime.now().plusMinutes(ERROR_RETRY_MINUTES_VALUE);
                reschedule(retry);
                logWarning(routineLogChiefOrderLine(why + "; no Enact tap"));
                return false;
            }
            logInfo(routineLogChiefOrderLine("Chief Order Enact authorized: "
                    + second.order() + ", points=" + second.cost()
                    + " (balance verified); no diamonds"));
        }
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
        // A successful free Claim can return directly to the City/Furnace page
        // without showing the generic Rewards overlay.
        if (emuManager.getSelectedEmulatorType() == EmulatorType.SERVE_EMU
                && ChiefOrderLiveFrame.isCityHome(emuManager.captureScreen(EMULATOR_NUMBER))) {
            sleepTask(400);
            if (ChiefOrderLiveFrame.isCityHome(emuManager.captureScreen(EMULATOR_NUMBER))) {
                returnedHomeDirectly = true;
                logInfo(routineLogChiefOrderLine("Enact returned directly to verified City Home"));
                return true;
            }
        }
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
