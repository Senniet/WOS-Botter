package dev.frostguard.tasks.dailies;

/** A Triumph task is not complete just because a template was not found.
 * The game may show a new reward layout or a claim action may have failed.
 */
final class TriumphClaimVerification {
    private TriumphClaimVerification() {}

    static boolean dailyVerified(boolean claimedBefore, boolean claimButtonVisible,
                                 boolean tappedClaimButton, boolean claimedAfter) {
        return claimedBefore || (claimButtonVisible && tappedClaimButton && claimedAfter);
    }
}
