package dev.frostguard.tasks.analytics;

import dev.frostguard.engine.emulator.EmulatorType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameAnalyticsRoutineBackendTest {
    @Test void historyPreservesTheActualCaptureBackend() {
        assertEquals(EmulatorType.SERVE_EMU,
                GameAnalyticsRoutine.analyticsBackend(EmulatorType.SERVE_EMU));
        assertEquals(EmulatorType.MUMU,
                GameAnalyticsRoutine.analyticsBackend(EmulatorType.MUMU));
        assertThrows(NullPointerException.class, () -> GameAnalyticsRoutine.analyticsBackend(null));
    }
}
