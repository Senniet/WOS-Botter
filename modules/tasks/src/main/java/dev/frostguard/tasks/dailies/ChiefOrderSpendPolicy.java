package dev.frostguard.tasks.dailies;

/**
 * Chief Order points are a separate, earned game resource, not gems.
 * Explicitly approved for the normal Chief Orders by the player.
 * Unknown offer identity, price or balance always fails closed.
 */
final class ChiefOrderSpendPolicy {
    private ChiefOrderSpendPolicy() {}

    static boolean allowed(ChiefOrderLiveFrame.Inspection first,
            ChiefOrderLiveFrame.Inspection second, String expectedOrder,
            Long firstBalance, Long secondBalance) {
        if (first == null || second == null || expectedOrder == null
                || !expectedOrder.equals(first.order()) || !expectedOrder.equals(second.order())
                || first.action() != second.action()
                || first.cost() != second.cost()) return false;
        if (first.action() == ChiefOrderLiveFrame.Action.FREE)
            return first.cost() == 0;
        if (first.action() != ChiefOrderLiveFrame.Action.COSTS_POINTS)
            return false;
        long cost = first.cost();
        return cost > 0
                && firstBalance != null && secondBalance != null
                && firstBalance >= cost && secondBalance >= cost
                && Math.abs(firstBalance - secondBalance)
                   <= Math.max(1000L, firstBalance / 100L);
    }
}
