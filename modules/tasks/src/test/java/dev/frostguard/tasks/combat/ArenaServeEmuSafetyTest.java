package dev.frostguard.tasks.combat;

import dev.frostguard.engine.emulator.EmulatorType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaServeEmuSafetyTest {
    @Test void storedLegacyFlagsCannotBuyArenaAttemptsOrPaidRefreshesOnServeEmu() {
        assertFalse(ArenaRoutine.arenaPaidActionsAllowed(EmulatorType.SERVE_EMU));
        assertTrue(ArenaRoutine.arenaPaidActionsAllowed(EmulatorType.BLUESTACKS));
    }
}
