package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import java.time.LocalDateTime;

/**
 * Safe Daily Deals free chest scanner. Never touches paid pack rows, gems,
 * the purchase-all banner, or any unknown reward button. Other daily free
 * chest locations (Dawn Market, Artisan's Trove) are only visited after their
 * game-specific live pages can be positively identified; neither is present
 * in the observed Top-up Center tab carousel on this account.
 */
public class FreeDailyChestsRoutine extends DelayedTask {
    private static final PointData TABS_FROM=new PointData(630,143);
    private static final PointData TABS_TO=new PointData(145,143);
    private static final PointData BACK=new PointData(42,34);
    public FreeDailyChestsRoutine(AccountDescriptor profile,TpDailyTaskEnum task){super(profile,task);}
    @Override protected LaunchPoint getRequiredStartLocation(){return LaunchPoint.ANY;}

    @Override protected void execute(){
        Integer raw=profile.getConfig(ConfigurationKeyEnum.FREE_DAILY_CHESTS_INTERVAL_INT,Integer.class);
        int interval=raw==null?120:Math.clamp(raw,30,1440);
        boolean opened=false;
        boolean verified=false;
        try{
            RawImageData frame=emuManager.captureScreen(EMULATOR_NUMBER);
            if(TopUpDailyDealsFrame.topUp(frame))opened=true;
            else{
                navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME,this::checkPreemption);
                frame=emuManager.captureScreen(EMULATOR_NUMBER);
                ImageSearchResultData cart=TopUpDailyDealsFrame.cityCart(frame);
                if(!cart.isFound()){
                    retry("Verified City shopping cart not found");return;
                }
                tapNear(cart.getPoint(),3);
                sleepTask(380);
                opened=TopUpDailyDealsFrame.topUp(emuManager.captureScreen(EMULATOR_NUMBER));
                if(!opened){retry("Top-up Center header not verified after cart tap");return;}
            }
            // When the user enters the Top-up Center it can remember another
            // tab. Search only the horizontal 90..195 menu, never offer cards.
            for(int scan=0;scan<7;scan++){
                checkPreemption();
                frame=emuManager.captureScreen(EMULATOR_NUMBER);
                if(TopUpDailyDealsFrame.dailyDeals(frame)){verified=true;break;}
                ImageSearchResultData tab=TopUpDailyDealsFrame.dailyDealsTab(frame);
                if(tab.isFound()){
                    sleepTask(130);
                    var second=TopUpDailyDealsFrame.dailyDealsTab(
                            emuManager.captureScreen(EMULATOR_NUMBER));
                    if(second.isFound() && tab.getPoint().manhattanDistanceTo(second.getPoint())<25){
                        tapNear(second.getPoint(),4);
                        sleepTask(360);
                        verified=TopUpDailyDealsFrame.dailyDeals(emuManager.captureScreen(EMULATOR_NUMBER));
                        if(verified)break;
                    }
                }
                if(scan<6){swipe(TABS_FROM,TABS_TO,350);sleepTask(270);}
            }
            if(!verified){retry("Daily Deals tab not found/verified, no pack tapped");return;}
            frame=emuManager.captureScreen(EMULATOR_NUMBER);
            if(TopUpDailyDealsFrame.openedFreeChestOnCooldown(frame)
                    || TopUpDailyDealsFrame.cooldownText(frame)){
                logInfo("Free Daily Chests: Daily Deals free chest already collected; cooldown visible. "
                        +"Never touch paid pack rows. Dawn Market / Artisan's Trove absent from current carousel.");
            }else if(TopUpDailyDealsFrame.readyFreeChest(frame)){
                // The free chest is in the top-left header at (80,420).
                // Paid pack offers start *below* y=600; all other controls
                // including the US$4.99 discount banner are excluded.
                sleepTask(170);
                RawImageData confirmed=emuManager.captureScreen(EMULATOR_NUMBER);
                if(!TopUpDailyDealsFrame.readyFreeChest(confirmed)){
                    retry("Free chest was no longer ready on confirmation frame");return;
                }
                tapNear(new PointData(83,411),4);
                sleepTask(470);
                RawImageData after=emuManager.captureScreen(EMULATOR_NUMBER);
                if(TopUpDailyDealsFrame.openedFreeChestOnCooldown(after)){
                    logInfo("Free Daily Chests: free Daily Deals chest claimed; cooldown appeared");
                }else{
                    // Some clients show an additional Rewards receipt. No
                    // follow-up tap unless the reward screen can be verified.
                    logWarning("Free Daily Chests: tapped only verified free chest, "
                            +"but no cooldown confirmation appeared; no second click");
                    markWorkIncomplete("Free chest action did not verify claim completion");
                }
            }else{
                // OCR failures are not proof that a chest is free.
                logInfo("Free Daily Chests: ready free chest not yet positively identified; "
                        +"all paid purchase controls skipped");
            }
        }finally{
            if(opened){
                RawImageData current=emuManager.captureScreen(EMULATOR_NUMBER);
                if(TopUpDailyDealsFrame.topUp(current)){
                    tapNear(BACK,4);sleepTask(430);
                }
                if(!TopUpDailyDealsFrame.city(emuManager.captureScreen(EMULATOR_NUMBER))){
                    markWorkIncomplete("Free Daily Chests: City return not verified");
                    logWarning("Free Daily Chests: could not confirm City after closing Top-up Center");
                }else logInfo("Free Daily Chests: City Home verified");
            }
        }
        reschedule(LocalDateTime.now().plusMinutes(interval));
    }
    private void retry(String reason){
        markWorkIncomplete("Free Daily Chests: "+reason);
        logWarning("Free Daily Chests: "+reason+"; "+TaskDiagnosticSnapshots.capture(
                emuManager,EMULATOR_NUMBER,"free-daily-chests","unverified"));
        reschedule(LocalDateTime.now().plusMinutes(20));
    }
}
