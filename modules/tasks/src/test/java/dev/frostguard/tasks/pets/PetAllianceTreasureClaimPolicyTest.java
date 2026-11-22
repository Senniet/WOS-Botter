package dev.frostguard.tasks.pets;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PetAllianceTreasureClaimPolicyTest {
    @Test
    void quotaZeroOcrCanReportZeroAsLetterOButNotPositiveAttempts() {
        org.junit.jupiter.api.Assertions.assertTrue(
                PetAllianceTreasuresRoutine.remainingClaimsExhausted("Remaining claims today: 0"));
        org.junit.jupiter.api.Assertions.assertTrue(
                PetAllianceTreasuresRoutine.remainingClaimsExhausted("Remaining claims today: O"));
        org.junit.jupiter.api.Assertions.assertFalse(
                PetAllianceTreasuresRoutine.remainingClaimsExhausted("Remaining claims today: 5"));
        org.junit.jupiter.api.Assertions.assertFalse(
                PetAllianceTreasuresRoutine.remainingClaimsExhausted(null));
    }

    @Test void confirmsOnlyExhaustedDailyClaims() {
        assertTrue(PetAllianceTreasuresRoutine.remainingClaimsExhausted("Remaining claims today: 0"));
        assertTrue(PetAllianceTreasuresRoutine.remainingClaimsExhausted("Remaining claims today:\n0"));
        assertFalse(PetAllianceTreasuresRoutine.remainingClaimsExhausted("Remaining claims today: 5"));
        assertFalse(PetAllianceTreasuresRoutine.remainingClaimsExhausted("Remaining claims today: 10"));
        assertFalse(PetAllianceTreasuresRoutine.remainingClaimsExhausted(null));
    }
}
