package dev.frostguard.tasks.alliance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AllianceAutojoinConfigurationTest {
    @Test void missingOrInvalidQueueSettingDoesNotCrashAndUsesSafeDefault() {
        assertEquals(3, AllianceAutojoinRoutine.validQueueCount(null));
        assertEquals(3, AllianceAutojoinRoutine.validQueueCount(0));
        assertEquals(3, AllianceAutojoinRoutine.validQueueCount(7));
        assertEquals(1, AllianceAutojoinRoutine.validQueueCount(1));
        assertEquals(6, AllianceAutojoinRoutine.validQueueCount(6));
    }
}
