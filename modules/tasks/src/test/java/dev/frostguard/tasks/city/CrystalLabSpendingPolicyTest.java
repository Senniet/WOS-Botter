package dev.frostguard.tasks.city;

import dev.frostguard.engine.emulator.EmulatorType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrystalLabSpendingPolicyTest {
    @Test void serveEmuNeverExecutesRefinedFireCrystalSpendingRoutes() {
        assertFalse(CrystalLaboratoryRoutine.allowFireCrystalSpending(EmulatorType.SERVE_EMU));
        assertTrue(CrystalLaboratoryRoutine.allowFireCrystalSpending(EmulatorType.BLUESTACKS));
    }
}
