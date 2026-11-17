package dev.frostguard.tasks.pets;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PetAllianceTreasureClaimPolicyTest {
    @Test void confirmsOnlyExhaustedDailyClaims() {
        assertTrue(PetAllianceTreasuresRoutine.remainingClaimsExhausted("Remaining claims today: 0"));
        assertTrue(PetAllianceTreasuresRoutine.remainingClaimsExhausted("Remaining claims today:\n0"));
        assertFalse(PetAllianceTreasuresRoutine.remainingClaimsExhausted("Remaining claims today: 5"));
        assertFalse(PetAllianceTreasuresRoutine.remainingClaimsExhausted("Remaining claims today: 10"));
        assertFalse(PetAllianceTreasuresRoutine.remainingClaimsExhausted(null));
    }
}
