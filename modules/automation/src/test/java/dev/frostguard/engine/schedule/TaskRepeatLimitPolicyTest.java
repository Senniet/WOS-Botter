package dev.frostguard.engine.schedule;

import dev.frostguard.api.configs.TpDailyTaskEnum;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskRepeatLimitPolicyTest {
    private final LocalDateTime now = LocalDateTime.of(2026,11,21,23,0);

    @Test void defaultAutomaticDoesNotChangeSchedule() {
        assertEquals(now.plusMinutes(5),
                TaskRepeatLimitPolicy.minimumNext(now.plusMinutes(5),now,0));
    }
    @Test void configuredMinimumDefersTooFastRepeat() {
        assertEquals(now.plusMinutes(30),
                TaskRepeatLimitPolicy.minimumNext(now.plusMinutes(5),now,30));
    }
    @Test void existingGameCooldownIsNeverShortened() {
        assertEquals(now.plusHours(12),
                TaskRepeatLimitPolicy.minimumNext(now.plusHours(12),now,30));
    }
    @Test void limitsAreBoundedToSafeRange() {
        assertEquals(now.plusMinutes(5),
                TaskRepeatLimitPolicy.minimumNext(now.plusMinutes(5),now,2));
        assertEquals(now.plusMinutes(5),
                TaskRepeatLimitPolicy.minimumNext(now.plusMinutes(5),now,10081));
    }
    @Test void onlyWhitelistedTasksHaveConfigurableMinimum() {
        assertNotNull(TaskRepeatLimitPolicy.keyFor(TpDailyTaskEnum.MERCENARY_EVENT));
        assertNotNull(TaskRepeatLimitPolicy.keyFor(TpDailyTaskEnum.RESEARCH));
        assertNull(TaskRepeatLimitPolicy.keyFor(TpDailyTaskEnum.BEAR_TRAP));
        assertNull(TaskRepeatLimitPolicy.keyFor(TpDailyTaskEnum.ARENA));
        assertNull(TaskRepeatLimitPolicy.keyFor(TpDailyTaskEnum.INITIALIZE));
    }
}
