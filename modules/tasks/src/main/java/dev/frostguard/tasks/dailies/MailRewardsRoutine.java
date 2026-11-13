package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.engine.service.StatisticsService;
import dev.frostguard.vision.convert.GameTimeUtils;
import java.time.LocalDateTime;
import java.util.List;

import static dev.frostguard.tasks.dailies.MailClaimPassPolicy.Decision;
import static dev.frostguard.tasks.dailies.MailClaimPassPolicy.Evidence;

public class MailRewardsRoutine extends DelayedTask {

private static final int MAX_MAIL_CLAIM_PASSES = 3;

private static final int MAX_UNREAD_REPORT_OPENS = 3;

private static final int MAIL_MENU_SEARCH_RETRIES_VALUE = 5;

private static final int MAIL_MENU_OPEN_VERIFICATION_RETRIES_VALUE = 5;

private static final int UNCLAIMED_REWARDS_SEARCH_RETRIES_VALUE = 3;

private static final int MAIL_TAB_COUNT_VALUE = 3;

private static final int ERROR_RETRY_MINUTES_VALUE = 10;

private static final int DEFAULT_OFFSET_MINUTES_VALUE = Integer.parseInt(
        ConfigurationKeyEnum.MAIL_REWARDS_OFFSET_INT.getDefaultValue());

private static final PointData MAIL_MENU_SEARCH_TOP_LEFT_VALUE = new PointData(600, 1000);

private static final PointData MAIL_MENU_SEARCH_BOTTOM_RIGHT_VALUE = new PointData(715, 1100);

private static final PointData MAIL_MENU_OPEN_TOP_LEFT_VALUE = new PointData(70, 70);

private static final PointData MAIL_MENU_OPEN_BOTTOM_RIGHT_VALUE = new PointData(195, 145);

private static final PointData MAIL_LIST_INDICATORS_TOP_LEFT_VALUE = new PointData(625, 175);

private static final PointData MAIL_LIST_INDICATORS_BOTTOM_RIGHT_VALUE = new PointData(715, 1160);

private static final PointData UNREAD_REPORT_SHORTCUT_TOP_LEFT_VALUE = new PointData(300, 0);

private static final PointData UNREAD_REPORT_SHORTCUT_BOTTOM_RIGHT_VALUE = new PointData(420, 95);

private static final PointData CLAIM_BUTTON_TOP_LEFT_VALUE = new PointData(420, 1227);

private static final PointData CLAIM_BUTTON_BOTTOM_RIGHT_VALUE = new PointData(450, 1250);

private static final int CLAIM_BUTTON_TAP_COUNT_VALUE = 4;

private static final int CLAIM_BUTTON_TAP_DELAY_MS = 500;

private static final int UNREAD_REPORT_OPEN_DELAY_MS = 500;

private static final PointData TAB_ALLIANCE_VALUE = new PointData(230, 185);

private static final PointData TAB_SYSTEM_VALUE = new PointData(360, 185);

private static final PointData TAB_REPORTS_VALUE = new PointData(500, 185);

private static final PointData[] MAIL_TAB_BUTTONS = { TAB_ALLIANCE_VALUE, TAB_SYSTEM_VALUE, TAB_REPORTS_VALUE };

private static final String[] MAIL_TAB_NAMES = { "Alliance", "System", "Reports" };

private static final int SYSTEM_TAB_INDEX = 1;

private int scheduleOffsetMinutes;

public MailRewardsRoutine(AccountDescriptor profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

@Override
    protected void execute() {
        hydrateConfiguration();

        if (!openUpMailMenu()) {
            manageMailMenuOpenFailure();
            return;
        }

        handleAllMailTabs();
        dismissMailMenu();
        queueNextRun();
    }

@Override
    protected LaunchPoint getRequiredStartLocation() {
        return LaunchPoint.ANY;
    }

@Override
    public boolean provideDailyMissionProgress() {
        return false;
    }

private String routineLogMailRewardsLine(String note) {
        return "MailRewardsRoutine | " + note;
    }

private void manageMailMenuOpenFailure() {
        logError(routineLogMailRewardsLine("Could not open mail menu. Retrying in " + ERROR_RETRY_MINUTES_VALUE + " minutes."));
        reschedule(LocalDateTime.now().plusMinutes(ERROR_RETRY_MINUTES_VALUE));
    }

private LocalDateTime computeNextExecutionTime() {
        LocalDateTime proposedTime = LocalDateTime.now().plusMinutes(scheduleOffsetMinutes);
        LocalDateTime gameResetTime = GameTimeUtils.dailyResetTime();


        return proposedTime.isAfter(gameResetTime) ? gameResetTime : proposedTime;
    }

private boolean redeemAllVisibleRewards() {
        if (!confirmMailMenuOpened()) {
            logWarning(routineLogMailRewardsLine("Mail title absent; refusing unverified footer action"));
            return false;
        }
        var claimRegion = SearchConfig.builder().withMaxAttempts(1).withThreshold(92)
                .withArea(new AreaData(new PointData(390, 1180), new PointData(720, 1279))).build();
        ImageSearchResultData before = templateSearchHelper.locatePattern(
                TemplatesEnum.SERVE_EMU_MAIL_READ_CLAIM, claimRegion);
        if (!before.isFound()) {
            logWarning(routineLogMailRewardsLine("Read & Claim All control unverified; no blind footer taps"));
            return false;
        }
        sleepTask(150);
        ImageSearchResultData after = templateSearchHelper.locatePattern(
                TemplatesEnum.SERVE_EMU_MAIL_READ_CLAIM, claimRegion);
        if (!dev.frostguard.engine.helper.RecognitionConsensus.agrees(before, after, 12)) {
            logWarning(routineLogMailRewardsLine("Mail claim action unstable between frames"));
            return false;
        }
        logInfo(routineLogMailRewardsLine("Read & Claim All confirmed; tapping once"));
        tapInside(after);
        sleepTask(300);
        return true;
    }

private Evidence findUnreadMailEvidence() {
        List<ImageSearchResultData> unreadMarkers = templateSearchHelper.locateAllPatterns(
                TemplatesEnum.MAIL_UNCLAIMED_REWARDS,
                SearchConfig.builder()
                        .withArea(new AreaData(
                                MAIL_LIST_INDICATORS_TOP_LEFT_VALUE,
                                MAIL_LIST_INDICATORS_BOTTOM_RIGHT_VALUE))
                        .withMaxAttempts(UNCLAIMED_REWARDS_SEARCH_RETRIES_VALUE)
                        .withDelay(100L)
                        .withMaxResults(20)
                        .build());

        return Evidence.fromMatches(unreadMarkers);
    }

private MailClaimResult claimMailRewards(String tabName) {
        Evidence before = findUnreadMailEvidence();
        if (before.isEmpty()) {
            logDebug(routineLogMailRewardsLine(tabName + ": no unclaimed mail markers; no footer tap needed"));
            return new MailClaimResult(before, Decision.COMPLETE);
        }
        for (int completedPasses = 1; completedPasses <= MAX_MAIL_CLAIM_PASSES; completedPasses++) {
            if (!redeemAllVisibleRewards()) {
                return new MailClaimResult(before, Decision.STOP_NO_PROGRESS);
            }
            Evidence after = findUnreadMailEvidence();
            Decision decision = MailClaimPassPolicy.decide(
                    before, after, completedPasses, MAX_MAIL_CLAIM_PASSES);

            if (!before.isEmpty() && !before.sameVisualStateAs(after)) {
                StatisticsService.obtain().addToCounter(profile, "Mail Rewards Claimed", 1);
            }

            if (decision == Decision.COMPLETE) {
                logDebug(routineLogMailRewardsLine(tabName + " tab claim pass completed; unread markers "
                        + before.count() + " -> 0."));
                return new MailClaimResult(after, decision);
            }
            if (decision == Decision.STOP_NO_PROGRESS) {
                logWarning(routineLogMailRewardsLine(tabName + " tab still has " + after.count()
                        + " unread marker(s) after claim pass " + completedPasses
                        + "; no claim progress detected, ending claim passes."));
                return new MailClaimResult(after, decision);
            }
            if (decision == Decision.STOP_BUDGET_EXHAUSTED) {
                logWarning(routineLogMailRewardsLine(tabName + " tab still has " + after.count()
                        + " unread marker(s) after the bounded " + MAX_MAIL_CLAIM_PASSES
                        + " claim passes; ending claim passes."));
                return new MailClaimResult(after, decision);
            }

            logInfo(routineLogMailRewardsLine(tabName + " tab claim progress detected; unread markers "
                    + before.count() + " -> " + after.count() + ", retrying within pass budget "
                    + completedPasses + "/" + MAX_MAIL_CLAIM_PASSES + "."));
            before = after;
        }

        return new MailClaimResult(before, Decision.STOP_BUDGET_EXHAUSTED);
    }

private ImageSearchResultData findUnreadReportShortcut() {
        return templateSearchHelper.locatePattern(
                TemplatesEnum.MAIL_UNREAD_REPORT_SHORTCUT,
                SearchConfig.builder()
                        .withArea(new AreaData(
                                UNREAD_REPORT_SHORTCUT_TOP_LEFT_VALUE,
                                UNREAD_REPORT_SHORTCUT_BOTTOM_RIGHT_VALUE))
                        .withMaxAttempts(2)
                        .withDelay(100L)
                        .build());
    }

private void openUnreadSystemReports(Evidence initialEvidence) {
        Evidence before = initialEvidence;
        for (int completedOpens = 1; completedOpens <= MAX_UNREAD_REPORT_OPENS; completedOpens++) {
            ImageSearchResultData shortcut = findUnreadReportShortcut();
            if (!shortcut.isFound()) {
                logWarning(routineLogMailRewardsLine("System tab has " + before.count()
                        + " persistent unread marker(s), but the unread-report shortcut is not visible; "
                        + "continuing with the next tab."));
                return;
            }

            logInfo(routineLogMailRewardsLine("Opening unread System report " + completedOpens
                    + "/" + MAX_UNREAD_REPORT_OPENS + " to mark it as read."));
            tapInside(shortcut);
            sleepTask(UNREAD_REPORT_OPEN_DELAY_MS);
            pressBack();
            sleepTask(UNREAD_REPORT_OPEN_DELAY_MS);

            Evidence after = findUnreadMailEvidence();
            Decision decision = MailClaimPassPolicy.decide(
                    before, after, completedOpens, MAX_UNREAD_REPORT_OPENS);
            if (decision == Decision.COMPLETE) {
                logInfo(routineLogMailRewardsLine("System reports marked as read; unread markers "
                        + before.count() + " -> 0."));
                return;
            }
            if (decision == Decision.STOP_NO_PROGRESS) {
                logWarning(routineLogMailRewardsLine("Opening the unread System report made no progress; "
                        + after.count() + " unread marker(s) remain, continuing with the next tab."));
                return;
            }
            if (decision == Decision.STOP_BUDGET_EXHAUSTED) {
                logWarning(routineLogMailRewardsLine("System report open budget exhausted; "
                        + after.count() + " unread marker(s) remain, continuing with the next tab."));
                return;
            }

            logInfo(routineLogMailRewardsLine("Unread System report consumed; unread markers "
                    + before.count() + " -> " + after.count() + "."));
            before = after;
        }
    }

private boolean confirmMailMenuOpened() {
        ImageSearchResultData inMailMenu = templateSearchHelper.locatePattern(
                TemplatesEnum.MAIL_MENU_OPEN,
                SearchConfig.builder()
                        .withArea(new AreaData(MAIL_MENU_OPEN_TOP_LEFT_VALUE, MAIL_MENU_OPEN_BOTTOM_RIGHT_VALUE))
                        .withMaxAttempts(MAIL_MENU_OPEN_VERIFICATION_RETRIES_VALUE)
                        .withDelay(200L)
                        .build());

        if (!inMailMenu.isFound()) {
            inMailMenu = templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_MAIL_TITLE,
                    SearchConfig.builder().withMaxAttempts(2).withDelay(120).withThreshold(90)
                            .withArea(new AreaData(new PointData(75, 75),
                                    new PointData(210, 150))).build());
            if (!inMailMenu.isFound()) {
                logError(routineLogMailRewardsLine("Mail menu title unrecognized; refusing actions"));
                return false;
            }
        }

        logDebug(routineLogMailRewardsLine("Mail menu opened finished cleanly."));
        return true;
    }

private void hydrateConfiguration() {
        Integer configuredOffset = profile.getConfig(
                ConfigurationKeyEnum.MAIL_REWARDS_OFFSET_INT, Integer.class);
        this.scheduleOffsetMinutes = (configuredOffset != null)
                ? configuredOffset
                : DEFAULT_OFFSET_MINUTES_VALUE;

        logDebug(routineLogMailRewardsLine("Configuration loaded - Schedule offset: " + scheduleOffsetMinutes + " minutes"));
    }

private void handleAllMailTabs() {
        for (int i = 0; i < MAIL_TAB_COUNT_VALUE; i++) {
            PointData tabButton = MAIL_TAB_BUTTONS[i];
            String tabName = MAIL_TAB_NAMES[i];

            logInfo(routineLogMailRewardsLine("Processing " + tabName + " tab."));
            handleMailTab(tabButton, tabName, i == SYSTEM_TAB_INDEX);
        }
    }

private void switchToTabFlow(PointData tabButton) {
        tapNear(tabButton);
        sleepTask(200);

    }

private void dismissMailMenu() {
        logDebug(routineLogMailRewardsLine("Closing mail menu."));
        if (isCurrentMailMenuVisible()) {
            // The 2026 UI exposes an explicit X. Android Back is unsafe on
            // a World map (it can open the Quit game confirmation).
            tapInside(new PointData(661, 83), new PointData(708, 136));
            sleepTask(250);
            return;
        }
        // Preserve legacy close behaviour only when its old mail header is present.
        if (templateSearchHelper.locatePattern(TemplatesEnum.MAIL_MENU_OPEN,
                SearchConfig.builder().withMaxAttempts(1)
                        .withArea(new AreaData(MAIL_MENU_OPEN_TOP_LEFT_VALUE,
                                MAIL_MENU_OPEN_BOTTOM_RIGHT_VALUE)).build()).isFound()) {
            pressBack();
        }
    }

private boolean isCurrentMailMenuVisible() {
        return templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_MAIL_TITLE,
                SearchConfig.builder().withMaxAttempts(1).withThreshold(90)
                        .withArea(AreaData.of(75, 75, 210, 150)).build()).isFound();
    }

private boolean openUpMailMenu() {
        logInfo(routineLogMailRewardsLine("Entering mail menu."));
        if (isCurrentMailMenuVisible()) return true;

        ImageSearchResultData mailMenu = templateSearchHelper.locatePattern(
                TemplatesEnum.MAIL_MENU,
                SearchConfig.builder()
                        .withArea(new AreaData(MAIL_MENU_SEARCH_TOP_LEFT_VALUE, MAIL_MENU_SEARCH_BOTTOM_RIGHT_VALUE))
                        .withMaxAttempts(MAIL_MENU_SEARCH_RETRIES_VALUE)
                        .withDelay(200L)
                        .build());

        if (!mailMenu.isFound()) {
            logError(routineLogMailRewardsLine("Could not find mail menu icon."));
            return false;
        }

        tapInside(mailMenu);
        sleepTask(500);


        return confirmMailMenuOpened();
    }

private void handleMailTab(PointData tabButton, String tabName, boolean openUnreadReports) {
        switchToTabFlow(tabButton);
        MailClaimResult result = claimMailRewards(tabName);
        if (openUnreadReports
                && result.decision() == Decision.STOP_NO_PROGRESS
                && !result.remainingEvidence().isEmpty()) {
            openUnreadSystemReports(result.remainingEvidence());
        }
    }

private void queueNextRun() {
        LocalDateTime nextExecutionTime = computeNextExecutionTime();
        reschedule(nextExecutionTime);
        logInfo(routineLogMailRewardsLine("Mail rewards task completed. Next run at: " + nextExecutionTime.format(DATETIME_FORMATTER)));
    }

private record MailClaimResult(Evidence remainingEvidence, Decision decision) {
    }
}
