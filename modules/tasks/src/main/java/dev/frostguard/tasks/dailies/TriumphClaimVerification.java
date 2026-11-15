package dev.frostguard.tasks.dailies;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Daily Triumph outcomes must distinguish collected rewards from unavailable ones. */
final class TriumphClaimVerification {
    private static final Pattern PROGRESS = Pattern.compile("([0-9]{1,5})\\s*/\\s*([0-9]{2,5})");

    enum DailyState { CLAIMED, NOT_READY, UNVERIFIED }

    record ChestProgress(int current, int required) {
        boolean belowThreshold() { return current < required; }
    }

    private TriumphClaimVerification() {}

    static ChestProgress parseChestProgress(String text) {
        if (text == null) return null;
        Matcher match = PROGRESS.matcher(text.trim());
        if (!match.matches()) return null;
        try {
            int current = Integer.parseInt(match.group(1));
            int required = Integer.parseInt(match.group(2));
            // Reject implausible HUD values and OCR errors instead of making a
            // non-claim decision from unrelated counters on a wrong screen.
            if (required < 100 || required > 10_000 || current > required) return null;
            return new ChestProgress(current, required);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    static boolean dailyVerified(boolean claimedBefore, boolean claimButtonVisible,
                                 boolean tappedClaimButton, boolean claimedAfter) {
        return claimedBefore || (claimButtonVisible && tappedClaimButton && claimedAfter);
    }

    static DailyState dailyState(boolean claimedBefore, boolean claimButtonVisible,
                                 boolean tappedClaimButton, boolean claimedAfter,
                                 ChestProgress progress) {
        if (dailyVerified(claimedBefore, claimButtonVisible, tappedClaimButton, claimedAfter)) {
            return DailyState.CLAIMED;
        }
        if (!claimButtonVisible && progress != null && progress.belowThreshold()) {
            return DailyState.NOT_READY;
        }
        return DailyState.UNVERIFIED;
    }

    /** Recheck a real not-yet-unlocked chest on the configured cadence or after reset. */
    static LocalDateTime nextProgressReview(LocalDateTime now, LocalDateTime reset, int minutes) {
        LocalDateTime interval = now.plusMinutes(Math.max(10, Math.min(minutes, 1_440)));
        LocalDateTime afterReset = reset.plusMinutes(5);
        return interval.isBefore(afterReset) ? interval : afterReset;
    }
}
