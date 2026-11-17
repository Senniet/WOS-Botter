package dev.frostguard.tasks.city;

import dev.frostguard.engine.emulator.EmulatorType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarAcademySafetyPolicyTest {
    @Test void currentServeEmuDoesNotAttemptUnverifiedShardRedemption() {
        assertFalse(WarAcademyRoutine.isLegacyRedeemFlowAllowed(EmulatorType.SERVE_EMU));
        assertTrue(WarAcademyRoutine.isLegacyRedeemFlowAllowed(EmulatorType.BLUESTACKS));
    }
}
