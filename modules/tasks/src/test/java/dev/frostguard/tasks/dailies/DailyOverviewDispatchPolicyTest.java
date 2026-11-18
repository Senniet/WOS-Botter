package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TpDailyTaskEnum;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DailyOverviewDispatchPolicyTest {
    @Test void realDailySidebarStatusesEnqueueOnlyClearlyReadySafeTasks(){
        String observed="""
                Arena
                Arena
                Challenge attempts: 5/5
                Pet Adventure
                Pet Adventure
                Ready
                Alliance Contribution
                Alliance Contribution
                Can contribute 17/25
                Lighthouse
                Lighthouse Intel
                Intel Gain: 9
                """;
        assertEquals(Set.of(TpDailyTaskEnum.PET_ADVENTURE,
                TpDailyTaskEnum.ALLIANCE_TECH,TpDailyTaskEnum.INTEL),
                DailyOverviewDispatchPolicy.actionable(observed));
        assertFalse(DailyOverviewDispatchPolicy.actionable(observed).contains(TpDailyTaskEnum.ARENA),
                "Arena has a user-configured battle time and must never be rescheduled by the overview");
    }
    @Test void exhaustedOrMissingEntriesDoNotTriggerTasks(){
        String exhausted="""
                Arena Challenge attempts: 0/5
                Pet Adventure   Complete
                Alliance Contribution Can contribute 0/25
                Lighthouse Intel Gain: 0
                Online Rewards Collected
                """;
        assertTrue(DailyOverviewDispatchPolicy.actionable(exhausted).isEmpty());
        assertTrue(DailyOverviewDispatchPolicy.actionable("Page unavailable").isEmpty());
        assertTrue(DailyOverviewDispatchPolicy.actionable(null).isEmpty());
    }
    @Test void onlineRewardOnlyWhenExplicitReady(){
        assertEquals(Set.of(TpDailyTaskEnum.ONLINE_REWARDS),
                DailyOverviewDispatchPolicy.actionable("Online Rewards    Ready"));
        assertTrue(DailyOverviewDispatchPolicy.actionable("Online Rewards  01:25:00").isEmpty());
    }
}
