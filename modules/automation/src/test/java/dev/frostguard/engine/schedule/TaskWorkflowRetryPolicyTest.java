package dev.frostguard.engine.schedule;

import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.TaskStateData;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskWorkflowRetryPolicyTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 17, 14, 0);
    private final LocalDateTime due = now.minusMinutes(1);

    @Test void unchangedOverdueTaskMustNotBeReportedCompletedOrRunAgainImmediately() {
        assertTrue(TaskWorkflowRetryPolicy.needsRetry(
                TpDailyTaskEnum.VIP_POINTS, true, due, due, now));
        assertEquals(now.plusMinutes(5), TaskWorkflowRetryPolicy.nextRetryAt(now));
    }

    @Test void aNewValidFutureRunPreservesTheRoutineSchedule() {
        assertFalse(TaskWorkflowRetryPolicy.needsRetry(
                TpDailyTaskEnum.VIP_POINTS, true, due, now.plusHours(1), now));
        assertFalse(TaskWorkflowRetryPolicy.needsRetry(
                TpDailyTaskEnum.VIP_POINTS, true, now.plusHours(1), now.plusHours(1), now));
    }

    @Test void pastOrAbsentNextRunRequiresBoundedRetry() {
        assertTrue(TaskWorkflowRetryPolicy.needsRetry(
                TpDailyTaskEnum.VIP_POINTS, true, due, null, now));
        assertTrue(TaskWorkflowRetryPolicy.needsRetry(
                TpDailyTaskEnum.VIP_POINTS, true, due, now.minusSeconds(1), now));
        assertTrue(TaskWorkflowRetryPolicy.needsRetry(
                TpDailyTaskEnum.VIP_POINTS, true, due, now, now));
    }

    @Test void aOneShotOrInitializeNeverInheritsTheRecurringWorkflowGuard() {
        assertFalse(TaskWorkflowRetryPolicy.needsRetry(
                TpDailyTaskEnum.VIP_POINTS, false, due, due, now));
        assertFalse(TaskWorkflowRetryPolicy.needsRetry(
                TpDailyTaskEnum.INITIALIZE, true, due, due, now));
    }

    @Test void runNowCannotForgeLastAttemptSuccessOrChangeLastOutcome() {
        TaskStateData previous = new TaskStateData();
        previous.setLastExecutionTime(now.minusDays(2));
        previous.setLastAttemptAt(now.minusDays(2));
        previous.setLastSuccessfulAt(now.minusDays(3));
        previous.setExecutionOutcome(TaskStateData.ExecutionOutcome.DEFERRED);
        var queued = TaskQueue.queuedRunNowState(previous, 9L,
                TpDailyTaskEnum.VIP_POINTS.getId(), now);
        assertSame(previous, queued);
        assertTrue(queued.isScheduled());
        assertFalse(queued.isExecuting());
        assertEquals(now, queued.getScheduledAt());
        assertEquals(now.minusDays(2), queued.getLastExecutionTime());
        assertEquals(now.minusDays(2), queued.getLastAttemptAt());
        assertEquals(now.minusDays(3), queued.getLastSuccessfulAt());
        assertEquals(TaskStateData.ExecutionOutcome.DEFERRED, queued.getExecutionOutcome());
    }

    @Test void newRunNowStatusStartsWithoutInventingAnExecutionTime() {
        var state = TaskQueue.queuedRunNowState(null, 9L,
                TpDailyTaskEnum.VIP_POINTS.getId(), now);
        assertNull(state.getLastExecutionTime());
        assertTrue(state.isScheduled());
    }
}
