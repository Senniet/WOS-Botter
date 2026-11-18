package dev.frostguard.app.web;

import dev.frostguard.api.domain.DailyTaskStatusData;
import dev.frostguard.api.domain.TaskStateData;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskExecutionHistoryViewTest {
    private static final LocalDateTime PAST=LocalDateTime.of(2026,11,18,18,0);
    private static final LocalDateTime RECENT=LocalDateTime.of(2026,11,18,21,26);
    private static final LocalDateTime NEXT=LocalDateTime.of(2026,11,19,1,0);

    private static DailyTaskStatusData persisted(LocalDateTime completed) {
        return DailyTaskStatusData.create(1L,42,null,true,NEXT,completed);
    }

    @Test void runningOrQueuedStateWithNullTimestampsFallsBackToDurableSuccess() {
        var running=TaskStateData.of(1L,42,null,true,false,null,NEXT);
        assertNull(running.getLastSuccessfulAt());
        assertNull(running.getLastAttemptAt());
        assertEquals(PAST,TaskExecutionHistoryView.lastAttempt(running,persisted(PAST)));
        assertEquals(PAST,TaskExecutionHistoryView.lastSuccess(running,persisted(PAST)));
        assertEquals(NEXT,TaskExecutionHistoryView.next(running,persisted(PAST)));
    }

    @Test void afterRestartWhenLiveStateIsMissingPersistedHistoryRemainsVisible() {
        assertEquals(PAST,TaskExecutionHistoryView.lastAttempt(null,persisted(PAST)));
        assertEquals(PAST,TaskExecutionHistoryView.lastSuccess(null,persisted(PAST)));
        assertEquals(NEXT,TaskExecutionHistoryView.next(null,persisted(PAST)));
    }

    @Test void failedLaterAttemptMustNotOverwriteLastSuccessfulRun() {
        var running=TaskStateData.of(1L,42,null,true,false,null,NEXT);
        running.setLastAttemptAt(RECENT);
        running.setExecutionOutcome(TaskStateData.ExecutionOutcome.FAILED);
        assertEquals(RECENT,TaskExecutionHistoryView.lastAttempt(running,persisted(PAST)));
        assertEquals(PAST,TaskExecutionHistoryView.lastSuccess(running,persisted(PAST)));
    }

    @Test void newerDurableSuccessWinsOverStaleInMemorySuccess() {
        var running=TaskStateData.of(1L,42,null,true,false,null,NEXT);
        running.setLastSuccessfulAt(PAST);
        running.setLastAttemptAt(PAST);
        assertEquals(RECENT,TaskExecutionHistoryView.lastSuccess(running,persisted(RECENT)));
        assertEquals(RECENT,TaskExecutionHistoryView.lastAttempt(running,persisted(RECENT)));
    }

    @Test void neverIsReservedForARealAbsenceOfBothHistorySources() {
        assertNull(TaskExecutionHistoryView.lastAttempt(null,null));
        assertNull(TaskExecutionHistoryView.lastSuccess(null,null));
        var queued=TaskStateData.of(1L,42,null,true,false,null,NEXT);
        assertNull(TaskExecutionHistoryView.lastAttempt(queued,null));
        assertNull(TaskExecutionHistoryView.lastSuccess(queued,null));
    }

    @Test void unscheduledLiveTaskDoesNotInheritHistoricalDueDate() {
        var disabled=TaskStateData.of(1L,42,null,false,false,null,null);
        assertNull(TaskExecutionHistoryView.next(disabled,persisted(PAST)));
    }
}
