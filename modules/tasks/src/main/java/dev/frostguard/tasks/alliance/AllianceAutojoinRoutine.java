package dev.frostguard.tasks.alliance;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.engine.helper.NavigationHelper;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.engine.schedule.TaskQueue;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class AllianceAutojoinRoutine extends DelayedTask {

private static final AreaData RALLY_SECTION_TAB_VALUE = new AreaData(
			new PointData(81, 114),
			new PointData(195, 152));

private static final AreaData AUTOJOIN_SETTINGS_BUTTON_VALUE = new AreaData(
			new PointData(260, 1200),
			new PointData(450, 1240));

private static final PointData USE_ALL_TROOPS_BUTTON_VALUE = new PointData(98, 376);

private static final PointData SPECIFIC_FORMATION_BUTTON_VALUE = new PointData(98, 442);

private static final PointData QUEUE_COUNTER_SWIPE_START_VALUE = new PointData(430, 600);

private static final PointData QUEUE_COUNTER_SWIPE_END_VALUE = new PointData(40, 600);

private static final AreaData QUEUE_INCREMENT_BUTTON_VALUE = new AreaData(
			new PointData(460, 590),
			new PointData(497, 610));

private static final AreaData ENABLE_AUTOJOIN_BUTTON_VALUE = new AreaData(
			new PointData(380, 1070),
			new PointData(640, 1120));

private static final int MIN_QUEUE_COUNT_FLOOR = 1;

private static final int MAX_QUEUE_COUNT_LIMIT = 6;

private static final int DEFAULT_QUEUE_COUNT_VALUE = 3;

private static final int SCHEDULE_HOURS_VALUE = 7;

private static final int SCHEDULE_MINUTES_VALUE = 50;

private static final int LOOKAHEAD_RETRY_MINUTES_VALUE = 5;

private boolean useAllTroops;

private int queueCount;

public AllianceAutojoinRoutine(AccountDescriptor profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

@Override
    protected void execute() {
        hydrateConfiguration();
        if (deferForUpcomingAutoJoinReset()) return;
        if (!openUpAllianceWarMenu()) {
            manageTaskFailure("Alliance War unavailable");
            return;
        }
        if (!openUpAutoJoinSettings()) {
            manageTaskFailure("Auto-Join dialog title not verified");
            return;
        }
        // A preexisting active timer is a success. Do not stop/restart it.
        if (autoJoinActive()) {
            logInfo(routineLogAllianceAutojoinLine(
                    "Auto-Join already active; keeping current timer and settings"));
            if (closeAutoJoinAndReturnHome()) queueNextRun();
            else manageTaskFailure("Auto-Join active, City return unconfirmed");
            return;
        }
        configureTroopSelectionFlow();
        setAutoJoinQueuesFlow(queueCount);
        enableAutoJoinFlow();
        if (!autoJoinActive()) {
            markWorkIncomplete("Auto-Join activation not verified");
            closeAutoJoinAndReturnHome();
            manageTaskFailure("Start did not show active Restart state");
            return;
        }
        if (closeAutoJoinAndReturnHome()) {
            logInfo(routineLogAllianceAutojoinLine("Auto-Join enabled, returned to City"));
            queueNextRun();
        } else manageTaskFailure("Auto-Join enabled, City return unconfirmed");
    }

    private boolean detect(RawImageData frame, TemplatesEnum icon,
            PointData tl, PointData br, double threshold) {
        return frame!=null && frame.isValid() && frame.getWidth()==720
                && frame.getHeight()==1280 && OpenCvPatternLocator.locatePattern(
                        frame,icon.getTemplate(),tl,br,threshold).isFound();
    }

    private boolean autoJoinDialog(RawImageData frame) {
        return detect(frame,TemplatesEnum.AUTOJOIN_DIALOG_TITLE_2026,
                new PointData(260,110),new PointData(488,185),90);
    }

    private boolean autoJoinActive() {
        RawImageData frame=emuManager.captureScreen(EMULATOR_NUMBER);
        return autoJoinDialog(frame)
                && detect(frame,TemplatesEnum.AUTOJOIN_RESTART_2026,
                        new PointData(325,1035),new PointData(686,1155),90);
    }

    private boolean warPage(RawImageData frame) {
        return detect(frame,TemplatesEnum.AUTOJOIN_WAR_TITLE_2026,
                new PointData(80,5),new PointData(260,80),90);
    }

    private boolean alliancePage(RawImageData frame) {
        return detect(frame,TemplatesEnum.AUTOJOIN_ALLIANCE_TITLE_2026,
                new PointData(80,5),new PointData(260,80),90);
    }

    private boolean cityPage(RawImageData frame) {
        return detect(frame,TemplatesEnum.SERVE_EMU_CITY_ANCHOR,
                new PointData(590,1145),new PointData(715,1275),90);
    }

    private boolean closeAutoJoinAndReturnHome() {
        // Real verified exit: Auto-Join modal X -> War header Back ->
        // Alliance header Back -> City HUD; no generic overlay sweep.
        for (int step=0;step<4;step++) {
            checkPreemption();
            RawImageData frame=emuManager.captureScreen(EMULATOR_NUMBER);
            if(cityPage(frame)) return true;
            if(autoJoinDialog(frame)) {
                if(!detect(frame,TemplatesEnum.AUTOJOIN_DIALOG_CLOSE_2026,
                        new PointData(620,115),new PointData(710,190),88))
                    return false;
                tapNear(new PointData(666,148),4);
            } else if (warPage(frame) || alliancePage(frame)) {
                tapNear(new PointData(43,35),5);
            } else {
                logWarning(routineLogAllianceAutojoinLine(
                        "Unknown page during exit; no blind Back"));
                return false;
            }
            sleepTask(350);
        }
        return cityPage(emuManager.captureScreen(EMULATOR_NUMBER));
    }

private boolean deferForUpcomingAutoJoinReset() {
		TaskQueue queue = scheduleService.getCoordinator().getQueue(profile.getId());
		if (queue == null) {
			return false;
		}

		List<TpDailyTaskEnum> queuedTasks = queue.getNextQueuedTaskTypes(
				AutojoinActivationPolicy.LOOKAHEAD_TASK_COUNT);
		Optional<TpDailyTaskEnum> resetTask = AutojoinActivationPolicy.findUpcomingResetTask(queuedTasks);
		if (resetTask.isEmpty()) {
			return false;
		}

		reschedule(LocalDateTime.now().plusMinutes(LOOKAHEAD_RETRY_MINUTES_VALUE));
		logInfo(routineLogAllianceAutojoinLine("Deferring activation because "
				+ resetTask.get().getName() + " is within the next "
				+ AutojoinActivationPolicy.LOOKAHEAD_TASK_COUNT
				+ " queued tasks and will restore auto-join afterwards"));
		return true;
	}

@Override
	protected LaunchPoint getRequiredStartLocation() {
		return LaunchPoint.ANY;
	}

private String routineLogAllianceAutojoinLine(String note) {
        return "AllianceAutojoinRoutine | " + note;
    }

private void configureTroopSelectionFlow() {
		if (useAllTroops) {
			logInfo(routineLogAllianceAutojoinLine("Selecting 'Use all troops' option"));
			tapNear(USE_ALL_TROOPS_BUTTON_VALUE);
		} else {
			logInfo(routineLogAllianceAutojoinLine("Selecting 'Specific formation' option"));
			tapNear(SPECIFIC_FORMATION_BUTTON_VALUE);
		}
		sleepTask(700);

	}

private boolean openUpAutoJoinSettings() {
        RawImageData frame=emuManager.captureScreen(EMULATOR_NUMBER);
        if (autoJoinDialog(frame)) return true;
        if (!warPage(frame)) {
            logWarning(routineLogAllianceAutojoinLine(
                    "War page title not confirmed; refusing Auto-Join shortcut"));
            return false;
        }
        // Rally is already the default War tab in the 2026 client.
        tapInside(AUTOJOIN_SETTINGS_BUTTON_VALUE.topLeft(),
                AUTOJOIN_SETTINGS_BUTTON_VALUE.bottomRight());
        sleepTask(430);
        boolean visible=autoJoinDialog(emuManager.captureScreen(EMULATOR_NUMBER));
        if (!visible) logWarning(routineLogAllianceAutojoinLine(
                "Auto-Join dialog title not confirmed after shortcut"));
        return visible;
    }

private void setAutoJoinQueuesFlow(int count) {
		logInfo(routineLogAllianceAutojoinLine("Applying auto-join queue count to " + count));


		logDebug(routineLogAllianceAutojoinLine("Resetting queue counter to zero"));
		swipe(QUEUE_COUNTER_SWIPE_START_VALUE, QUEUE_COUNTER_SWIPE_END_VALUE);
		sleepTask(300);


		if (count > 1) {
			logDebug(routineLogAllianceAutojoinLine("Incrementing queue counter " + (count - 1) + " times"));
			tapInside(
					QUEUE_INCREMENT_BUTTON_VALUE.topLeft(),
					QUEUE_INCREMENT_BUTTON_VALUE.bottomRight(),
					(count - 1),
					400

			);
			sleepTask(300);

		}

		logDebug(routineLogAllianceAutojoinLine("Queue count set to " + count));
	}

private boolean openUpAllianceWarMenu() {
		logDebug(routineLogAllianceAutojoinLine("Entering Alliance War menu"));

		boolean success = navigationHelper.navigateToAllianceMenu(NavigationHelper.AllianceMenu.WAR);

		if (success) {
			sleepTask(1000);

			logDebug(routineLogAllianceAutojoinLine("Alliance War menu opened successfully"));
		} else {
			logError(routineLogAllianceAutojoinLine("Could not navigate to Alliance War menu"));
		}

		return success;
	}

private void hydrateConfiguration() {
        Boolean allTroopsSetting = profile.getConfig(
                ConfigurationKeyEnum.ALLIANCE_AUTOJOIN_USE_ALL_TROOPS_BOOL, Boolean.class);
        useAllTroops = Boolean.TRUE.equals(allTroopsSetting);

        Integer rawQueueCount = profile.getConfig(
                ConfigurationKeyEnum.ALLIANCE_AUTOJOIN_QUEUES_INT, Integer.class);
        queueCount = validQueueCount(rawQueueCount);
        if (rawQueueCount == null || queueCount != rawQueueCount) {
            logWarning(routineLogAllianceAutojoinLine("Missing/invalid queue count: " + rawQueueCount
                    + ". Using safe default: " + queueCount));
        }

		logInfo(routineLogAllianceAutojoinLine("Configuration loaded - Use all troops: " + useAllTroops +
				", Queue count: " + queueCount));
	}

    static int validQueueCount(Integer configured) {
        return configured != null && configured >= MIN_QUEUE_COUNT_FLOOR
                && configured <= MAX_QUEUE_COUNT_LIMIT
                ? configured : DEFAULT_QUEUE_COUNT_VALUE;
    }

private void enableAutoJoinFlow() {
		logInfo(routineLogAllianceAutojoinLine("Enabling auto-join"));
		tapInside(ENABLE_AUTOJOIN_BUTTON_VALUE.topLeft(), ENABLE_AUTOJOIN_BUTTON_VALUE.bottomRight());
		sleepTask(500);

		logDebug(routineLogAllianceAutojoinLine("Auto-join activation command sent"));
	}

private void manageTaskFailure(String reason) {
		logWarning(routineLogAllianceAutojoinLine("Routine pass did not complete: " + reason));

		LocalDateTime retryTime = LocalDateTime.now().plusMinutes(5);
		reschedule(retryTime);

		logInfo(routineLogAllianceAutojoinLine("Task rescheduled to retry in 5 minutes"));
	}

private void queueNextRun() {
		LocalDateTime nextExecutionTime = LocalDateTime.now()
				.plusHours(SCHEDULE_HOURS_VALUE)
				.plusMinutes(SCHEDULE_MINUTES_VALUE);

		reschedule(nextExecutionTime);

		logInfo(routineLogAllianceAutojoinLine("Alliance auto-join configured successfully. Next execution in " +
				SCHEDULE_HOURS_VALUE + "h " + SCHEDULE_MINUTES_VALUE + "m"));
	}
}
