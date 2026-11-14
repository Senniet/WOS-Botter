package dev.frostguard.api.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TaskExecutionOutcomeTest {
    @Test void outcomeIsIndependentOfSchedulingPhase() {
        TaskStateData state = new TaskStateData();
        assertEquals(TaskStateData.ExecutionOutcome.NOT_RUN, state.getExecutionOutcome());
        state.setScheduled(true);
        state.setExecuting(false);
        state.setExecutionOutcome(TaskStateData.ExecutionOutcome.FAILED);
        state.setExecutionDetail("HomeNotFoundException");
        state.setLastAttemptAt(LocalDateTime.of(2026, 11, 14, 14, 0));
        assertEquals(TaskStateData.ExecutionPhase.PENDING, state.currentPhase());
        assertEquals(TaskStateData.ExecutionOutcome.FAILED, state.getExecutionOutcome());
        assertEquals("HomeNotFoundException", state.getExecutionDetail());
        assertNotNull(state.getLastAttemptAt());
    }

    @Test void unknownOutcomeIsRepresentableWithoutMarkingSuccess() {
        TaskStateData state = new TaskStateData();
        state.setExecutionOutcome(TaskStateData.ExecutionOutcome.UNKNOWN);
        assertNotEquals(TaskStateData.ExecutionOutcome.SUCCEEDED, state.getExecutionOutcome());
    }
}
