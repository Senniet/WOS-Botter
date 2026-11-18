package dev.frostguard.tasks.alliance;

/**
 * Free-only Alliance Tech donation pacing. The last free attempt is always
 * verified on its own so an async UI transition cannot turn a burst into a
 * currency action. Batch progress comes from the game's remaining counter,
 * never from how many taps were sent.
 */
final class AllianceDonationBatchPolicy {
    static final int MAX_BURST_SIZE = 4;
    static final int INTER_TAP_DELAY_MS = 140;

    private AllianceDonationBatchPolicy() {}

    static int nextBatchSize(int available, boolean fastMode) {
        if (available <= 0 || available > 25) return 0;
        if (!fastMode || available <= 2) return 1;
        // Keep one free attempt in reserve for an independently checked tap.
        return Math.min(MAX_BURST_SIZE, available - 1);
    }

    /** -1 means no reliable confirmation: do not send the next donation. */
    static int confirmedDonationCount(int before, int after, int requested) {
        if (before < 1 || before > 25 || after < 0 || after >= before
                || requested < 1 || requested > MAX_BURST_SIZE) return -1;
        int delta = before - after;
        return delta <= requested ? delta : -1;
    }
}
