package dev.frostguard.tasks.pets;

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
import dev.frostguard.vision.convert.GameTimeUtils;
import java.time.LocalDateTime;

/**
 * Care for up to 3 alliance members' Trees of Life from the WORLD MAP.
 *
 * No player-owned Daybreak Island is necessary: the blue handshake balloon
 * above alliance cities is itself the route to the ally island. The previous
 * implementation opened the user's *own* Life Essence menu, which is locked
 * for accounts that have no island and caused hourly false retry loops.
 *
 * Verified on original Android 16 Serve-Emu 720x1280 (18 Nov 2026): World
 * handshake -> 'Visit this Chief's island?' -> Confirm -> ally tree handshake
 * -> +200 Life Essence + care counter 3/3 -> 2/3 -> World.
 */
public final class LifeEssenceCaringRoutine extends DelayedTask {
    private static final PointData CONFIRM_VISIT = new PointData(515,789);
    private static final PointData RETURN_TO_WORLD = new PointData(41,29);
    private static final int MAX_DAILY_CARES = 3;

    public LifeEssenceCaringRoutine(AccountDescriptor profile,TpDailyTaskEnum type){super(profile,type);}

    @Override protected LaunchPoint getRequiredStartLocation(){return LaunchPoint.WORLD;}

    @Override protected void execute(){runExclusiveUiTransaction(this::collectFromAllianceWorld);}

    private void collectFromAllianceWorld(){
        Integer delay=profile.getConfig(ConfigurationKeyEnum.ALLIANCE_LIFE_ESSENCE_OFFSET_INT,Integer.class);
        int retryMinutes=delay!=null&&delay>0?Math.clamp(delay,10,1440):60;
        int confirmed=0;
        try {
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.WORLD,this::checkPreemption);
            if(!AllianceTreeCareFrame.world(emuManager.captureScreen(EMULATOR_NUMBER))){
                incomplete("World HUD not confirmed before alliance tree scan",retryMinutes);
                return;
            }
            logInfo("Life Essence · Alliance: searching for World-map Tree of Life caring icons; "
                    +"no personal island needed");
            for(int slot=0;slot<MAX_DAILY_CARES;slot++){
                checkPreemption();
                RawImageData map=emuManager.captureScreen(EMULATOR_NUMBER);
                ImageSearchResultData first=AllianceTreeCareFrame.worldCareBadge(map);
                if(!first.isFound()){
                    if(confirmed==0){
                        logInfo("Life Essence Caring: no blue alliance tree care badge visible on World");
                        incomplete("No positively identified alliance tree icon in current World viewport",retryMinutes);
                    }else{
                        logInfo("Life Essence Caring: " +confirmed+" verified care(s), no additional visible icon");
                        reschedule(LocalDateTime.now().plusMinutes(retryMinutes));
                    }
                    return;
                }
                sleepTask(170);
                ImageSearchResultData second=AllianceTreeCareFrame.worldCareBadge(
                        emuManager.captureScreen(EMULATOR_NUMBER));
                if(!RecognitionConsensus.agrees(first,second,18)){
                    incomplete("World caring badge moved between screenshots; no tap",15);
                    return;
                }
                logInfo("Life Essence Caring: opening ally badge at "+second.getPoint());
                tapNear(second.getPoint(),4);
                sleepTask(310);
                if(!AllianceTreeCareFrame.visitQuestion(
                        emuManager.captureScreen(EMULATOR_NUMBER))){
                    incomplete("Expected Visit this Chief's island? confirmation absent",15);
                    return;
                }
                sleepTask(125);
                if(!AllianceTreeCareFrame.visitQuestion(
                        emuManager.captureScreen(EMULATOR_NUMBER))){
                    incomplete("Visit confirmation changed on second frame",15);
                    return;
                }
                // Only this specifically recognized confirmation authorizes
                // the one stable blue Confirm action, never any paid prompt.
                tapNear(CONFIRM_VISIT,4);
                if(!waitForIsland()){
                    incomplete("Island did not open after verified Visit confirmation",20);
                    return;
                }
                try {
                    RawImageData ready=emuManager.captureScreen(EMULATOR_NUMBER);
                    ImageSearchResultData care=AllianceTreeCareFrame.islandCareBadge(ready);
                    // The bottom-right island HUD appears before the animated
                    // central tree and its care balloon finish loading.
                    // Wait for the *same positive badge*, never guess its
                    // position from the static island template.
                    for(int delayed=0;!care.isFound()&&delayed<7;delayed++){
                        checkPreemption();
                        sleepTask(380);
                        ready=emuManager.captureScreen(EMULATOR_NUMBER);
                        if(!AllianceTreeCareFrame.island(ready))break;
                        care=AllianceTreeCareFrame.islandCareBadge(ready);
                    }
                    if(!care.isFound()){
                        // No available Tree badge: either attempts exhausted
                        // or this ally cannot accept care; do not tap the tree.
                        logInfo("Life Essence Caring: ally island has no available blue caring badge");
                        if(confirmed==0)incomplete("Visited ally has no claimable caring badge",retryMinutes);
                        else reschedule(LocalDateTime.now().plusMinutes(retryMinutes));
                        return;
                    }
                    int before=AllianceTreeCareFrame.availableCareAttempts(ready);
                    if(before==0){
                        logInfo("Life Essence Caring: daily 0/3 reached; next game reset");
                        reschedule(GameTimeUtils.dailyResetTime());
                        return;
                    }
                    if(before<0){
                        logInfo("Life Essence Caring: counter font not recognized; using two-frame "
                                +"blue Caring badge confirmation instead of guessing a number");
                    }
                    sleepTask(160);
                    var again=AllianceTreeCareFrame.islandCareBadge(
                            emuManager.captureScreen(EMULATOR_NUMBER));
                    if(!RecognitionConsensus.agrees(care,again,22)){
                        incomplete("Caring badge moved before tap",15);
                        return;
                    }
                    logInfo("Life Essence Caring: verified Tree care tap, attempts before="+before);
                    tapNear(again.getPoint(),4);
                    // The daily count is a tiny animated glyph and varies by
                    // island. It is supplementary evidence, not the sole
                    // success criterion. The positively identified blue
                    // caring balloon must disappear on TWO fresh island
                    // frames. This is the same transition observed during
                    // a real +200 Life Essence claim.
                    boolean consumed=false;
                    boolean used=false;
                    int remaining=-1;
                    for(int post=0;post<7;post++){
                        checkPreemption();
                        sleepTask(post==0?340:210);
                        RawImageData after=emuManager.captureScreen(EMULATOR_NUMBER);
                        if(!AllianceTreeCareFrame.island(after))break;
                        used|=AllianceTreeCareFrame.helpedToast(after);
                        int newCount=AllianceTreeCareFrame.availableCareAttempts(after);
                        if(newCount>=0)remaining=newCount;
                        if(!AllianceTreeCareFrame.islandCareBadge(after).isFound()){
                            sleepTask(170);
                            RawImageData stable=emuManager.captureScreen(EMULATOR_NUMBER);
                            if(AllianceTreeCareFrame.island(stable)
                                    && !AllianceTreeCareFrame.islandCareBadge(stable).isFound()){
                                int verified=AllianceTreeCareFrame.availableCareAttempts(stable);
                                if(verified>=0)remaining=verified;
                                consumed=true;
                                break;
                            }
                        }
                    }
                    if(!consumed){
                        incomplete("Care click did not clear verified blue badge on two island screenshots; no retry tap",20);
                        return;
                    }
                    if(!used && (before<0||remaining<0))
                        logInfo("Life Essence Caring: care badge consumed on two stable frames; "
                                +"daily numeric overlay could not be read");
                    confirmed++;
                    logInfo("Life Essence Caring: earned Tree of Life essence from alliance member "
                            +"("+confirmed+" verified today in this run, attempts "+before+"→"+remaining+")");
                    if(remaining==0 || confirmed>=MAX_DAILY_CARES){
                        logInfo("Life Essence Caring: all three care actions complete; next daily reset");
                        reschedule(GameTimeUtils.dailyResetTime());
                        return;
                    }
                }finally{
                    // Every island visit must end at World, even if the care
                    // button isn't found or the task is preempted.
                    if(!returnToWorld()){
                        markWorkIncomplete("Life Essence Caring: World return not verified");
                        logWarning("Life Essence Caring: World return not verified; stop further taps");
                    }
                }
                if(!AllianceTreeCareFrame.world(emuManager.captureScreen(EMULATOR_NUMBER))){
                    incomplete("World HUD not restored after successful ally care",20);
                    return;
                }
            }
            reschedule(GameTimeUtils.dailyResetTime());
        }finally{
            if(AllianceTreeCareFrame.island(emuManager.captureScreen(EMULATOR_NUMBER))){
                if(!returnToWorld())markWorkIncomplete("Life Essence Caring: island remained open after task");
            }
        }
    }

    private boolean waitForIsland(){
        for(int poll=0;poll<9;poll++){
            checkPreemption();
            sleepTask(poll==0?600:280);
            if(AllianceTreeCareFrame.island(emuManager.captureScreen(EMULATOR_NUMBER)))return true;
        }
        return false;
    }

    private boolean returnToWorld(){
        RawImageData current=emuManager.captureScreen(EMULATOR_NUMBER);
        if(AllianceTreeCareFrame.world(current))return true;
        if(!AllianceTreeCareFrame.island(current))return false;
        tapNear(RETURN_TO_WORLD,4);
        for(int pass=0;pass<8;pass++){
            checkPreemption();
            sleepTask(pass==0?640:280);
            if(AllianceTreeCareFrame.world(emuManager.captureScreen(EMULATOR_NUMBER)))return true;
        }
        return false;
    }

    private void incomplete(String reason,int retryMinutes){
        markWorkIncomplete("Life Essence Caring: "+reason);
        logWarning("Life Essence Caring: "+reason+"; "
                +TaskDiagnosticSnapshots.capture(emuManager,EMULATOR_NUMBER,"life-essence-alliance","unverified"));
        reschedule(LocalDateTime.now().plusMinutes(retryMinutes));
    }
}
