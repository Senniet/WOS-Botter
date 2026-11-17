package dev.frostguard.tasks.dailies;

/** A missing Claim All button alone is never sufficient proof of success. */
final class IntelClaimRewardConfirmation {
    private IntelClaimRewardConfirmation() { }

    static boolean isConfirmed(boolean intelMapVisible, boolean activeClaimAllStillVisible) {
        return intelMapVisible && !activeClaimAllStillVisible;
    }
}
