package dev.frostguard.tasks.dailies;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TriumphClaimVerificationTest {
    @Test void cannotTreatBothMissingTemplatesAsSuccess() {
        assertFalse(TriumphClaimVerification.dailyVerified(false, false, false, false));
    }
    @Test void clickingWithoutVerifiedClaimedStateIsStillUnresolved() {
        assertFalse(TriumphClaimVerification.dailyVerified(false, true, true, false));
        assertFalse(TriumphClaimVerification.dailyVerified(false, true, false, true));
    }
    @Test void alreadyClaimedOrNewlyVerifiedClaimedAreSafe() {
        assertTrue(TriumphClaimVerification.dailyVerified(true, false, false, false));
        assertTrue(TriumphClaimVerification.dailyVerified(false, true, true, true));
    }
}
