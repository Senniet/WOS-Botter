package dev.frostguard.tasks.exploration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyLabyrinthProgressPolicyTest {

    @Test
    void reportsProgressOnlyWhenAChallengeAttemptWasStarted() {
        assertTrue(DailyLabyrinthRoutine.shouldReportDailyMissionProgress(true));
        assertFalse(DailyLabyrinthRoutine.shouldReportDailyMissionProgress(false));
    }
}
