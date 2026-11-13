package dev.frostguard.tasks.dailies;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class IntelActionAcknowledgementPolicyTest {
    @Test void aSingleMissingFrameCannotConfirmARescue() {
        var policy = new IntelActionAcknowledgementPolicy();
        assertFalse(policy.record(true));
        assertFalse(policy.record(false));
        assertFalse(policy.record(true));
        assertTrue(policy.record(true));
    }

    @Test void aVisibleLegacyVariantResetsThePendingConfirmation() {
        var policy = new IntelActionAcknowledgementPolicy();
        assertFalse(policy.record(false));
        assertFalse(policy.record(true));
        assertFalse(policy.record(false));
        assertFalse(policy.record(true));
        assertTrue(policy.record(true));
    }

    @Test void consecutiveAbsenceAlsoWorksForJourneyExplore() {
        var policy = new IntelActionAcknowledgementPolicy();
        assertFalse(policy.record(true));
        assertTrue(policy.record(true));
        assertTrue(policy.record(true));
    }
}
