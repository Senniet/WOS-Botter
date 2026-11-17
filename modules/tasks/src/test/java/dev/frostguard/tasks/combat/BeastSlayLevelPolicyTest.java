package dev.frostguard.tasks.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BeastSlayLevelPolicyTest {
    @Test void acceptsSupportedLevelRange() {
        assertTrue(BeastSlayRoutine.validBeastLevel(1));
        assertTrue(BeastSlayRoutine.validBeastLevel(30));
        assertTrue(BeastSlayRoutine.validBeastLevel(15));
    }
    @Test void rejectsInvalidLevelsBeforeRepeatedTaps() {
        assertFalse(BeastSlayRoutine.validBeastLevel(0));
        assertFalse(BeastSlayRoutine.validBeastLevel(-1));
        assertFalse(BeastSlayRoutine.validBeastLevel(31));
        assertFalse(BeastSlayRoutine.validBeastLevel(100));
    }
}
