package dev.frostguard.tasks.events;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.time.LocalDateTime;

/**
 * Collect earned FREE personal event milestone rewards. The first supported
 * target is live-verified King of Icefield; future events require their own
 * screen classifiers before being added here. No paid event shop is touched.
 */
public final class EventMilestoneRewardsRoutine extends DelayedTask {
    private static final PointData BACK = new PointData(42,37);
    private static final PointData SCROLL_FROM=new PointData(334,1070);
    private static final PointData SCROLL_TO=new PointData(334,710);
    private static final int MAX_CLAIMS=12,MAX_PAGES=4;

    public EventMilestoneRewardsRoutine(AccountDescriptor profile,TpDailyTaskEnum task){super(profile,task);}
    @Override protected LaunchPoint getRequiredStartLocation(){return LaunchPoint.ANY;}
    @Override protected void execute(){runExclusiveUiTransaction(this::executeSafely);}

    private void executeSafely(){
        Integer setting=profile.getConfig(ConfigurationKeyEnum.EVENT_MILESTONE_CHECK_INTERVAL_INT,Integer.class);
        int interval=setting==null?240:Math.clamp(setting,45,1440);
        boolean enteredEvents=false;
        try{
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME,this::checkPreemption);
            RawImageData home=emuManager.captureScreen(EMULATOR_NUMBER);
            var evt=OpenCvPatternLocator.locatePattern(home,
                    TemplatesEnum.HOME_EVENTS_BUTTON.getTemplate(),new PointData(575,78),
                    new PointData(719,255),87);
            if(!evt.isFound()){
                defer("Events home shortcut not positively identified",25);return;
            }
            tapNear(evt.getPoint(),3);
            sleepTask(570);
            enteredEvents=KingIcefieldMilestoneFrame.eventsPanel(
                    emuManager.captureScreen(EMULATOR_NUMBER));
            if(!enteredEvents){defer("Events header absent after shortcut",20);return;}
            if(!KingIcefieldMilestoneFrame.kingPage(emuManager.captureScreen(EMULATOR_NUMBER))){
                var tab=KingIcefieldMilestoneFrame.kingTab(emuManager.captureScreen(EMULATOR_NUMBER));
                if(!tab.isFound()){
                    logInfo("Event Milestone Rewards: King of Icefield tab is not active/visible; skip");
                    reschedule(LocalDateTime.now().plusMinutes(interval));return;
                }
                sleepTask(160);
                var second=KingIcefieldMilestoneFrame.kingTab(emuManager.captureScreen(EMULATOR_NUMBER));
                if(!RecognitionConsensus.agrees(tab,second,20)){
                    defer("King of Icefield tab shifted before tap",20);return;
                }
                tapNear(second.getPoint(),4);
                sleepTask(430);
            }
            if(!KingIcefieldMilestoneFrame.kingPage(emuManager.captureScreen(EMULATOR_NUMBER))){
                defer("King of Icefield header not confirmed; no claim",30);return;
            }
            int claims=0;
            for(int page=0;page<MAX_PAGES&&claims<MAX_CLAIMS;page++){
                checkPreemption();
                for(int row=0;row<MAX_CLAIMS&&claims<MAX_CLAIMS;row++){
                    RawImageData firstFrame=emuManager.captureScreen(EMULATOR_NUMBER);
                    var first=KingIcefieldMilestoneFrame.unclaimedMilestone(firstFrame);
                    if(!first.isFound())break;
                    sleepTask(165);
                    var second=KingIcefieldMilestoneFrame.unclaimedMilestone(
                            emuManager.captureScreen(EMULATOR_NUMBER));
                    if(!RecognitionConsensus.agrees(first,second,24)){
                        defer("King milestone Claim changed before tap",20);return;
                    }
                    PointData pos=second.getPoint();
                    tapNear(pos,5);
                    sleepTask(480);
                    RawImageData after=emuManager.captureScreen(EMULATOR_NUMBER);
                    if(!KingIcefieldMilestoneFrame.claimedAt(after,pos)){
                        defer("King milestone Claim was tapped once but claimed checkmark is unverified; "
                                +"no duplicate tap",20);
                        return;
                    }
                    claims++;
                    logInfo("King of Icefield: earned personal milestone #"+claims
                            +" claimed and checked; never used gems");
                }
                // Small, bounded scroll of the King stage reward list, not
                // the stage tabs. New page may expose the next earned tier.
                if(page+1<MAX_PAGES){
                    swipe(SCROLL_FROM,SCROLL_TO,345);
                    sleepTask(290);
                    if(!KingIcefieldMilestoneFrame.kingPage(
                            emuManager.captureScreen(EMULATOR_NUMBER))){
                        defer("King event changed during reward scan",20);return;
                    }
                }
            }
            logInfo("Event Milestone Rewards: King of Icefield page inspected; "
                    +claims+" verified free reward claim(s)");
            reschedule(LocalDateTime.now().plusMinutes(interval));
        }finally{
            if(enteredEvents){
                RawImageData before=emuManager.captureScreen(EMULATOR_NUMBER);
                if(KingIcefieldMilestoneFrame.eventsPanel(before)){
                    tapNear(BACK,4);
                    sleepTask(410);
                }
                boolean city=KingIcefieldMilestoneFrame.eventClosed(
                        emuManager.captureScreen(EMULATOR_NUMBER));
                if(!city){
                    markWorkIncomplete("Event Milestone Rewards: City after Events not verified");
                    logWarning("Event Milestone Rewards: City after Events not verified");
                }else logInfo("Event Milestone Rewards: City Home verified");
            }
        }
    }
    private void defer(String message,int minutes){
        markWorkIncomplete("Event Milestone Rewards: "+message);
        logWarning("Event Milestone Rewards: "+message+"; "
                +TaskDiagnosticSnapshots.capture(emuManager,EMULATOR_NUMBER,"event-milestones","unverified"));
        reschedule(LocalDateTime.now().plusMinutes(minutes));
    }
}
