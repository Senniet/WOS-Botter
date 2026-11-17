package dev.frostguard.engine.emulator;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmulatorControllerSlotKeyTest {
    @Test void aliasAndConfiguredIndexShareTheSamePhysicalDeviceLease() {
        Map<String, String> configuredSerials = Map.of(
                "0", "192.168.1.20:15559",
                "192.168.1.20:15559", "192.168.1.20:15559",
                "1", "192.168.1.20:15561");
        var resolve = (java.util.function.Function<String, String>) configuredSerials::get;
        assertEquals(EmulatorController.canonicalSlotKey("0", resolve),
                EmulatorController.canonicalSlotKey("192.168.1.20:15559", resolve));
        assertNotEquals(EmulatorController.canonicalSlotKey("0", resolve),
                EmulatorController.canonicalSlotKey("1", resolve));
    }

    @Test void refusesMissingPhysicalIdentityInsteadOfGuessingLease() {
        assertThrows(IllegalArgumentException.class,
                () -> EmulatorController.canonicalSlotKey("", x -> "x"));
        assertThrows(IllegalArgumentException.class,
                () -> EmulatorController.canonicalSlotKey("0", x -> ""));
        assertThrows(IllegalArgumentException.class,
                () -> EmulatorController.canonicalSlotKey("0", x -> null));
    }
}
