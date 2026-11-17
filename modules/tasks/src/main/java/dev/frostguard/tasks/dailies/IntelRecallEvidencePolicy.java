package dev.frostguard.tasks.dailies;

/** Only stop when the same unresolved march-capacity observation repeats. */
final class IntelRecallEvidencePolicy {
    private static final int UNCHANGED_LIMIT = 3;

    private IntelRecallEvidencePolicy() { }

    static int nextMissingEvidenceStreak(int previousStreak, int lastIdle, int observedIdle) {
        if (observedIdle < 0) return 0;
        return observedIdle == lastIdle ? previousStreak + 1 : 1;
    }

    static boolean shouldStopUnverified(int streak, int idle, int configuredCapacity) {
        // The earlier code already treats all-free or configured-minus-one
        // (possible expired VIP) as resolved, so do not change that policy.
        return streak >= UNCHANGED_LIMIT && idle >= 0
                && configuredCapacity > 1 && idle < configuredCapacity - 1;
    }
}
