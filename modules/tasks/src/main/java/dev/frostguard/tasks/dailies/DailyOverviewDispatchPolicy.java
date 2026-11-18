package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TpDailyTaskEnum;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative, read-only interpretation of the *visible* Daily sidebar.
 * A row label alone never grants permission to start a task: require a clear
 * remaining/ready status. Disabled routines, real due times and cooldowns are
 * enforced separately by SmartDailyOverviewRoutine and TaskQueue.
 *
 * Arena is intentionally informational: existing Arena routines can be
 * scheduled at specific times and the overview must not override them.
 */
final class DailyOverviewDispatchPolicy {
    private static final Pattern ALLIANCE = Pattern.compile(
            "ALLIANCE\\s+CONTRIBUTION.{0,75}?CAN\\s+CONTRIBUTE\\s+(\\d{1,2})\\s*/", Pattern.DOTALL);
    private static final Pattern LIGHTHOUSE = Pattern.compile(
            "LIGHTHOUSE.{0,90}?INTEL\\s+GAIN\\s*:?\\s*(\\d{1,3})", Pattern.DOTALL);
    private DailyOverviewDispatchPolicy() {}

    static Set<TpDailyTaskEnum> actionable(String ocr) {
        EnumSet<TpDailyTaskEnum> ready=EnumSet.noneOf(TpDailyTaskEnum.class);
        if(ocr==null || ocr.isBlank())return ready;
        String text=ocr.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9:/]+"," ")
                .replaceAll("\\s+"," ").trim();
        // Full rows are distinct. A generic 'Ready' on another line must
        // never activate a different task from a mere matching title.
        if(Pattern.compile("PET\\s+ADVENTURE\\s+PET\\s+ADVENTURE\\s+READY").matcher(text).find())
            ready.add(TpDailyTaskEnum.PET_ADVENTURE);
        Matcher contribution=ALLIANCE.matcher(text);
        if(contribution.find() && positive(contribution.group(1)))
            ready.add(TpDailyTaskEnum.ALLIANCE_TECH);
        Matcher intel=LIGHTHOUSE.matcher(text);
        if(intel.find() && positive(intel.group(1)))
            ready.add(TpDailyTaskEnum.INTEL);
        if(Pattern.compile("ONLINE\\s+REWARDS?\\s+(?:READY|CLAIM(?:ABLE)?|AVAILABLE)").matcher(text).find())
            ready.add(TpDailyTaskEnum.ONLINE_REWARDS);
        return ready;
    }

    private static boolean positive(String number){
        try{return Integer.parseInt(number)>0;}catch(NumberFormatException ignored){return false;}
    }
}
