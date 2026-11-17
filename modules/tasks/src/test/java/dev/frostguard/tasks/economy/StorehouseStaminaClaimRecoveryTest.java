package dev.frostguard.tasks.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StorehouseStaminaClaimRecoveryTest {

    @Test
    void leavesUnknownStateUntouchedWithoutBothDialogAnchors() {
        for (boolean[] anchors : new boolean[][]{{false, true}, {true, false}}) {
            Stub actions = new Stub(anchors[0], anchors[1], true);
            assertEquals(StorehouseStaminaClaimRecovery.Outcome.NOT_PRESENT,
                    StorehouseStaminaClaimRecovery.dismissUnacceptedClaim(actions));
            assertFalse(actions.dismissed);
        }
    }

    @Test
    void closesVerifiedUnacceptedClaimWithoutReportingSuccess() {
        Stub actions = new Stub(true, true, true);
        assertEquals(StorehouseStaminaClaimRecovery.Outcome.DISMISSED,
                StorehouseStaminaClaimRecovery.dismissUnacceptedClaim(actions));
        assertTrue(actions.dismissed);
    }

    @Test
    void reportsUnknownWhenVerifiedDialogCannotBeClosed() {
        Stub actions = new Stub(true, true, false);
        assertEquals(StorehouseStaminaClaimRecovery.Outcome.CLOSE_UNCONFIRMED,
                StorehouseStaminaClaimRecovery.dismissUnacceptedClaim(actions));
        assertTrue(actions.dismissed);
    }

    private static final class Stub implements StorehouseStaminaClaimRecovery.Actions {
        private final boolean title;
        private final boolean claim;
        private final boolean city;
        private boolean dismissed;

        Stub(boolean title, boolean claim, boolean city) {
            this.title = title;
            this.claim = claim;
            this.city = city;
        }

        @Override
        public boolean rewardTitleVisible() { return title; }

        @Override
        public boolean claimButtonVisible() { return claim; }

        @Override
        public void dismiss() { dismissed = true; }

        @Override
        public boolean cityVisible() { return city; }
    }
}
