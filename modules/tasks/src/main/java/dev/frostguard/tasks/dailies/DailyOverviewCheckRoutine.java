package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.nav.SidebarSection;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.vision.ocr.OcrEngine;
import dev.frostguard.vision.ocr.OcrException;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Read-only overview: never starts quests, spends resources or toggles another task.
 * Actual actions remain owned by their dedicated routines and scheduler.
 */
public class DailyOverviewCheckRoutine extends DelayedTask {
    public DailyOverviewCheckRoutine(AccountDescriptor profile, TpDailyTaskEnum type) {
        super(profile, type);
    }

    @Override protected LaunchPoint getRequiredStartLocation() { return LaunchPoint.ANY; }

    @Override protected void execute() {
        int configured = profile.getConfig(ConfigurationKeyEnum.DAILY_OVERVIEW_CHECK_OFFSET_INT, Integer.class);
        int interval = Math.max(30, Math.min(1440, configured));
        boolean opened = false;
        try {
            opened = navigationHelper.openSidebarSection(SidebarSection.DAILY);
            if (!opened) {
                defer("Daily Overview sidebar could not be verified");
                return;
            }
            RawImageData frame = emuManager.captureScreen(EMULATOR_NUMBER);
            if (!DailyOverviewFrame.isDailyOverview(frame)) {
                defer("Daily Overview content not recognized");
                return;
            }
            DailyOverviewFrame.Summary summary = DailyOverviewFrame.summarize(frame);
            logInfo("Daily Overview: " + summary.text());
            reschedule(LocalDateTime.now().plusMinutes(interval));
        } finally {
            if (opened && !navigationHelper.closeSidebar()) {
                markWorkIncomplete("Daily Overview sidebar did not close to City/World");
                logWarning("Daily Overview failed to return Home; " + TaskDiagnosticSnapshots.capture(
                        emuManager, EMULATOR_NUMBER, "daily-overview", "close-failed"));
            }
        }
    }

    private void defer(String reason) {
        markWorkIncomplete(reason);
        logWarning(reason + "; " + TaskDiagnosticSnapshots.capture(
                emuManager, EMULATOR_NUMBER, "daily-overview", "unrecognized"));
        reschedule(LocalDateTime.now().plusMinutes(20));
    }
}
