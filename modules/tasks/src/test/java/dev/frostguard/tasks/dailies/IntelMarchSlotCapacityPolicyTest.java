package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.MarchSlotState;
import dev.frostguard.api.domain.MarchSlotStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntelMarchSlotCapacityPolicyTest {
    @Test void fourIdlePlusTwoUnavailableUsesActualFourSlots() {
        List<MarchSlotState> slots = List.of(
                slot(1,MarchSlotStatus.IDLE),slot(2,MarchSlotStatus.IDLE),
                slot(3,MarchSlotStatus.IDLE),slot(4,MarchSlotStatus.IDLE),
                slot(5,MarchSlotStatus.LOCKED),slot(6,MarchSlotStatus.LOCKED));
        assertEquals(4,IntelMarchSlotCapacityPolicy.fullyIdleUsableCapacity(slots,6));
        assertTrue(IntelMarchSlotCapacityPolicy.confirmedByTwoReadings(4,4));
    }

    @Test void occupiedMarchCannotBeDeclaredSuccessfullyRecalled() {
        List<MarchSlotState> slots = List.of(
                slot(1,MarchSlotStatus.IDLE),slot(2,MarchSlotStatus.IDLE),
                slot(3,MarchSlotStatus.GATHERING),slot(4,MarchSlotStatus.IDLE),
                slot(5,MarchSlotStatus.LOCKED),slot(6,MarchSlotStatus.LOCKED));
        assertEquals(-1,IntelMarchSlotCapacityPolicy.fullyIdleUsableCapacity(slots,6));
    }

    @Test void unknownOrIncompleteRowsNeverChangeCapacity() {
        List<MarchSlotState> slots = new ArrayList<>();
        for(int i=1;i<=4;i++)slots.add(slot(i,MarchSlotStatus.IDLE));
        slots.add(slot(5,MarchSlotStatus.LOCKED));
        assertEquals(-1,IntelMarchSlotCapacityPolicy.fullyIdleUsableCapacity(slots,6));
        slots.add(slot(6,MarchSlotStatus.BUSY_UNKNOWN));
        assertEquals(-1,IntelMarchSlotCapacityPolicy.fullyIdleUsableCapacity(slots,6));
        slots.set(5,slot(5,MarchSlotStatus.LOCKED));
        assertEquals(-1,IntelMarchSlotCapacityPolicy.fullyIdleUsableCapacity(slots,6));
        assertFalse(IntelMarchSlotCapacityPolicy.confirmedByTwoReadings(4,-1));
        assertFalse(IntelMarchSlotCapacityPolicy.confirmedByTwoReadings(4,3));
    }

    private static MarchSlotState slot(int number, MarchSlotStatus status) {
        return MarchSlotState.of(number,status);
    }
}
