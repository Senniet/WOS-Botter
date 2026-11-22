package dev.frostguard.engine.schedule;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class TaskExecutionPauseGateTest {
    @Test void noQueueContextNeverBlocks() {
        TaskExecutionPauseGate.checkpoint();
    }

    @Test void runningWorkerParksUntilResume() throws Exception {
        AtomicBoolean paused = new AtomicBoolean(true);
        AtomicInteger progress = new AtomicInteger();
        CountDownLatch atGate = new CountDownLatch(1);
        Thread worker = Thread.ofVirtual().start(() -> {
            TaskExecutionPauseGate.bind(paused::get);
            try {
                atGate.countDown();
                TaskExecutionPauseGate.checkpoint();
                progress.incrementAndGet();
            } finally {
                TaskExecutionPauseGate.unbind();
            }
        });
        assertTrue(atGate.await(2,TimeUnit.SECONDS));
        Thread.sleep(180);
        assertEquals(0,progress.get(), "No new action after pause");
        paused.set(false);
        worker.join(2000);
        assertFalse(worker.isAlive());
        assertEquals(1,progress.get());
    }

    @Test void stoppingParkedWorkerDoesNotDeadlock() throws Exception {
        AtomicBoolean paused = new AtomicBoolean(true);
        AtomicReference<Throwable> caught = new AtomicReference<>();
        CountDownLatch atGate = new CountDownLatch(1);
        Thread worker = Thread.ofVirtual().start(() -> {
            TaskExecutionPauseGate.bind(paused::get);
            try {
                atGate.countDown();
                TaskExecutionPauseGate.checkpoint();
            } catch (RuntimeException ex) {
                caught.set(ex);
            } finally {
                TaskExecutionPauseGate.unbind();
            }
        });
        assertTrue(atGate.await(2,TimeUnit.SECONDS));
        Thread.sleep(120);
        worker.interrupt();
        worker.join(2000);
        assertFalse(worker.isAlive(), "Stop interrupts paused worker");
        assertNotNull(caught.get());
        assertTrue(caught.get().getClass().getSimpleName().contains("StopExecutionException"));
    }

    @Test void pauseIsScopedToOneQueueWorker() throws Exception {
        AtomicBoolean paused = new AtomicBoolean(true);
        CountDownLatch completed = new CountDownLatch(1);
        Thread unaffected = Thread.ofVirtual().start(() -> {
            TaskExecutionPauseGate.bind(() -> false);
            try { TaskExecutionPauseGate.checkpoint(); completed.countDown(); }
            finally { TaskExecutionPauseGate.unbind(); }
        });
        assertTrue(completed.await(2,TimeUnit.SECONDS));
        unaffected.join();
        assertTrue(paused.get());
    }
}
