package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.nav.SidebarDestination;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import dev.frostguard.vision.match.OpenCvPatternLocator;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Infirmary healing. Uses the verified 720x1280 City->Research Center->
 * Infirmary route. Only the blue Heal action is allowed, never the orange
 * premium Finish action or any speed-up confirmation.
 */
public class HealTroopsRoutine extends DelayedTask {
    private static final PointData INFIRMARY_BUILDING = new PointData(519, 430);
    private static final PointData CITY_HEAL_ACTION = new PointData(483, 853);
    private static final PointData QUICK_SELECT = new PointData(79, 1203);
    private static final PointData HEAL_BUTTON = new PointData(575, 1205);
    private static final Pattern TIMER = Pattern.compile("(?:\\d{1,2}:)?\\d{1,2}:\\d{2}");
    private static final Pattern INJURED_COUNT = Pattern.compile("([0-9,]+)\\s*/\\s*([0-9,]+)");
    private static final Duration RETRY = Duration.ofMinutes(5);
    private boolean healingStarted;

    public HealTroopsRoutine(AccountDescriptor profile, TpDailyTaskEnum task) {
        super(profile, task);
    }

    @Override protected LaunchPoint getRequiredStartLocation() { return LaunchPoint.HOME; }

    @Override public boolean provideDailyMissionProgress() { return healingStarted; }

    static boolean shouldQuickSelect(String mode) {
        return mode == null || !mode.toLowerCase(java.util.Locale.ROOT).startsWith("small");
    }

    static Duration parseTime(String text) {
        if (text == null) return null;
        Matcher m=TIMER.matcher(text.replace('O','0').replace('o','0'));
        if (!m.find()) return null;
        String[] parts=m.group().split(":");
        long seconds=0;
        for (String part:parts) seconds=seconds*60+Long.parseLong(part);
        return seconds>0 && seconds<=48*3600 ? Duration.ofSeconds(seconds) : null;
    }

    static Integer injuredCount(String text) {
        if (text == null) return null;
        Matcher m=INJURED_COUNT.matcher(text);
        if (!m.find()) return null;
        try {
            int count=Integer.parseInt(m.group(1).replace(",",""));
            return count>=0 ? count:null;
        } catch(NumberFormatException e) { return null; }
    }

    private boolean match(RawImageData frame, TemplatesEnum sprite, PointData tl, PointData br, double threshold) {
        return frame!=null && frame.isValid() && frame.getWidth()==720 && frame.getHeight()==1280
                && OpenCvPatternLocator.locatePattern(frame, sprite.getTemplate(),tl,br,threshold).isFound();
    }

    private boolean selected(RawImageData frame) {
        return match(frame,TemplatesEnum.INFIRMARY_CITY_SELECTED_2026,
                new PointData(270,505),new PointData(460,580),86);
    }

    private boolean healPage(RawImageData frame) {
        return match(frame,TemplatesEnum.INFIRMARY_HEAL_TITLE_2026,
                new PointData(80,8),new PointData(302,75),89);
    }

    private boolean healReady(RawImageData frame) {
        return healPage(frame) && match(frame,TemplatesEnum.INFIRMARY_HEAL_BUTTON_2026,
                new PointData(510,1155),new PointData(675,1235),89);
    }

    private boolean cityVisible(RawImageData frame) {
        return match(frame,TemplatesEnum.SERVE_EMU_CITY_ANCHOR,
                new PointData(590,1145),new PointData(715,1275),90);
    }

    private void defer(String note, Duration delay) {
        logWarning("Heal Troops: "+note+"; "+TaskDiagnosticSnapshots.capture(
                emuManager,EMULATOR_NUMBER,"heal-troops",note.replaceAll("[^a-zA-Z0-9-]","-")));
        if (!healingStarted) markWorkIncomplete("Heal Troops: "+note);
        reschedule(LocalDateTime.now().plus(delay));
    }

    @Override protected void execute() {
        healingStarted=false;
        boolean allianceHelp=Boolean.TRUE.equals(profile.getConfig(
                ConfigurationKeyEnum.HEAL_REQUEST_ALLIANCE_HELP_BOOL,Boolean.class));
        String mode=profile.getConfig(ConfigurationKeyEnum.HEAL_BATCH_MODE_STRING,String.class);

        RawImageData start=emuManager.captureScreen(EMULATOR_NUMBER);
        if (!healPage(start) && !selected(start)) {
            if (!navigationHelper.navigateToSidebarDestination(SidebarDestination.RESEARCH_CENTER)) {
                defer("Research Center shortcut absent",RETRY);
                return;
            }
            // Live-validated route on emulator-5554, 2026-11-17:
            // tap adjacent Infirmary, then verify its own name.
            tapNear(INFIRMARY_BUILDING,7);
            sleepTask(550);
            start=emuManager.captureScreen(EMULATOR_NUMBER);
        }

        if (selected(start)) {
            Duration busy=readSelectedTimer();
            if (busy!=null) {
                logInfo("Infirmary already healing; next collection attempt after "+busy);
                reschedule(LocalDateTime.now().plus(busy).plusSeconds(25));
                return;
            }
            if (match(start,TemplatesEnum.INFIRMARY_ACTIVE_HEALING_2026,
                    new PointData(250,615),new PointData(330,680),90)) {
                // The timer may be unreadable while its green plus/progress
                // badge is still clearly visible. Never open another batch.
                logInfo("Infirmary busy badge confirmed; no duplicate Heal tap");
                reschedule(LocalDateTime.now().plusMinutes(3));
                return;
            }
            // A selected Infirmary also exposes Finish and sometimes Speedup.
            // Only the blue Heal action may be opened.
            if (!match(start,TemplatesEnum.INFIRMARY_CITY_HEAL_2026,
                    new PointData(420,760),new PointData(590,930),85)) {
                defer("Infirmary selected but blue Heal action not confirmed",RETRY);
                return;
            }
            tapNear(CITY_HEAL_ACTION,5);
            sleepTask(400);
            start=emuManager.captureScreen(EMULATOR_NUMBER);
        }
        if (!healPage(start)) {
            defer("Heal Injured title not verified",RETRY);
            return;
        }

        Integer wounded=readWounded();
        // Live-verified 2026 Infirmary empty page shows an explicit
        // "No injured troops to show. All's well!" banner. Prefer that
        // visual gate when OCR drops the tiny 0/19,300 counter.
        boolean empty=match(start,TemplatesEnum.INFIRMARY_EMPTY_2026,
                new PointData(100,565),new PointData(625,650),85);
        if (empty || (wounded!=null && wounded==0)) {
            logInfo("No severely injured troops remaining");
            tapNear(new PointData(39,37),5);
            reschedule(LocalDateTime.now().plusMinutes(30));
            return;
        }

        if (shouldQuickSelect(mode)) {
            if (!match(start,TemplatesEnum.INFIRMARY_HEAL_BUTTON_2026,
                    new PointData(510,1155),new PointData(675,1235),89)) {
                defer("Full-batch Heal panel controls not verified",RETRY);
                return;
            }
            tapNear(QUICK_SELECT,6);
            sleepTask(400);
        }

        // On the same layout, the orange Finish button is immediately left
        // of Heal. Never use shared footer action matching or random wide taps.
        RawImageData first=emuManager.captureScreen(EMULATOR_NUMBER);
        if (!healReady(first)) {
            defer("Blue resource Heal button absent or disabled",RETRY);
            return;
        }
        sleepTask(170);
        RawImageData second=emuManager.captureScreen(EMULATOR_NUMBER);
        if (!healReady(second)) {
            defer("Blue resource Heal button did not persist for two frames",RETRY);
            return;
        }
        tapNear(HEAL_BUTTON,5);
        sleepTask(600);

        RawImageData after=emuManager.captureScreen(EMULATOR_NUMBER);
        if (cityVisible(after) && !healPage(after)) {
            healingStarted=true;
            logInfo("Heal action confirmed by exit to City; no gems or speed-ups used");
            if (allianceHelp) {
                // Optional help only when a known in-game help request badge
                // appears. Never tap the generic help button blindly.
                ImageSearchResultData help=templateSearchHelper.locatePattern(
                        TemplatesEnum.GAME_HOME_SHORTCUTS_HELP_REQUEST4,
                        dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig.builder()
                            .withMaxAttempts(1).withThreshold(90)
                            .withArea(AreaData.of(280,490,445,675)).build());
                if (help!=null && help.isFound()) {
                    tapInside(help);
                    logInfo("Verified alliance help request tapped");
                }
            }
            Duration remaining=readSelectedTimer();
            reschedule(LocalDateTime.now().plus(
                    remaining!=null ? remaining.plusSeconds(25) : Duration.ofMinutes(4)));
            return;
        }

        // No blind retry: the server may already have accepted Heal.
        defer("Heal transition unconfirmed; avoiding duplicate submission",Duration.ofMinutes(10));
    }

    private Integer readWounded() {
        try {
            String value=emuManager.readText(EMULATOR_NUMBER,
                    new PointData(355,162),new PointData(550,211),
                    OcrSettingsData.configurator()
                        .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build(),false);
            return injuredCount(value);
        } catch(Exception error) {
            logDebug("Wounded count OCR unavailable: "+error.getClass().getSimpleName());
            return null;
        }
    }

    private Duration readSelectedTimer() {
        try {
            String value=emuManager.readText(EMULATOR_NUMBER,
                    new PointData(305,626),new PointData(438,663),
                    OcrSettingsData.configurator()
                        .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build(),false);
            return parseTime(value);
        } catch(Exception error) {
            logDebug("Infirmary countdown unreadable: "+error.getClass().getSimpleName());
            return null;
        }
    }
}
