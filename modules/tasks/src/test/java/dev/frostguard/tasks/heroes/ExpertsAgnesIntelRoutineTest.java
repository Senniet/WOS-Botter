package dev.frostguard.tasks.heroes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExpertsAgnesIntelRoutineTest {

    @Test
    void homeMustBeVerifiedBeforeTheClaimCanWaitUntilDailyReset() {
        assertTrue(ExpertsAgnesIntelRoutine.confirmedClaimAndHome(true, true));
        assertFalse(ExpertsAgnesIntelRoutine.confirmedClaimAndHome(true, false));
        assertFalse(ExpertsAgnesIntelRoutine.confirmedClaimAndHome(false, true));
        assertFalse(ExpertsAgnesIntelRoutine.confirmedClaimAndHome(false, false));
    }

    @Test
    void onlyTwoConfirmedDisappearanceFramesAuthorizeDailyCompletion() {
        assertTrue(ExpertsAgnesIntelRoutine.claimDisappeared(true, false, false));
        assertFalse(ExpertsAgnesIntelRoutine.claimDisappeared(false, false, false));
        assertFalse(ExpertsAgnesIntelRoutine.claimDisappeared(true, true, false));
        assertFalse(ExpertsAgnesIntelRoutine.claimDisappeared(true, false, true));
        assertFalse(ExpertsAgnesIntelRoutine.claimDisappeared(true, true, true));
    }
}
