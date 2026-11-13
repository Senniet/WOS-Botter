package dev.frostguard.tasks.dailies;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
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
}
