package dev.frostguard.tasks.city;

/**
 * Authorization gate for paid Chief Orders. A failed/missing OCR result
 * never authorizes a point-spending action. Double Time is not a gem purchase.
 */
final class DoubleTimePolicy {
    static final long REQUIRED_POINTS = 800_000L;
    private DoubleTimePolicy() {}

    static boolean mayEnact(Long visibleBalance, Long verifiedCost,
                             boolean correctBook, boolean visibleEnact) {
        return correctBook && visibleEnact
                && verifiedCost != null && verifiedCost == REQUIRED_POINTS
                && visibleBalance != null && visibleBalance >= verifiedCost;
    }
}
