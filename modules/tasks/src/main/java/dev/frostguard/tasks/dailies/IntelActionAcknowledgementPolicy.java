package dev.frostguard.tasks.dailies;

/** Requires two successive complete, button-absent observations after a mission action. */
final class IntelActionAcknowledgementPolicy {
    private int consecutiveAbsent;

    boolean record(boolean allKnownButtonsAbsent) {
        consecutiveAbsent = allKnownButtonsAbsent ? Math.min(2, consecutiveAbsent + 1) : 0;
        return consecutiveAbsent >= 2;
    }
}
