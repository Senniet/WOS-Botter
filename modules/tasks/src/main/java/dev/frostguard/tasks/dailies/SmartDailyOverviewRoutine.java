package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.nav.SidebarSection;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.engine.schedule.TaskQueue;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fast overview -> targeted scheduling of already enabled safe routines.
 * Runs exclusively through TaskQueue (never taps inside another task). No
 * gems, paid actions, formation changes or arena scheduling override.
 */
public class SmartDailyOverviewRoutine extends DelayedTask {
    // Independent task execution after a scan needs a backoff even if the
    // in-game Daily status stays stale for an hour. In-memory per profile.
    private static final ConcurrentHashMap<String,LocalDateTime> LAST_DISPATCH=
            new ConcurrentHashMap<>();
    private static final int COOLDOWN_MINUTES=85;

    public SmartDailyOverviewRoutine(AccountDescriptor profile,TpDailyTaskEnum type){super(profile,type);}
    @Override protected LaunchPoint getRequiredStartLocation(){return LaunchPoint.ANY;}

    @Override protected void execute(){
        Integer raw=profile.getConfig(ConfigurationKeyEnum.SMART_DAILY_OVERVIEW_INTERVAL_INT,Integer.class);
        int minutes=raw==null?90:Math.clamp(raw,30,1440);
        boolean opened=false;
        boolean closed=false;
        Set<TpDailyTaskEnum> ready=Set.of();
        try{
            opened=navigationHelper.openSidebarSection(SidebarSection.DAILY);
            if(!opened){retry("Daily sidebar not verified");return;}
            RawImageData frame=emuManager.captureScreen(EMULATOR_NUMBER);
            if(!DailyOverviewFrame.isDailyOverview(frame)){
                retry("Daily sidebar does not match current layout");return;
            }
            // Each visible row needs BOTH an identified icon and an actual
            // green readiness status; this saves the four costly tiny OCR
            // checks and avoids false negatives on the original game font.
            ready=DailyReadyStatusFrame.actionable(frame);
            // The sidebar can animate through a status transition. Confirm
            // on a second screenshot and dispatch only the intersection.
            sleepTask(170);
            Set<TpDailyTaskEnum> fresh=DailyReadyStatusFrame.actionable(
                    emuManager.captureScreen(EMULATOR_NUMBER));
            ready.retainAll(fresh);
            logInfo("Smart Daily Overview: verified green/anchored tasks on two frames="+ready);
            // Online Rewards has no captured ready icon on this account.
            // It is still polled by its independent free-only routine.
            // Before any dispatch the sidebar must close to the real Home.
            // Leave the UI under this task's ownership until that is proven.
        } finally {
            if(opened){
                closed=navigationHelper.closeSidebar();
                if(!closed){
                    markWorkIncomplete("Smart Daily Overview: sidebar failed to close");
                    logWarning("Daily sidebar left open; no dispatch permitted");
                }
            }
        }
        if(!closed){
            reschedule(LocalDateTime.now().plusMinutes(15));
            return;
        }
        dispatchReady(ready);
        reschedule(LocalDateTime.now().plusMinutes(minutes));
    }

    private void dispatchReady(Set<TpDailyTaskEnum> ready){
        TaskQueue queue=scheduleService.getCoordinator().getQueue(profile.getId());
        if(queue==null){logWarning("Smart Daily Overview: no live scheduler queue");return;}
        LocalDateTime now=LocalDateTime.now();
        int scheduled=0;
        for(TpDailyTaskEnum type:ready){
            checkPreemption();
            ConfigurationKeyEnum key=type.getConfigKey();
            if(key==null || !Boolean.TRUE.equals(profile.getConfig(key,Boolean.class))){
                logInfo("Smart Daily Overview: "+type+" disabled, respecting current profile");
                continue;
            }
            // The actual task takes over UI only AFTER we have exited Home.
            String cooldownKey=profile.getId()+":"+type.name();
            LocalDateTime prev=LAST_DISPATCH.get(cooldownKey);
            if(prev!=null && now.isBefore(prev.plusMinutes(COOLDOWN_MINUTES)))continue;
            if(queue.isTaskScheduledSoon(type,45*60))continue;
            if(queue.scheduleOrRescheduleQueuedTask(type,profile,now.plusSeconds(12))){
                LAST_DISPATCH.put(cooldownKey,now);
                scheduled++;
                logInfo("Smart Daily Overview: queued verified ready "+type+" (enabled and not already due soon)");
            }
        }
        logInfo("Smart Daily Overview: queued "+scheduled+" task(s); rest of schedule preserved");
    }

    private void retry(String reason){
        markWorkIncomplete("Smart Daily Overview: "+reason);
        logWarning("Smart Daily Overview: "+reason+"; "+TaskDiagnosticSnapshots.capture(
                emuManager,EMULATOR_NUMBER,"smart-daily","sidebar-unverified"));
        reschedule(LocalDateTime.now().plusMinutes(15));
    }
}
