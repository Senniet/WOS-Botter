package dev.frostguard.tasks.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.frostguard.tasks.economy.GatherRoutine.ActiveGatherMarchCandidate;
import dev.frostguard.tasks.economy.GatherRoutine.GatherType;
import dev.frostguard.tasks.economy.GatherRoutine.RecallAttempt;
import dev.frostguard.api.domain.MarchSlotState;
import dev.frostguard.api.domain.MarchSlotStatus;

class GatherRecallBatchTest {

    @Test
    void reducedQueueLimitBlocksNewDeploymentsWithoutRequiringRecall() {
        assertTrue(GatherRoutine.hasReachedConfiguredQueueLimit(3, 1));
        assertTrue(GatherRoutine.hasReachedConfiguredQueueLimit(1, 1));
        assertFalse(GatherRoutine.hasReachedConfiguredQueueLimit(0, 1));
    }

    @Test
    void highPriorityRecallStopsAfterFirstScanWithoutControls() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 13, 1, 0);
        List<ActiveGatherMarchCandidate> candidates = List.of(
                new ActiveGatherMarchCandidate(GatherType.MEAT, 0, now.plusHours(2)),
                new ActiveGatherMarchCandidate(GatherType.WOOD, 1, now.plusHours(3)));
        AtomicInteger attempts = new AtomicInteger();

        var result = GatherRoutine.executeRecallBatch(candidates, ignored -> {
            attempts.incrementAndGet();
            return RecallAttempt.CONTROLS_NOT_FOUND;
        });

        assertEquals(1, attempts.get());
        assertEquals(0, result.recalled());
        assertTrue(result.controlsMissing());
    }

    @Test
    void unknownRecallOutcomeStopsTheBatchWithoutSendingAnotherRecall() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 13, 1, 0);
        List<ActiveGatherMarchCandidate> candidates = List.of(
                new ActiveGatherMarchCandidate(GatherType.MEAT, 0, now.plusHours(2)),
                new ActiveGatherMarchCandidate(GatherType.WOOD, 1, now.plusHours(3)));
        AtomicInteger attempts = new AtomicInteger();

        var result = GatherRoutine.executeRecallBatch(candidates, ignored -> {
            attempts.incrementAndGet();
            return RecallAttempt.UNKNOWN_OUTCOME;
        });

        assertEquals(1, attempts.get());
        assertEquals(0, result.recalled());
        assertTrue(result.unknownOutcome());
        assertFalse(result.controlsMissing());
    }

    @Test
    void confirmsRecallWhenMarchQueueShowsReturning() {
        assertTrue(GatherRoutine.isRecallConfirmed(
                new MarchSlotState(1, MarchSlotStatus.GATHERING, null),
                new MarchSlotState(1, MarchSlotStatus.RETURNING, null)));
    }

    @Test
    void confirmsRecallWhenMarchQueueShowsIdle() {
        assertTrue(GatherRoutine.isRecallConfirmed(
                new MarchSlotState(1, MarchSlotStatus.GATHERING, null),
                new MarchSlotState(1, MarchSlotStatus.IDLE, null)));
    }

    @Test
    void doesNotConfirmRecallWhenMarchIsStillGathering() {
        assertFalse(GatherRoutine.isRecallConfirmed(
                new MarchSlotState(1, MarchSlotStatus.GATHERING, null),
                new MarchSlotState(1, MarchSlotStatus.GATHERING, null)));
    }

    @Test
    void doesNotConfirmRecallWhenPostActionStateIsUnknown() {
        assertFalse(GatherRoutine.isRecallConfirmed(
                new MarchSlotState(1, MarchSlotStatus.GATHERING, null),
                new MarchSlotState(1, MarchSlotStatus.BUSY_UNKNOWN, null)));
    }

    @Test
    void doesNotCountAMarchThatWasAlreadyReturning() {
        assertFalse(GatherRoutine.isRecallConfirmed(
                new MarchSlotState(1, MarchSlotStatus.RETURNING, null),
                new MarchSlotState(1, MarchSlotStatus.RETURNING, null)));
    }

    @Test
    void countsOnlySlotsWithConfirmedRecallTransitions() {
        List<MarchSlotState> before = List.of(
                new MarchSlotState(1, MarchSlotStatus.GATHERING, null),
                new MarchSlotState(2, MarchSlotStatus.STATIONED, null));
        List<MarchSlotState> after = List.of(
                new MarchSlotState(1, MarchSlotStatus.RETURNING, null),
                new MarchSlotState(2, MarchSlotStatus.STATIONED, null));

        assertEquals(1, GatherRoutine.countConfirmedRecallTransitions(before, after));
    }

    @Test
    void excludesAlreadyReturningGatherMarchesFromRecallCandidates() {
        assertFalse(GatherRoutine.isRecallCandidate(
                new MarchSlotState(1, MarchSlotStatus.RETURNING, null)));
    }

    @Test
    void excludesUnknownGatherMarchPhasesFromRecallCandidates() {
        assertFalse(GatherRoutine.isRecallCandidate(
                new MarchSlotState(1, MarchSlotStatus.BUSY_UNKNOWN, null)));
    }

    @Test
    void acceptsWorkingGatherMarchesAsRecallCandidates() {
        assertTrue(GatherRoutine.isRecallCandidate(
                new MarchSlotState(1, MarchSlotStatus.GATHERING, null)));
    }
    @Test
    void troopReturnWaitIncludesGatherMarchesAlreadyReturning() {
        List<MarchSlotState> slots = List.of(
                new MarchSlotState(1, MarchSlotStatus.RETURNING, null),
                new MarchSlotState(2, MarchSlotStatus.GATHERING, null),
                new MarchSlotState(3, MarchSlotStatus.STATIONED, null));

        assertEquals(2, GatherRoutine.collectActiveGatherMarchSlots(slots).size());
    }

    @Test
    void doesNotTreatAnEmptyMarchQueueReadAsProofThatTroopsAreHome() {
        assertTrue(GatherRoutine.hasUnknownMarchQueueState(List.of()));
    }

    @Test
    void keepsRecallWaitActiveWhenAnyMarchQueueSlotIsUnknown() {
        assertTrue(GatherRoutine.hasUnknownMarchQueueState(List.of(
                new MarchSlotState(1, MarchSlotStatus.IDLE, null),
                new MarchSlotState(2, MarchSlotStatus.BUSY_UNKNOWN, null))));
    }

    @Test
    void recognizesACompleteKnownMarchQueueSnapshot() {
        assertFalse(GatherRoutine.hasUnknownMarchQueueState(List.of(
                new MarchSlotState(1, MarchSlotStatus.IDLE, null),
                new MarchSlotState(2, MarchSlotStatus.STATIONED, null))));
    }

}
