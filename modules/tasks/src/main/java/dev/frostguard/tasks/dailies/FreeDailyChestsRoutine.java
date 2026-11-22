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
    // The Top-up Center remembers the LAST selected tab across visits. A
    // left-only scan gets stuck when the saved tab is already at the far
    // right of the carousel; Daily Deals then lies to the LEFT. Never try
    // to solve that by tapping an unverified paid pack.
    private static final int TABS_PER_DIRECTION=7;
    private static final PointData BACK=new PointData(42,34);
    public FreeDailyChestsRoutine(AccountDescriptor profile,TpDailyTaskEnum task){super(profile,task);}
    @Override protected LaunchPoint getRequiredStartLocation(){return LaunchPoint.ANY;}

    @Override protected void execute(){
        Integer raw=profile.getConfig(ConfigurationKeyEnum.FREE_DAILY_CHESTS_INTERVAL_INT,Integer.class);
        int interval=raw==null?360:Math.clamp(raw,30,1440);
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
            // Safe reversible search: Daily Deals is visible after two left
            // swipes from the first tab in the Nov 19 client. Previously the
            // scanner moved LEFT ONLY: after one missed run the carousel was
            // already at its right end, so every later run also missed.
            // Reverse direction after the first sweep; never touch pack rows.
            verified=findVerifiedDailyDealsTab();
            if(!verified){retry("Daily Deals not found after left/right tab scan; no paid pack tapped");return;}
            // The tab title appears before the animated Daily Deals chest.
            // Poll the same known FREE region briefly instead of deciding the
            // whole page has no free chest on the first transitioning frame.
            frame=waitForDailyDealsChestState();
            if(frame==null){
                retry("Daily Deals opened, but neither Free chest nor cooldown confirmed");
                return;
            }
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
                boolean claimed=false;
                for(int receiptPoll=0;receiptPoll<10;receiptPoll++){
                    checkPreemption();
                    sleepTask(receiptPoll==0?480:220);
                    RawImageData after=emuManager.captureScreen(EMULATOR_NUMBER);
                    if(TopUpDailyDealsFrame.claimedReceipt(after)){
                        logInfo("Free Daily Chests: actual Claimed reward receipt verified; closing its safe footer");
                        tapNear(new PointData(351,1188),3);
                        sleepTask(460);
                        RawImageData returned=emuManager.captureScreen(EMULATOR_NUMBER);
                        if(TopUpDailyDealsFrame.topUp(returned)
                                && !TopUpDailyDealsFrame.claimedReceipt(returned)){
                            claimed=true;
                            logInfo("Free Daily Chests: confirmed free reward received and receipt dismissed");
                        }
                        break;
                    }
                    if(TopUpDailyDealsFrame.openedFreeChestOnCooldown(after)
                            || TopUpDailyDealsFrame.cooldownText(after)){
                        claimed=true;
                        logInfo("Free Daily Chests: free chest claimed; cooldown confirmed");
                        break;
                    }
                }
                if(!claimed){
                    logWarning("Free Daily Chests: verified Free chest tapped but no stable receipt/cooldown; no repeat tap");
                    markWorkIncomplete("Free chest action not yet confirmed after one tap");
                }
            }else{
                // OCR failures are not proof that a chest is free.
                retry("Free chest state not positively identified; all paid purchase controls skipped");
                return;
            }
        }finally{
            if(opened){
                RawImageData current=emuManager.captureScreen(EMULATOR_NUMBER);
                if(TopUpDailyDealsFrame.claimedReceipt(current)){
                    // Positive receipt is the only safe authorization to
                    // dismiss this full-screen overlay. No blind Back.
                    tapNear(new PointData(351,1188),3);
                    sleepTask(430);
                    current=emuManager.captureScreen(EMULATOR_NUMBER);
                }
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
    /** The free chest artwork glows/animates after the tab title appears. */
    private RawImageData waitForDailyDealsChestState(){
        for(int attempt=0;attempt<8;attempt++){
            checkPreemption();
            RawImageData frame=emuManager.captureScreen(EMULATOR_NUMBER);
            if(TopUpDailyDealsFrame.dailyDeals(frame) &&
                    (TopUpDailyDealsFrame.openedFreeChestOnCooldown(frame)
                     || TopUpDailyDealsFrame.cooldownText(frame)
                     || TopUpDailyDealsFrame.readyFreeChest(frame)))return frame;
            if(attempt<7)sleepTask(220);
        }
        return null;
    }

    /** Search the verified horizontal tab strip in BOTH directions. */
    private boolean findVerifiedDailyDealsTab(){
        for(int pass=0;pass<2;pass++){
            PointData from=pass==0?TABS_FROM:TABS_TO;
            PointData to=pass==0?TABS_TO:TABS_FROM;
            for(int scan=0;scan<TABS_PER_DIRECTION;scan++){
                checkPreemption();
                RawImageData frame=emuManager.captureScreen(EMULATOR_NUMBER);
                if(TopUpDailyDealsFrame.dailyDeals(frame))return true;
                if(!TopUpDailyDealsFrame.topUp(frame)){
                    logWarning("Free Daily Chests: Top-up Center vanished during carousel search");
                    return false;
                }
                var tab=TopUpDailyDealsFrame.dailyDealsTab(frame);
                if(tab.isFound()){
                    // The tab carousel continues gliding for a while AFTER
                    // swipe(...) returns. Real video frames captured during
                    // the Nov 19 bot scan had 99.2% Daily Deals matches in
                    // frames 13 and 17, but their X positions shifted 39px.
                    // Settle before checking a second fresh frame; never tap
                    // a tab while the moving sprite does not agree.
                    sleepTask(340);
                    var second=TopUpDailyDealsFrame.dailyDealsTab(
                            emuManager.captureScreen(EMULATOR_NUMBER));
                    if(!second.isFound() || tab.getPoint().manhattanDistanceTo(second.getPoint())>=25){
                        sleepTask(310);
                        tab=TopUpDailyDealsFrame.dailyDealsTab(
                                emuManager.captureScreen(EMULATOR_NUMBER));
                        if(tab.isFound()){
                            sleepTask(190);
                            second=TopUpDailyDealsFrame.dailyDealsTab(
                                    emuManager.captureScreen(EMULATOR_NUMBER));
                        }
                    }
                    if(second.isFound() && tab.isFound()
                            && tab.getPoint().manhattanDistanceTo(second.getPoint())<25){
                        tapNear(second.getPoint(),3);
                        sleepTask(580);
                        RawImageData daily=emuManager.captureScreen(EMULATOR_NUMBER);
                        if(TopUpDailyDealsFrame.dailyDeals(daily)){
                            logInfo("Free Daily Chests: Daily Deals tab verified (pass="
                                    +(pass==0?"left":"right")+", swipe="+scan+")");
                            return true;
                        }
                        logWarning("Free Daily Chests: tab tap not confirmed after carousel settled; scanning safely");
                    }
                }
                if(scan<TABS_PER_DIRECTION-1){
                    swipe(from,to,350);
                    sleepTask(590);
                }
            }
        }
        return false;
    }

    private void retry(String reason){
        markWorkIncomplete("Free Daily Chests: "+reason);
        logWarning("Free Daily Chests: "+reason+"; "+TaskDiagnosticSnapshots.capture(
                emuManager,EMULATOR_NUMBER,"free-daily-chests","unverified"));
        reschedule(LocalDateTime.now().plusMinutes(20));
    }
}
