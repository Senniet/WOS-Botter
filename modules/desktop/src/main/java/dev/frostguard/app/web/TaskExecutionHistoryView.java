package dev.frostguard.app.web;

import dev.frostguard.api.domain.DailyTaskStatusData;
import dev.frostguard.api.domain.TaskStateData;
import java.time.LocalDateTime;

/**
 * Browser-facing execution history. The live TaskStateData is volatile and
 * deliberately replaced when the scheduler starts/requeues/toggles a task.
 * The database daily_task.last_execution survives those operations and is
 * written only after a successful execution. Merge both timestamps instead
 * of showing "Never" for tasks that already ran.
 */
final class TaskExecutionHistoryView {
    private TaskExecutionHistoryView() {}

    static LocalDateTime lastSuccess(TaskStateData live, DailyTaskStatusData persisted) {
        return latest(live == null ? null : live.getLastSuccessfulAt(),
                persisted == null ? null : persisted.getLastCompletion());
    }

    static LocalDateTime lastAttempt(TaskStateData live, DailyTaskStatusData persisted) {
        // A persisted success necessarily implies an actual attempt; it is a
        // lower bound when the volatile lastAttemptAt disappeared on restart.
        return latest(live == null ? null : live.getLastAttemptAt(),
                persisted == null ? null : persisted.getLastCompletion());
    }

    static LocalDateTime next(TaskStateData live, DailyTaskStatusData persisted) {
        // Live scheduling takes precedence, including a deliberate dequeue.
        return live != null ? live.getNextExecutionTime()
                : persisted == null ? null : persisted.getScheduledAt();
    }

    private static LocalDateTime latest(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        return b != null && b.isAfter(a) ? b : a;
    }
}
