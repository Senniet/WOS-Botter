package dev.frostguard.tasks.exploration;

import java.time.LocalDateTime;

final class ExplorationSchedulePolicy {

    private ExplorationSchedulePolicy() {
    }

    static LocalDateTime nextRun(
            LocalDateTime now,
            int configuredOffsetMinutes,
            int unknownRetryMinutes,
            ExplorationRoutine.ExplorationClaimOutcome outcome) {
        int delayMinutes = outcome == ExplorationRoutine.ExplorationClaimOutcome.UNKNOWN
                ? unknownRetryMinutes
                : configuredOffsetMinutes;
        return now.plusMinutes(delayMinutes);
    }
}
