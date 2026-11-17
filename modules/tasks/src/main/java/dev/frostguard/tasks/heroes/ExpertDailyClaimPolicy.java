package dev.frostguard.tasks.heroes;

/** Do not credit a daily expert reward from an unverified tap or missing icon. */
final class ExpertDailyClaimPolicy {
    private ExpertDailyClaimPolicy() {}

    static boolean disappearedOnTwoFrames(boolean shownBefore,
                                           boolean shownAfterOne, boolean shownAfterTwo) {
        return shownBefore && !shownAfterOne && !shownAfterTwo;
    }

    static boolean readyForDailyReset(boolean claimed, boolean homeVerified) {
        return claimed && homeVerified;
    }
}
