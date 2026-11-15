package dev.frostguard.tasks.economy;

import java.util.Objects;

/** Conservatively dismisses an unaccepted reward after a verified Claim tap. */
final class StorehouseStaminaClaimRecovery {

    enum Outcome {
        NOT_PRESENT,
        DISMISSED,
        CLOSE_UNCONFIRMED
    }

    interface Actions {
        boolean rewardTitleVisible();

        boolean claimButtonVisible();

        void dismiss();

        boolean cityVisible();
    }

    private StorehouseStaminaClaimRecovery() {
    }

    static Outcome dismissUnacceptedClaim(Actions actions) {
        Objects.requireNonNull(actions, "actions");
        // Both independently identified UI elements must still exist after Claim.
        // A missing title or button is not evidence that the reward was collected.
        if (!actions.rewardTitleVisible() || !actions.claimButtonVisible()) {
            return Outcome.NOT_PRESENT;
        }
        actions.dismiss();
        return actions.cityVisible() ? Outcome.DISMISSED : Outcome.CLOSE_UNCONFIRMED;
    }
}
