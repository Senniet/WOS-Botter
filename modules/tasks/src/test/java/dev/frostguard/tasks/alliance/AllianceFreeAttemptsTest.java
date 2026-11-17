package dev.frostguard.tasks.alliance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AllianceFreeAttemptsTest {
    @Test void actual2026AttemptsCountdownIsParsedSafely(){
        assertEquals(25,AllianceTechRoutine.parseFreeAttemptCount("Attempts: 25/25"));
        assertEquals(24,AllianceTechRoutine.parseFreeAttemptCount("24/25"));
        assertEquals(0,AllianceTechRoutine.parseFreeAttemptCount("Attempts: 0/25"));
        assertEquals(-1,AllianceTechRoutine.parseFreeAttemptCount(null));
        assertEquals(-1,AllianceTechRoutine.parseFreeAttemptCount("No contribution available"));
        assertEquals(-1,AllianceTechRoutine.parseFreeAttemptCount("26/25"));
    }
}
