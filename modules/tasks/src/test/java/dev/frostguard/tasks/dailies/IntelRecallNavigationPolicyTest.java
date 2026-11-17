package dev.frostguard.tasks.dailies;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Turning off smart processing must not trigger march recalls. */
class IntelRecallNavigationPolicyTest {
    @Test void noImplicitRecallUnlessUserExplicitlyEnabledIt() {
        assertFalse(IntelligenceRoutine.shouldRecallGatherTroops(false));
        assertTrue(IntelligenceRoutine.shouldRecallGatherTroops(true));
    }
}
