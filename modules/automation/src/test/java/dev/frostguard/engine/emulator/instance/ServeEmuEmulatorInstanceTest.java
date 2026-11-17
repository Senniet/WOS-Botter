package dev.frostguard.engine.emulator.instance;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServeEmuEmulatorInstanceTest {
    @Test void acceptsOneOrMultipleDistinctTcpDevices() {
        assertEquals(List.of("192.168.1.20:5555", "192.168.1.20:5557"),
                ServeEmuEmulatorInstance.validateEndpoints("192.168.1.20:5555, 192.168.1.20:5557"));
        assertEquals(List.of("[fd00::2]:5555"), ServeEmuEmulatorInstance.validateEndpoints("[fd00::2]:5555"));
    }

    @Test void rejectsUnsafeAndNonTcpDeviceIdentifiers() {
        for (String invalid : List.of("", "localhost", "192.168.1.20:0", "192.168.1.20:65536",
                "192.168.1.20:5555;whoami", "192.168.1.20:5555,", "emulator-5554",
                "192.168.1.20:5555,192.168.1.20:5555")) {
            assertThrows(IllegalArgumentException.class,
                    () -> ServeEmuEmulatorInstance.validateEndpoints(invalid), invalid);
        }
    }
}
