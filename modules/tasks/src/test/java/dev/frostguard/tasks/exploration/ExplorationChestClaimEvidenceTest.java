package dev.frostguard.tasks.exploration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationChestClaimEvidenceTest {
    @Test
    void onlyDisabledClaimControlWithoutEnabledControlProvesCompletion() {
        assertTrue(ExplorationRoutine.claimConfirmed(true, false));
        assertFalse(ExplorationRoutine.claimConfirmed(false, false));
        assertFalse(ExplorationRoutine.claimConfirmed(false, true));
        assertFalse(ExplorationRoutine.claimConfirmed(true, true));
    }
}
