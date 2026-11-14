package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.MarchSlotAvailability;
import dev.frostguard.api.domain.MarchSlotState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Never count a locked/Unavailable row as an active march slot. */
final class IntelMarchSlotCapacityPolicy {
    private IntelMarchSlotCapacityPolicy() { }

    /**
     * Returns a positive effective capacity only for a complete, unambiguous
     * six-row panel in which every usable slot is idle and every other slot
     * is explicitly LOCKED. Unknown or occupied slots cannot confirm it.
     */
    static int fullyIdleUsableCapacity(List<MarchSlotState> slots, int configuredCapacity) {
        if (slots == null || configuredCapacity < 2 || configuredCapacity > 6
                || slots.size() != 6) return -1;
        Set<Integer> seen = new HashSet<>();
        int idle=0, locked=0;
        for (MarchSlotState slot : slots) {
            if (slot == null || slot.slot() < 1 || slot.slot() > 6
                    || !seen.add(slot.slot())) return -1;
            if (slot.availability() == MarchSlotAvailability.IDLE) idle++;
            else if (slot.availability() == MarchSlotAvailability.LOCKED) locked++;
            else return -1; // Occupied and UNKNOWN both demand safer retries
        }
        return seen.size() == 6 && locked > 0 && idle > 0 && idle < configuredCapacity
                ? idle : -1;
    }

    static boolean confirmedByTwoReadings(int first, int second) {
        return first > 0 && first == second;
    }
}
