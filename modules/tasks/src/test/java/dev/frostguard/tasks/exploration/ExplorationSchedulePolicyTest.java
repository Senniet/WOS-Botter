package dev.frostguard.tasks.exploration;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExplorationSchedulePolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 12, 0);

    @Test
    void retriesUnknownClaimOutcomeWithoutUsingTheNormalOffset() {
        assertEquals(
                NOW.plusMinutes(10),
                ExplorationSchedulePolicy.nextRun(
                        NOW, 240, 10, ExplorationRoutine.ExplorationClaimOutcome.UNKNOWN));
    }

    @Test
    void usesConfiguredOffsetAfterConfirmedClaim() {
        assertEquals(
                NOW.plusMinutes(240),
                ExplorationSchedulePolicy.nextRun(
                        NOW, 240, 10, ExplorationRoutine.ExplorationClaimOutcome.CLAIMED));
    }

    @Test
    void usesConfiguredOffsetWhenNoRewardIsAvailable() {
        assertEquals(
                NOW.plusMinutes(240),
                ExplorationSchedulePolicy.nextRun(
                        NOW, 240, 10, ExplorationRoutine.ExplorationClaimOutcome.NONE_AVAILABLE));
    }
}
