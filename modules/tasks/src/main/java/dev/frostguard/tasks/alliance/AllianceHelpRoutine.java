package dev.frostguard.tasks.alliance;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.time.LocalDateTime;

/**
 * A real, stand-alone Task Coach task for the old cooperative Alliance Help
 * shortcut injection. Detects the city HUD and the precise help shortcut on
 * the same fresh screen. Never clicks based only on text, stale detection, or
 * old date-stamped variants. No other tasks can inject taps during this run.
 */
public final class AllianceHelpRoutine extends DelayedTask {
    private static final PointData HOME_MIN = new PointData(590, 1145);
    private static final PointData HOME_MAX = new PointData(715, 1275);
    private static final int THRESHOLD = 90;
    private static final int SCHEDULE_MINUTES = 10;

    public AllianceHelpRoutine(AccountDescriptor profile, TpDailyTaskEnum type) {
        super(profile, type);
    }

    @Override protected LaunchPoint getRequiredStartLocation() { return LaunchPoint.HOME; }
    @Override protected boolean acceptsInjections() { return false; }

    @Override protected void execute() {
        try {
            RawImageData first=emuManager.captureScreen(EMULATOR_NUMBER);
            ImageSearchResultData pending=locateHelpWithCoachEvidence(first);
            if (!pending.isFound()) {
                if (!isCity(first)) {
                    markWorkIncomplete("Alliance Help: city home not confirmed");
                    logWarning("Alliance Help: current screen unverified, no tap");
                } else {
                    logInfo("Alliance Help: no pending request button on City; no action required");
                }
                return;
            }
            sleepTask(220);
            RawImageData second=emuManager.captureScreen(EMULATOR_NUMBER);
            ImageSearchResultData ready=locateHelpWithCoachEvidence(second);
            if (!AllianceHelpVerification.sameVisibleButton(pending, ready)) {
                markWorkIncomplete("Alliance Help: request vanished or moved before click");
                logWarning("Alliance Help: changed shortcut, refusing stale click");
                return;
            }
            tapInside(ready);
            sleepTask(650);
            RawImageData after=emuManager.captureScreen(EMULATOR_NUMBER);
            if (!AllianceHelpVerification.verifiedClaim(
                    true, isCity(after), locateHelpWithCoachEvidence(after).isFound())) {
                markWorkIncomplete("Alliance Help: click not verified by shortcut disappearance");
                logWarning("Alliance Help: input sent but no verified completed help request");
                return;
            }
            logInfo("Alliance Help request completed; shortcut disappeared on verified City");
        } finally {
            reschedule(LocalDateTime.now().plusMinutes(SCHEDULE_MINUTES));
        }
    }

    private ImageSearchResultData locateHelpWithCoachEvidence(RawImageData screen) {
        if (!isCorrectViewport(screen))return ImageSearchResultData.miss();
        // The earlier Task Coach run only captured City matches, never the
        // Help button itself: the raw OpenCV call bypassed coach evidence.
        // Reuse the SAME frame and central controller so the actual button
        // hit/miss (before + after click) is recorded for independent QA.
        var city=emuManager.locatePattern(EMULATOR_NUMBER,screen,
                TemplatesEnum.SERVE_EMU_CITY_ANCHOR, HOME_MIN,HOME_MAX,THRESHOLD);
        if (!city.isFound())return ImageSearchResultData.miss();
        return emuManager.locatePattern(EMULATOR_NUMBER,screen,
                TemplatesEnum.GAME_HOME_SHORTCUTS_HELP_REQUEST2,
                new PointData(0,0),new PointData(720,1280),THRESHOLD);
    }

    static boolean isCity(RawImageData screen) {
        return isCorrectViewport(screen)
                && OpenCvPatternLocator.locatePattern(screen,
                   TemplatesEnum.SERVE_EMU_CITY_ANCHOR.getTemplate(),
                   HOME_MIN,HOME_MAX,THRESHOLD).isFound();
    }

    static ImageSearchResultData locateHelp(RawImageData screen) {
        if (!isCity(screen)) return ImageSearchResultData.miss();
        return OpenCvPatternLocator.locatePattern(screen,
                TemplatesEnum.GAME_HOME_SHORTCUTS_HELP_REQUEST2.getTemplate(),
                new PointData(0,0),new PointData(720,1280),THRESHOLD);
    }

    private static boolean isCorrectViewport(RawImageData screen) {
        return screen != null && screen.isValid()
                && screen.getWidth() == 720 && screen.getHeight() == 1280;
    }
}
