package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import java.time.LocalDateTime;

/**
 * Claim already EARNED growth + chapter missions, never start their upgrade,
 * attack, spend, gem, level-up or training 'Go' actions. Daily Missions is
 * completely separate and cannot be claimed by this routine.
 */
public final class GrowthMissionRewardsRoutine extends DelayedTask {
    private static final PointData MISSION_HUD=new PointData(50,1050);
    private static final PointData GROWTH_TAB=new PointData(355,1134);
    private static final PointData CHAPTER_TAB=new PointData(126,1134);
    private static final PointData CLOSE=new PointData(683,121);
    private static final PointData SCROLL_START=new PointData(327,930);
    private static final PointData SCROLL_END=new PointData(328,510);
    private static final int MAX_GROWTH_CLAIMS=10,MAX_SCROLLS=4;

    public GrowthMissionRewardsRoutine(AccountDescriptor profile,TpDailyTaskEnum task){super(profile,task);}
    @Override protected LaunchPoint getRequiredStartLocation(){return LaunchPoint.ANY;}
    @Override protected void execute(){runExclusiveUiTransaction(this::executeSafely);}

    private void executeSafely(){
        Integer setting=profile.getConfig(ConfigurationKeyEnum.GROWTH_REWARDS_CHECK_INTERVAL_INT,Integer.class);
        int interval=setting==null?240:Math.clamp(setting,45,1440);
        boolean modalOpened=false;
        try{
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME,this::checkPreemption);
            if(!GrowthMissionRewardsFrame.city(emuManager.captureScreen(EMULATOR_NUMBER))){
                defer("City HUD not verified",25);return;
            }
            tapNear(MISSION_HUD,4);
            sleepTask(420);
            RawImageData opened=emuManager.captureScreen(EMULATOR_NUMBER);
            modalOpened=GrowthMissionRewardsFrame.missionScreen(opened);
            if(!modalOpened){defer("Missions popup not positively verified",20);return;}
            if(!GrowthMissionRewardsFrame.growth(opened)){
                tapNear(GROWTH_TAB,4);
                sleepTask(380);
            }
            if(!GrowthMissionRewardsFrame.growth(emuManager.captureScreen(EMULATOR_NUMBER))){
                defer("Growth Missions header not verified; no claim",20);return;
            }
            int growthClaims=0;
            for(int page=0;page<MAX_SCROLLS&&growthClaims<MAX_GROWTH_CLAIMS;page++){
                for(int claim=0;claim<MAX_GROWTH_CLAIMS&&growthClaims<MAX_GROWTH_CLAIMS;claim++){
                    checkPreemption();
                    var before=GrowthMissionRewardsFrame.growthClaim(
                            emuManager.captureScreen(EMULATOR_NUMBER));
                    if(!before.isFound())break;
                    sleepTask(170);
                    var fresh=GrowthMissionRewardsFrame.growthClaim(
                            emuManager.captureScreen(EMULATOR_NUMBER));
                    if(!RecognitionConsensus.agrees(before,fresh,24)){
                        defer("Growth Claim moved between screenshots; no blind tap",20);return;
                    }
                    tapNear(fresh.getPoint(),5);
                    sleepTask(450);
                    RawImageData after=emuManager.captureScreen(EMULATOR_NUMBER);
                    if(!GrowthMissionRewardsFrame.growth(after)){
                        defer("Growth rewards page changed unexpectedly after a single Claim",20);return;
                    }
                    var still=GrowthMissionRewardsFrame.growthClaim(after);
                    if(still.isFound()
                            && still.getPoint().manhattanDistanceTo(fresh.getPoint())<30){
                        defer("Growth Claim not consumed after one tap; refusing duplicate",20);return;
                    }
                    growthClaims++;
                    logInfo("Growth Missions: earned free claim #"+growthClaims+" confirmed consumed");
                }
                if(page+1<MAX_SCROLLS){
                    swipe(SCROLL_START,SCROLL_END,360);
                    sleepTask(270);
                    if(!GrowthMissionRewardsFrame.growth(
                            emuManager.captureScreen(EMULATOR_NUMBER))){
                        defer("Growth Missions scrolled to an unexpected screen",20);return;
                    }
                }
            }
            // Chapter uses a different, bottom-only aggregate reward claim.
            // It does not complete the unfinished tasks with blue Go labels.
            tapNear(CHAPTER_TAB,4);
            sleepTask(350);
            var chapter=emuManager.captureScreen(EMULATOR_NUMBER);
            if(!GrowthMissionRewardsFrame.chapter(chapter)){
                defer("Chapter Missions header did not confirm after tab switch",20);return;
            }
            var first=GrowthMissionRewardsFrame.chapterRewardClaim(chapter);
            int chapterClaims=0;
            if(first.isFound()){
                sleepTask(160);
                var second=GrowthMissionRewardsFrame.chapterRewardClaim(
                        emuManager.captureScreen(EMULATOR_NUMBER));
                if(!RecognitionConsensus.agrees(first,second,20)){
                    defer("Chapter reward Claim changed before tap",20);return;
                }
                tapNear(second.getPoint(),5);
                sleepTask(520);
                var after=emuManager.captureScreen(EMULATOR_NUMBER);
                if(!GrowthMissionRewardsFrame.chapter(after)
                        || GrowthMissionRewardsFrame.chapterRewardClaim(after).isFound()){
                    defer("Chapter reward tapped but not positively consumed; no duplicate",20);return;
                }
                chapterClaims=1;
            }
            logInfo("Growth & Chapter rewards checked: "+growthClaims
                    +" earned Growth claims and "+chapterClaims+" earned Chapter claims");
            reschedule(LocalDateTime.now().plusMinutes(interval));
        }finally{
            if(modalOpened){
                RawImageData before=emuManager.captureScreen(EMULATOR_NUMBER);
                if(GrowthMissionRewardsFrame.missionScreen(before)){
                    tapNear(CLOSE,4);
                    sleepTask(400);
                }
                boolean home=GrowthMissionRewardsFrame.city(
                        emuManager.captureScreen(EMULATOR_NUMBER));
                if(!home){
                    markWorkIncomplete("Growth Mission Rewards: City Home not verified after closing popup");
                    logWarning("Growth Mission Rewards: City Home not verified after closing popup");
                }else logInfo("Growth Mission Rewards: City Home verified");
            }
        }
    }
    private void defer(String message,int minutes){
        markWorkIncomplete("Growth Mission Rewards: "+message);
        logWarning("Growth Mission Rewards: "+message+"; "
                +TaskDiagnosticSnapshots.capture(emuManager,EMULATOR_NUMBER,"growth-rewards","unverified"));
        reschedule(LocalDateTime.now().plusMinutes(minutes));
    }
}
