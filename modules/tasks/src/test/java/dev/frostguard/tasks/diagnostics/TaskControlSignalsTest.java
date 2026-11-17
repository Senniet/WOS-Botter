package dev.frostguard.tasks.diagnostics;

import org.junit.jupiter.api.Test;

import dev.frostguard.engine.error.StopExecutionException;
import dev.frostguard.engine.error.TaskPreemptedException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskControlSignalsTest {

    @Test
    void rethrowsWrappedTaskPreemption() {
        TaskPreemptedException preempted = TaskPreemptedException.because("higher priority task");

        TaskPreemptedException actual = assertThrows(TaskPreemptedException.class,
                () -> TaskControlSignals.rethrowControlSignal(new RuntimeException("capture failed", preempted)));

        assertSame(preempted, actual);
    }

    @Test
    void rethrowsStopExecution() {
        StopExecutionException stopped = StopExecutionException.userCancelled();

        StopExecutionException actual = assertThrows(StopExecutionException.class,
                () -> TaskControlSignals.rethrowControlSignal(stopped));

        assertSame(stopped, actual);
    }
}
