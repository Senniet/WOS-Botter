package dev.frostguard.tasks.dailies;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class IntelClaimRewardConfirmationTest {
    @Test void buttonAbsenceAloneCannotConfirmClaim() {
        assertFalse(IntelClaimRewardConfirmation.isConfirmed(false,false));
        assertFalse(IntelClaimRewardConfirmation.isConfirmed(false,true));
        assertFalse(IntelClaimRewardConfirmation.isConfirmed(true,true));
        assertTrue(IntelClaimRewardConfirmation.isConfirmed(true,false));
    }
}
