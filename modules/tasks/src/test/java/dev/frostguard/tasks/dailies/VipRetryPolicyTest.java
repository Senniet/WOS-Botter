package dev.frostguard.tasks.dailies;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VipRetryPolicyTest {
    @Test void unknownVipMenuUsesShortRetryInsteadOfWaitingForTomorrow() {
        var now = LocalDateTime.of(2026, 11, 17, 12, 0);
        assertEquals(now.plusMinutes(10), VipRoutine.nextUnverifiedVisit(now));
    }
}
