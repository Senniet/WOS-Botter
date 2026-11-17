package dev.frostguard.tasks.heroes;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpertSkillTrainingSchedulingTest {
    @Test void unknownSkillStartHasBoundedPositiveRetryNotAnImmediateLoop() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 17, 12, 0);
        assertEquals(now.plusMinutes(30), ExpertSkillTrainingRoutine.unknownTrainingRetryAt(now));
    }
}
