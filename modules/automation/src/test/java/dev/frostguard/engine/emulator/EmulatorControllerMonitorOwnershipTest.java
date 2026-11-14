package dev.frostguard.engine.emulator;

import dev.frostguard.api.domain.AccountDescriptor;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmulatorControllerMonitorOwnershipTest {
    @Test void monitorRejectsOtherProfilesOnSameScreenUntilTheyOwnItsSlot() throws Exception {
        Constructor<EmulatorController> ctor = EmulatorController.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        EmulatorController controller = ctor.newInstance();
        AccountDescriptor owner = new AccountDescriptor(23L, "main", "0", true, 100L, 30L);
        AccountDescriptor other = new AccountDescriptor(24L, "farm", "0", true, 90L, 30L);
        assertFalse(controller.isActiveProfileOwner(owner));
        assertFalse(controller.isActiveProfileOwner(other));
        try {
            controller.adquireEmulatorSlot(owner, (thread, position) -> {});
            assertTrue(controller.isActiveProfileOwner(owner));
            assertFalse(controller.isActiveProfileOwner(other));
        } finally {
            controller.resetSlots();
        }
        assertFalse(controller.isActiveProfileOwner(owner));
    }
}
