package dev.frostguard.tasks.economy;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.frostguard.api.domain.MarchSlotState;
import dev.frostguard.api.domain.MarchSlotStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatherDeploymentConfirmationTest {

    @Test
    void confirmsNewGatherMarchFromPreviouslyIdleSlot() {
        assertEquals(1, GatherRoutine.countConfirmedNewGatherMarches(
                List.of(new MarchSlotState(1, MarchSlotStatus.IDLE, null)),
                List.of(new MarchSlotState(1, MarchSlotStatus.GATHERING, null))));
    }

    @Test
    void confirmsNewGatherMarchAfterPriorMarchReturned() {
        assertEquals(1, GatherRoutine.countConfirmedNewGatherMarches(
                List.of(new MarchSlotState(1, MarchSlotStatus.RETURNING, null)),
                List.of(new MarchSlotState(1, MarchSlotStatus.GATHERING, null))));
    }

    @Test
    void doesNotCountGatherMarchThatWasAlreadyActive() {
        assertEquals(0, GatherRoutine.countConfirmedNewGatherMarches(
                List.of(new MarchSlotState(1, MarchSlotStatus.GATHERING, null)),
                List.of(new MarchSlotState(1, MarchSlotStatus.GATHERING, null))));
    }

    @Test
    void doesNotCountUnknownPriorSlotStateAsAConfirmedNewDeployment() {
        assertEquals(0, GatherRoutine.countConfirmedNewGatherMarches(
                List.of(new MarchSlotState(1, MarchSlotStatus.BUSY_UNKNOWN, null)),
                List.of(new MarchSlotState(1, MarchSlotStatus.GATHERING, null))));
    }

    @Test
    void doesNotCountUnknownPostActionStateAsADeployment() {
        assertEquals(0, GatherRoutine.countConfirmedNewGatherMarches(
                List.of(new MarchSlotState(1, MarchSlotStatus.IDLE, null)),
                List.of(new MarchSlotState(1, MarchSlotStatus.BUSY_UNKNOWN, null))));
    }
    @Test
    void doesNotTreatAnEmptyInitialQueueReadAsProofOfNewDeployment() {
        assertEquals(0, GatherRoutine.countConfirmedNewGatherMarches(
                List.of(), List.of(new MarchSlotState(1, MarchSlotStatus.GATHERING, null))));
    }

    @Test
    void doesNotCountAnUnmatchedQueueSlotAsConfirmedDeployment() {
        assertEquals(0, GatherRoutine.countConfirmedNewGatherMarches(
                List.of(new MarchSlotState(2, MarchSlotStatus.IDLE, null)),
                List.of(new MarchSlotState(1, MarchSlotStatus.GATHERING, null))));
    }

}
