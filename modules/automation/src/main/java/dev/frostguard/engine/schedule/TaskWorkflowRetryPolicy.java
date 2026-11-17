package dev.frostguard.engine.schedule;

import dev.frostguard.api.configs.TpDailyTaskEnum;
import java.time.LocalDateTime;
import java.util.Objects;

/** Safety gate between completed task code and the recurring queue.
 * No success or immediate retry is inferred solely from a method returning.
 */
final class TaskWorkflowRetryPolicy {
    private static final int MIN_RETRY_MINUTES = 5;

    private TaskWorkflowRetryPolicy() {}

    static boolean needsRetry(TpDailyTaskEnum type, boolean recurring,
                              LocalDateTime scheduledBefore,
                              LocalDateTime scheduledAfter, LocalDateTime now) {
        if (!recurring || type == TpDailyTaskEnum.INITIALIZE) return false;
        Objects.requireNonNull(now, "now");
        if (scheduledAfter == null || !scheduledAfter.isAfter(now)) return true;
        // A task due for execution that returned with exactly the same old
        // schedule did not establish a meaningful next invocation.
        return Objects.equals(scheduledBefore, scheduledAfter)
                && (scheduledBefore == null || !scheduledBefore.isAfter(now));
    }

    static LocalDateTime nextRetryAt(LocalDateTime now) {
        return Objects.requireNonNull(now, "now").plusMinutes(MIN_RETRY_MINUTES);
    }
}
