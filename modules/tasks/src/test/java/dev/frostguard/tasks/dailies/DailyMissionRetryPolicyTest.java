package dev.frostguard.tasks.dailies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import org.junit.jupiter.api.Test;

class DailyMissionRetryPolicyTest {

    @Test
    void unverifiedDailyMissionRetriesInFiveMinutesInsteadOfWaitingForReset() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 13, 12, 0);

        assertEquals(now.plusMinutes(5), DailyMissionRoutine.nextUnverifiedRetry(now));
    }

    @Test
    void unverifiedDailyMissionRetryAlsoWorksNearReset() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 13, 23, 59);

        assertEquals(LocalDateTime.of(2026, 11, 14, 0, 4),
                DailyMissionRoutine.nextUnverifiedRetry(now));
    }
    @Test
    void noClaimAvailableDoesNotIncrementClaimStatistics() {
        assertFalse(DailyMissionRoutine.shouldCountClaim(true, false));
        assertFalse(DailyMissionRoutine.shouldCountClaim(false, true));
        assertFalse(DailyMissionRoutine.shouldCountClaim(false, false));
        assertTrue(DailyMissionRoutine.shouldCountClaim(true, true));
    }
    @Test
    void autoSchedulingMustKeepDailyMissionTaskInQueue() {
        AccountDescriptor profile = new AccountDescriptor(951L, "DailyAuto", "local", true, 0L, 0L);
        profile.setConfig(ConfigurationKeyEnum.DAILY_MISSION_AUTO_SCHEDULE_BOOL, true);
        var routine = new DailyMissionRoutine(profile, TpDailyTaskEnum.DAILY_MISSIONS);
        routine.hydrateTaskConfiguration();
        routine.configureRecurringBehaviorFlow();
        assertTrue(routine.isRecurring(),
                "TaskQueue removes a Daily Missions task permanently when recurring is false");
    }

    @Test
    void manualDailySchedulingAlsoKeepsItsRepeatingQueueEntry() {
        AccountDescriptor profile = new AccountDescriptor(952L, "DailyManual", "local", true, 0L, 0L);
        profile.setConfig(ConfigurationKeyEnum.DAILY_MISSION_AUTO_SCHEDULE_BOOL, false);
        var routine = new DailyMissionRoutine(profile, TpDailyTaskEnum.DAILY_MISSIONS);
        routine.hydrateTaskConfiguration();
        routine.configureRecurringBehaviorFlow();
        assertTrue(routine.isRecurring());
    }
}
