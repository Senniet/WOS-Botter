package dev.frostguard.tasks.events;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.NavigationHelper.EventMenu;
import dev.frostguard.engine.helper.NavigationHelper.EventMenuOpenResult;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import dev.frostguard.vision.convert.GameTimeUtils;
import java.time.LocalDateTime;

/**
 * Windhowler daily participation, opt-in and capped at three attacks.
 * One Claim after 3/3. Unknown UI never authorizes attack, claim or paid actions.
 *
 * The November 18 live account was on cooldown, so post-Go battle screens
 * cannot yet be E2E-verified; any unfamiliar state is deliberately deferred.
 */
public final class EndlessTrialRoutine extends DelayedTask {
    private static final int MAX_DAILY_ATTACKS = 3;
    private static final AreaData DAILY_CHALLENGES = AreaData.of(82, 1177, 104, 1210);
    private static final AreaData CLOSE_DAILY = AreaData.of(649, 318, 703, 382);
    private static final AreaData GO = AreaData.of(254, 1138, 475, 1234);
    private static final AreaData THIRD_CLAIM = AreaData.of(510, 858, 659, 924);
    private static final AreaData NAMED_ATTACK = AreaData.of(398, 998, 525, 1080);
    private static final AreaData FORMATION_DEPLOY = AreaData.of(280, 1170, 440, 1250);
    private static final AreaData RESULT_CONFIRM = AreaData.of(286, 895, 425, 951);

    public EndlessTrialRoutine(AccountDescriptor profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override protected LaunchPoint getRequiredStartLocation() { return LaunchPoint.ANY; }
    @Override protected boolean consumesStamina() { return false; }

    @Override protected void execute() {
        // One transaction: protect confirmation screens against unrelated injections.
        runExclusiveUiTransaction(this::executeExclusive);
    }

    private void executeExclusive() {
        Integer setting = profile.getConfig(ConfigurationKeyEnum.ENDLESS_TRIAL_TARGET_ATTACKS_INT, Integer.class);
        int target = Math.min(MAX_DAILY_ATTACKS, Math.max(1, setting == null ? 3 : setting));
        EventMenuOpenResult result = navigationHelper.openEventMenu(EventMenu.ENDLESS_TRIAL);
        if (result != EventMenuOpenResult.REACHED) {
            defer("Endless Trial tab unavailable (" + result + ")", 30);
            return;
        }
        sleepTask(350);
        RawImageData main = screen();
        if (!EndlessTrialLiveFrame.isTrialPage(main)) {
            defer("Endless Trial header not verified", 20);
            return;
        }
        boolean mayLeave = true;
        try {
            for (int i = 0; i <= MAX_DAILY_ATTACKS; i++) {
                checkPreemption();
                EndlessTrialLiveFrame.DailyProgress progress = inspectDaily();
                if (!progress.verified()) {
                    defer("Daily Challenges progress not readable (" + progress.raw() + ")", 20);
                    return;
                }
                int completed = progress.completedAttacks();
                logInfo("Windhowler daily participation " + completed + "/3, target=" + target);
                if (completed >= target) {
                    if (completed == MAX_DAILY_ATTACKS) {
                        if (!claimOnce()) {
                            defer("Endless Trial 3/3 reached but the single Claim could not be verified", 20);
                            return;
                        }
                    } else {
                        closeDaily();
                    }
                    logInfo("Endless Trial daily target completed (" + completed + "/3)");
                    reschedule(GameTimeUtils.dailyResetTime().plusMinutes(35));
                    return;
                }

                if (!closeDaily() || !EndlessTrialLiveFrame.isTrialPage(screen())) {
                    defer("Daily Challenges panel did not close to Endless Trial", 20);
                    return;
                }
                EndlessTrialLiveFrame.Availability go = EndlessTrialLiveFrame.readGo(screen());
                if (go.state() == EndlessTrialLiveFrame.GoState.UNKNOWN) {
                    defer("Endless Trial Go not verified (" + go.caption() + ")", 20);
                    return;
                }
                sleepTask(420);
                if (EndlessTrialLiveFrame.readGo(screen()).state() == EndlessTrialLiveFrame.GoState.UNKNOWN) {
                    defer("Go button disappeared on the confirmation frame", 20);
                    return;
                }
                // The time beneath Go is the remaining event duration. Go
                // navigates to Windhowler's world-map boss card; it is not
                // itself a troop attack and not an attack cooldown.
                logInfo("Endless Trial Go verified; navigating to Windhowler for attack "
                        + (completed+1) + "/" + target);
                tapInside(GO);
                sleepTask(1200);
                if (!waitForWorldCard()) {
                    mayLeave = false;
                    defer("Windhowler world boss card not confirmed after Go", 15);
                    return;
                }
                if (!EndlessTrialBattleFrame.canAttackWorldBoss(screen())) {
                    mayLeave = false;
                    defer("World boss card visible but named Attack is not confirmed", 15);
                    return;
                }
                tapInside(NAMED_ATTACK);
                sleepTask(800);
                if (!waitForFormation()) {
                    mayLeave = false;
                    defer("Windhowler hero formation not confirmed after Attack", 15);
                    return;
                }
                if (!EndlessTrialBattleFrame.canDeploy(screen())) {
                    mayLeave = false;
                    defer("Windhowler formation shown but Deploy button not confirmed", 15);
                    return;
                }
                tapInside(FORMATION_DEPLOY);
                logInfo("Windhowler march deployed; awaiting verified damage result");
                BattleEnd battleEnd = waitForBattleEnd();
                if (battleEnd == BattleEnd.UNKNOWN) {
                    mayLeave = false;
                    defer("Windhowler march started, but no result or settled World screen could be verified", 5);
                    return;
                }
                if (battleEnd == BattleEnd.RESULT_DIALOG) {
                    tapInside(RESULT_CONFIRM);
                    sleepTask(750);
                    if (EndlessTrialBattleFrame.isDamageResult(screen())) {
                        mayLeave = false;
                        defer("Windhowler Confirm did not dismiss the damage result", 10);
                        return;
                    }
                } else {
                    logInfo("Windhowler returned to World without the first-time record popup; checking Daily counter");
                }
                if (!EndlessTrialBattleFrame.isWorldRoot(screen())) {
                    mayLeave = false;
                    defer("Windhowler battle ended but World root was not verified", 10);
                    return;
                }
                if (navigationHelper.openEventMenu(EventMenu.ENDLESS_TRIAL)
                        != EventMenuOpenResult.REACHED || !EndlessTrialLiveFrame.isTrialPage(screen())) {
                    mayLeave = false;
                    defer("Windhowler battle result confirmed but event page not reopened", 10);
                    return;
                }
                EndlessTrialLiveFrame.DailyProgress after = inspectDaily();
                if (!after.verified() || after.completedAttacks() <= completed) {
                    defer("Windhowler attack counter did not increase; refusing duplicate attack", 15);
                    return;
                }
                logInfo("Windhowler attack confirmed by challenge counter: " + after.completedAttacks() + "/3");
                if (!closeDaily()) {
                    defer("Endless Trial reward panel could not be closed", 20);
                    return;
                }
            }
            defer("Endless Trial execution limit reached without completion", 20);
        } finally {
            if (mayLeave) returnFromEvent();
        }
    }

    private boolean waitForWorldCard() {
        for (int attempt=0;attempt<5;attempt++) {
            if (EndlessTrialBattleFrame.isWorldBossCard(screen())) return true;
            sleepTask(650);
        }
        return false;
    }

    private boolean waitForFormation() {
        for (int attempt=0;attempt<5;attempt++) {
            if (EndlessTrialBattleFrame.isWindhowlerFormation(screen())) return true;
            sleepTask(550);
        }
        return false;
    }

    private enum BattleEnd { RESULT_DIALOG, WORLD_RETURNED, UNKNOWN }

    /**
     * Ordinary Windhowler attempts may return directly to World. Only the
     * first/new-record attack reliably shows "All-Time Highest". We always
     * confirm the exact Daily counter afterwards; World alone is not success.
     */
    private BattleEnd waitForBattleEnd() {
        for (int attempt=0;attempt<34;attempt++) {
            sleepTask(1250);
            RawImageData frame=screen();
            if (EndlessTrialBattleFrame.isDamageResult(frame))return BattleEnd.RESULT_DIALOG;
            // Wait at least 19 seconds; a World HUD can also appear while the
            // 10-second outbound march is still running. No duplicate attacks.
            if (attempt>=15 && EndlessTrialBattleFrame.isWorldRoot(frame)) {
                sleepTask(1100);
                if (EndlessTrialBattleFrame.isWorldRoot(screen()))return BattleEnd.WORLD_RETURNED;
            }
        }
        return BattleEnd.UNKNOWN;
    }

    private EndlessTrialLiveFrame.DailyProgress inspectDaily() {
        RawImageData before = screen();
        if (EndlessTrialLiveFrame.isDailyChallenges(before))
            return EndlessTrialLiveFrame.readModalProgress(before);
        if (!EndlessTrialLiveFrame.isTrialPage(before))
            return new EndlessTrialLiveFrame.DailyProgress(false,-1,"not-on-Endless-Trial");
        tapInside(DAILY_CHALLENGES);
        // The modal animates in from the bottom. One short fixed delay is not
        // reliable on Serve-Emu, so check three settled frames without re-tapping.
        for (int poll=0;poll<3;poll++) {
            sleepTask(650);
            RawImageData current = screen();
            if (EndlessTrialLiveFrame.isDailyChallenges(current))
                return EndlessTrialLiveFrame.readModalProgress(current);
        }
        return new EndlessTrialLiveFrame.DailyProgress(false,-1,"Daily-Challenges-not-visible");
    }

    /** EXACTLY ONE claim: the game claims all three completed rewards on that action. */
    private boolean claimOnce() {
        RawImageData modal = screen();
        if (EndlessTrialLiveFrame.allDailyRewardsClaimed(modal)) {
            logInfo("Endless Trial 3/3: all three Daily Challenges already claimed; no tap necessary");
            return closeDaily();
        }
        if (!EndlessTrialLiveFrame.thirdClaimReady(modal)) {
            logInfo("Endless Trial 3/3; no verified Claim button available (already claimed or unknown)");
            // If the modal reports 3/3 with a non-Claim button, it may already be claimed;
            // however we cannot count it as success without a positive claim state.
            return false;
        }
        tapInside(THIRD_CLAIM);
        sleepTask(950);
        logInfo("Endless Trial: submitted exactly one Claim for all three Daily Challenges");
        RawImageData after = screen();
        if (EndlessTrialLiveFrame.isDailyChallenges(after)) {
            if (!EndlessTrialLiveFrame.allDailyRewardsClaimed(after)) return false;
            return closeDaily();
        }
        if (templateSearchHelper.locatePattern(TemplatesEnum.SHARED_REWARDS_BANNER,
                SearchConfigConstants.DEFAULT_SINGLE).isFound()) {
            // Only dismiss a positively verified Rewards screen.
            tapInside(AreaData.of(292, 1123, 439, 1220));
            sleepTask(450);
            return true;
        }
        // Click may have succeeded; never repeat it without fresh evidence.
        logWarning("Endless Trial: Claim screen changed but reward overlay not positively identified");
        return false;
    }

    private boolean closeDaily() {
        if (!EndlessTrialLiveFrame.isDailyChallenges(screen())) return true;
        tapInside(CLOSE_DAILY);
        sleepTask(350);
        return EndlessTrialLiveFrame.isTrialPage(screen());
    }

    private void returnFromEvent() {
        if (EndlessTrialLiveFrame.isDailyChallenges(screen()) && !closeDaily()) {
            markWorkIncomplete("Endless Trial Daily Challenges panel remained open");
            return;
        }
        if (EndlessTrialLiveFrame.isTrialPage(screen())) {
            pressBack();
            sleepTask(450);
        }
        RawImageData returned = screen();
        if (EndlessTrialLiveFrame.isEventCarousel(returned)) {
            // Only the confirmed Events index gets one final Back.
            pressBack();
            sleepTask(450);
        }
        if (EndlessTrialLiveFrame.isTrialPage(screen())) {
            markWorkIncomplete("Endless Trial page did not close after verified Back");
            logWarning("Could not return to City/World after Endless Trial");
        }
        // Base task also validates the City/World root on completion.
    }

    private void defer(String reason, int minutes) {
        markWorkIncomplete(reason);
        logWarning(reason + "; " + TaskDiagnosticSnapshots.capture(
                emuManager, EMULATOR_NUMBER, "endless-trial", "unverified"));
        reschedule(LocalDateTime.now().plusMinutes(minutes));
    }

    private RawImageData screen() {
        checkPreemption();
        return emuManager.captureScreen(EMULATOR_NUMBER);
    }
}
