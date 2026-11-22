package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.engine.nav.SidebarSection;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import java.time.LocalDateTime;

/**
 * Online Rewards are exposed as a Daily sidebar entry in the 2026 game.
 * Completed entries can be hidden by the user's original game setting.
 * When a verified row/page is present, only an unambiguously FREE claim is
 * eligible. No paid buttons, gems, items or unknown purchases are touched.
 */
public class OnlineRewardsRoutine extends DelayedTask {
    public OnlineRewardsRoutine(AccountDescriptor profile,TpDailyTaskEnum type){super(profile,type);}
    @Override protected LaunchPoint getRequiredStartLocation(){return LaunchPoint.ANY;}

    @Override protected void execute(){
        Integer raw=profile.getConfig(ConfigurationKeyEnum.ONLINE_REWARDS_CHECK_INTERVAL_INT,Integer.class);
        int minutes=raw==null?120:Math.clamp(raw,30,1440);
        boolean sidebar=false, entered=false;
        try{
            sidebar=navigationHelper.openSidebarSection(SidebarSection.DAILY);
            if(!sidebar){retry("Daily sidebar absent");return;}
            RawImageData frame=emuManager.captureScreen(EMULATOR_NUMBER);
            if(!DailyOverviewFrame.isDailyOverview(frame)){
                retry("Daily sidebar layout not recognized");return;
            }
            String observed=DailyOverviewFrame.readVisibleText(frame);
            if(!OnlineRewardsFrame.dailyOnlineReady(observed)){
                // User's "hide after completion" option can deliberately
                // remove Online Rewards when already collected.
                logInfo("Online Rewards: no visible ready entry in Daily sidebar; skip without taps");
                reschedule(LocalDateTime.now().plusMinutes(minutes));return;
            }
            int row=OnlineRewardsFrame.onlineRewardsRow(frame);
            if(row<0){retry("Online Rewards row not precisely located");return;}
            sleepTask(170);
            var second=emuManager.captureScreen(EMULATOR_NUMBER);
            int fresh=OnlineRewardsFrame.onlineRewardsRow(second);
            if(Math.abs(fresh-row)>24){retry("Online Rewards row shifted; no blind arrow tap");return;}
            // Current Daily sidebar action arrow centers on x=405. The exact
            // y is derived from the positively OCR-detected Online row.
            tapNear(new PointData(405,fresh),4);
            sleepTask(430);
            entered=true;
            frame=emuManager.captureScreen(EMULATOR_NUMBER);
            if(!OnlineRewardsFrame.onlinePage(frame)){
                retry("Online Rewards page header not verified");return;
            }
            var first=OnlineRewardsFrame.freeClaim(frame);
            if(!first.isFound()){
                logInfo("Online Rewards page verified, but no free Claim ready; no purchase attempted");
                reschedule(LocalDateTime.now().plusMinutes(minutes));return;
            }
            sleepTask(160);
            var next=OnlineRewardsFrame.freeClaim(emuManager.captureScreen(EMULATOR_NUMBER));
            if(!RecognitionConsensus.agrees(first,next,25)){
                retry("Free online Claim did not persist on two frames");return;
            }
            tapNear(next.getPoint(),3);
            sleepTask(430);
            var after=emuManager.captureScreen(EMULATOR_NUMBER);
            if(OnlineRewardsFrame.freeClaim(after).isFound()){
                retry("Online Rewards still claimable after one tap; no duplicate claim");return;
            }
            logInfo("Online Rewards: verified free Claim completed without paid actions");
            reschedule(LocalDateTime.now().plusMinutes(minutes));
        }finally{
            if(sidebar&&!entered){
                if(!navigationHelper.closeSidebar())markWorkIncomplete("Online Rewards: Daily sidebar did not close");
            }else if(entered){
                try{
                    navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME,this::checkPreemption);
                    logInfo("Online Rewards: Home restored after inspection");
                }catch(dev.frostguard.engine.error.HomeNotFoundException unexpected){
                    markWorkIncomplete("Online Rewards: Home not verified after leaving rewards");
                    logWarning("Online Rewards: Home missing after reward inspection");
                }
            }
        }
    }
    private void retry(String reason){
        markWorkIncomplete("Online Rewards: "+reason);
        logWarning("Online Rewards: "+reason+"; "+TaskDiagnosticSnapshots.capture(
                emuManager,EMULATOR_NUMBER,"online-rewards","unverified"));
        reschedule(LocalDateTime.now().plusMinutes(15));
    }
}
