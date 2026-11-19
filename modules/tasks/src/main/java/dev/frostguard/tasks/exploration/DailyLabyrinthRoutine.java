package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import dev.frostguard.vision.convert.GameTimeUtils;
import java.time.LocalDateTime;

/**
 * Daily Labyrinth -> Cave of Monsters. The 2026 version opens a stage map,
 * Challenge, troop-ratio formation and Deploy; it does NOT have the old
 * Land-of-Heroes Quick Challenge / Raid / Quick Deploy controls.
 *
 * Every tap requires a verified state and the one daily attempt only counts
 * as completed if the game actually displays Victory. No paid replenishment
 * or automatic repeated battle is permitted.
 */
public final class DailyLabyrinthRoutine extends DelayedTask {
    private static final PointData EXIT_CAVE = new PointData(680,36);
    private static final PointData BACK_OVERVIEW = new PointData(41,36);
    private static final PointData BACK_FORMATION = new PointData(41,36);
    // Verified on a real 19 Nov 2026 silver Defeat overlay. This is outside
    // both lower CTAs (Battle Report and Retry) and the Pet Power suggestions.
    private static final PointData DEFEAT_NEUTRAL_DISMISS = new PointData(354,810);
    private static final int ENTRY_DELAY_MS=440;
    private boolean completedVerified;
    private boolean defeatVerified;
    private boolean defeatDismissAttempted;

    public DailyLabyrinthRoutine(AccountDescriptor profile,TpDailyTaskEnum type){super(profile,type);}

    @Override public boolean provideDailyMissionProgress(){return shouldReportDailyMissionProgress(completedVerified);}
    static boolean shouldReportDailyMissionProgress(boolean completed){return completed;}
    @Override protected LaunchPoint getRequiredStartLocation(){return LaunchPoint.HOME;}
    @Override protected boolean acceptsInjections(){return false;}
    @Override protected void execute(){
        completedVerified=false;
        defeatVerified=false;
        defeatDismissAttempted=false;
        runExclusiveUiTransaction(this::runCaveOfMonsters);
    }

    private void runCaveOfMonsters(){
        try {
            // A previous interrupted run can leave Cave Defeat on-screen.
            // Recover it BEFORE navigation, otherwise the next attempt can
            // become trapped trying to find Home behind the defeat overlay.
            if(DailyLabyrinthFrame.defeat(emuManager.captureScreen(EMULATOR_NUMBER))){
                defeatVerified=true;
                logInfo("Labyrinth: pending Defeat from an earlier run detected; dismissing without Retry");
                if(!dismissDefeatToCave()){
                    defer("Pending Defeat could not be dismissed safely; Retry forbidden",90);
                    return;
                }
                markWorkIncomplete("Labyrinth: previous Defeat recovered; no repeated challenge");
                reschedule(GameTimeUtils.dailyResetTime());
                return;
            }
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME,this::checkPreemption);
            if(!navigationHelper.navigateToLabyrinth()){
                defer("Labyrinth Daily sidebar destination not found",60);return;
            }
            RawImageData overview=emuManager.captureScreen(EMULATOR_NUMBER);
            if(!DailyLabyrinthFrame.overview(overview)){
                defer("The Labyrinth overview and Cave of Monsters portal not verified",40);return;
            }
            // The portal is the only free daily dungeon currently unlocked.
            // A second screenshot avoids following a transitioning sidebar.
            sleepTask(180);
            RawImageData fresh=emuManager.captureScreen(EMULATOR_NUMBER);
            if(!DailyLabyrinthFrame.overview(fresh)){
                defer("Cave of Monsters entrance changed before tap",30);return;
            }
            tapNear(new PointData(204,484),4);
            sleepTask(ENTRY_DELAY_MS);
            RawImageData cave=waitForCave();
            if(cave==null){defer("Cave of Monsters stage map not confirmed",30);return;}
            ImageSearchResultData first=DailyLabyrinthFrame.challenge(cave);
            if(!first.isFound()){
                logInfo("Labyrinth: no verified free Cave of Monsters Challenge on current stage; retry after daily reset");
                reschedule(GameTimeUtils.dailyResetTime());return;
            }
            sleepTask(160);
            ImageSearchResultData second=DailyLabyrinthFrame.challenge(
                    emuManager.captureScreen(EMULATOR_NUMBER));
            if(!RecognitionConsensus.agrees(first,second,26)){
                defer("Cave Challenge shifted before tap",20);return;
            }
            tapNear(second.getPoint(),4);
            RawImageData formation=waitForFormation();
            if(formation==null){defer("Cave of Monsters troop formation not confirmed",30);return;}
            ImageSearchResultData deployFirst=DailyLabyrinthFrame.deploy(formation);
            sleepTask(180);
            ImageSearchResultData deploySecond=DailyLabyrinthFrame.deploy(
                    emuManager.captureScreen(EMULATOR_NUMBER));
            if(!RecognitionConsensus.agrees(deployFirst,deploySecond,28)){
                defer("Cave Deploy moved or disappeared; no blind action",30);return;
            }
            // One normal pet-stat battle, no resource/item/gem purchases.
            logInfo("Labyrinth Cave of Monsters: deploying one positively verified daily battle");
            tapNear(deploySecond.getPoint(),4);
            RawImageData resultScreen=waitForBattleOutcome();
            if(resultScreen==null){
                defer("Cave battle launched but neither Victory nor Defeat was verified; no repeat Deploy",120);
                return;
            }
            if(DailyLabyrinthFrame.defeat(resultScreen)){
                defeatVerified=true;
                logInfo("Labyrinth Cave of Monsters: actual Defeat detected; never clicking Retry");
                if(!dismissDefeatToCave()){
                    defer("Defeat detected but Cave return is unverified; no Retry",90);
                    return;
                }
                logInfo("Labyrinth: Defeat overlay dismissed; Cave stage map verified");
                markWorkIncomplete("Labyrinth battle lost; Retry skipped; waiting for next reset");
                reschedule(GameTimeUtils.dailyResetTime());
                return;
            }
            RawImageData win=resultScreen;
            completedVerified=true;
            logInfo("Labyrinth Cave of Monsters: actual Victory result confirmed");
            // Next selects the next stage. It is not Auto-Challenge, which
            // is locked and must never be activated by unattended automation.
            ImageSearchResultData nextFirst=DailyLabyrinthFrame.nextStage(win);
            sleepTask(210);
            ImageSearchResultData nextSecond=DailyLabyrinthFrame.nextStage(
                    emuManager.captureScreen(EMULATOR_NUMBER));
            if(RecognitionConsensus.agrees(nextFirst,nextSecond,25)){
                tapNear(nextSecond.getPoint(),3);
                sleepTask(370);
                logInfo("Labyrinth: Next selected next stage; checking result dismissal");
            }
            // In the live game Next advances the selected stage BEHIND the
            // Victory overlay. The overlay does not disappear by itself.
            // Tap the verified 'Tap anywhere to close' area exactly once,
            // then confirm the Cave map before touching its top-right X.
            RawImageData result=emuManager.captureScreen(EMULATOR_NUMBER);
            if(DailyLabyrinthFrame.victory(result)){
                tapNear(new PointData(335,1164),4);
                sleepTask(500);
            }
            if(waitForCave()==null){
                markWorkIncomplete("Labyrinth Victory earned but Cave stage map missing after result dismissal");
                logWarning("Labyrinth: Victory confirmed, Cave map not verified after dismiss; no second battle");
            }else{
                logInfo("Labyrinth: Victory dismissed; next Cave of Monsters stage selected");
            }
            reschedule(GameTimeUtils.dailyResetTime());
        }catch(dev.frostguard.engine.error.TaskPreemptedException
                |java.util.concurrent.CancellationException signal){throw signal;}
        catch(RuntimeException unexpected){
            logWarning("Labyrinth: failed safely: "+unexpected.getClass().getSimpleName()+" - "+unexpected.getMessage());
            defer("Unverified Labyrinth state: "+unexpected.getClass().getSimpleName(),60);
        }finally{
            // Exit ONLY from a positively recognized game screen. Never tap
            // Back repeatedly on unknown battle dialogs or purchase prompts.
            RawImageData frame=emuManager.captureScreen(EMULATOR_NUMBER);
            if(DailyLabyrinthFrame.defeat(frame) && !defeatDismissAttempted){
                defeatVerified=true;
                if(dismissDefeatToCave()){
                    frame=emuManager.captureScreen(EMULATOR_NUMBER);
                    logInfo("Labyrinth: left pending Defeat during safe final cleanup; no Retry");
                }
            }
            if(completedVerified && DailyLabyrinthFrame.victory(frame)){
                // A prior Next may have progressed the stage without closing
                // the overlay; this guarded exit does not start another fight.
                tapNear(new PointData(335,1164),4);
                sleepTask(430);
                frame=emuManager.captureScreen(EMULATOR_NUMBER);
            }
            if(DailyLabyrinthFrame.cave(frame)){
                tapNear(EXIT_CAVE,3);
                sleepTask(430);
            }else if(DailyLabyrinthFrame.overviewHeader(frame)){
                tapNear(BACK_OVERVIEW,3);
                sleepTask(430);
            }else if(DailyLabyrinthFrame.formation(frame)){
                tapNear(BACK_FORMATION,3);
                sleepTask(380);
                if(DailyLabyrinthFrame.cave(emuManager.captureScreen(EMULATOR_NUMBER))){
                    tapNear(EXIT_CAVE,3);sleepTask(420);
                }
            }
            if((completedVerified || defeatVerified)
                    && !DailyLabyrinthFrame.home(emuManager.captureScreen(EMULATOR_NUMBER))){
                markWorkIncomplete("Labyrinth result verified, but City Home not confirmed after safe exit");
                logWarning("Labyrinth " + (defeatVerified?"Defeat":"Victory")
                        + " was observed but Home is not confirmed (no repeated battle)");
            }else if(defeatVerified){
                logInfo("Labyrinth Defeat handled; City Home verified, Retry never pressed");
            }
        }
    }

    private RawImageData waitForCave(){
        for(int n=0;n<8;n++){
            checkPreemption();
            RawImageData f=emuManager.captureScreen(EMULATOR_NUMBER);
            if(DailyLabyrinthFrame.cave(f))return f;
            sleepTask(300);
        }
        return null;
    }
    private RawImageData waitForFormation(){
        for(int n=0;n<9;n++){
            checkPreemption();
            sleepTask(260);
            RawImageData f=emuManager.captureScreen(EMULATOR_NUMBER);
            if(DailyLabyrinthFrame.formation(f))return f;
        }
        return null;
    }
    private RawImageData waitForBattleOutcome(){
        // Both outcomes come from the same frame. The old routine waited for
        // Victory only and ignored actual Defeat for all 32 polling cycles.
        for(int n=0;n<32;n++){
            checkPreemption();
            sleepTask(n==0?750:300);
            RawImageData f=emuManager.captureScreen(EMULATOR_NUMBER);
            if(DailyLabyrinthFrame.defeat(f)||DailyLabyrinthFrame.victory(f))return f;
        }
        return null;
    }

    /**
     * The live Defeat overlay does not offer Return to City. A neutral tap at
     * y=810 closes it, whereas the separate lower buttons open Battle Report
     * or spend an attempt on Retry. Verified manually on real Serve-Emu:
     * silver Defeat+Retry -> neutral tap -> Cave map -> header X -> Home.
     * Requires a second positive frame before the only dismiss tap.
     */
    private boolean dismissDefeatToCave(){
        if(defeatDismissAttempted)return false;
        RawImageData first=emuManager.captureScreen(EMULATOR_NUMBER);
        if(!DailyLabyrinthFrame.defeat(first))return false;
        sleepTask(190);
        RawImageData second=emuManager.captureScreen(EMULATOR_NUMBER);
        if(!DailyLabyrinthFrame.defeat(second))return false;
        defeatDismissAttempted=true;
        tapNear(DEFEAT_NEUTRAL_DISMISS,3);
        sleepTask(350);
        return waitForCave()!=null;
    }
    private void defer(String reason,int retryMinutes){
        markWorkIncomplete("Labyrinth: "+reason);
        logWarning("Labyrinth: "+reason+"; "
                +TaskDiagnosticSnapshots.capture(emuManager,EMULATOR_NUMBER,"labyrinth","unverified"));
        reschedule(LocalDateTime.now().plusMinutes(retryMinutes));
    }
}
