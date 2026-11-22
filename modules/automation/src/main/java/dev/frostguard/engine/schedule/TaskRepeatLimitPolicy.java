package dev.frostguard.engine.schedule;

import java.time.LocalDateTime;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;

/**
 * Optional minimum spacing after the previous attempt.
 *
 * This is a floor, not a new automatic eligibility prediction: a task's real
 * game timer, retry/backoff and stamina deferral can always demand a later
 * start. A zero value preserves the existing exact scheduler behavior.
 */
public final class TaskRepeatLimitPolicy {
    private TaskRepeatLimitPolicy() { }

    public static ConfigurationKeyEnum keyFor(TpDailyTaskEnum task) {
        if (task == null) return null;
        try {
            return ConfigurationKeyEnum.valueOf("TASK_REPEAT_GAP_" + task.name() + "_INT");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static LocalDateTime adjust(TpDailyTaskEnum task,
            AccountDescriptor profile, LocalDateTime planned,
            LocalDateTime completedAt, boolean preempted) {
        if (planned == null || profile == null || preempted) return planned;
        ConfigurationKeyEnum key = keyFor(task);
        if (key == null) return planned;
        Integer configured = profile.getConfig(key, Integer.class);
        return minimumNext(planned, completedAt, configured == null ? 0 : configured);
    }

    public static LocalDateTime minimumNext(LocalDateTime planned,
            LocalDateTime finishedAt, int minutes) {
        if (planned == null || finishedAt == null || minutes < 5 || minutes > 10080)
            return planned;
        LocalDateTime earliest = finishedAt.plusMinutes(minutes);
        return planned.isBefore(earliest) ? earliest : planned;
    }
}
