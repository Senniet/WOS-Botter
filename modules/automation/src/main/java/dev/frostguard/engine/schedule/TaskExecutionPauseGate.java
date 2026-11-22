package dev.frostguard.engine.schedule;

import dev.frostguard.engine.error.StopExecutionException;
import java.util.function.BooleanSupplier;

/**
 * Cooperative user-pause gate for the current TaskQueue worker.
 *
 * A web Pause request previously only stopped the NEXT queued task. The
 * already-running routine could still issue dozens of taps and swipes.
 * Checkpoints in the central emulator controller and routine sleeps now park
 * that worker between ADB operations until Resume. Other profiles remain
 * independent. Stop can always interrupt the parked virtual thread.
 */
public final class TaskExecutionPauseGate {
    private static final ThreadLocal<BooleanSupplier> CURRENT = new ThreadLocal<>();
    private TaskExecutionPauseGate() {}

    static void bind(BooleanSupplier paused) {
        CURRENT.set(paused);
    }

    static void unbind() {
        CURRENT.remove();
    }

    public static void checkpoint() {
        BooleanSupplier paused = CURRENT.get();
        if (paused == null) return;
        while (paused.getAsBoolean()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new StopExecutionException("Stopped while user-paused");
            }
            try {
                Thread.sleep(60L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new StopExecutionException("Stopped while user-paused");
            }
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new StopExecutionException("Task interrupted by user request");
        }
    }
}
