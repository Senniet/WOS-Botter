package dev.frostguard.tasks.city;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingProgressConfirmationTest {

    private static final LocalDateTime READY_AT = LocalDateTime.of(2026, 11, 8, 13, 0);

    @Test
    void confirmsProgressWhenAnIdleQueueIsObservedTraining() {
        assertTrue(TrainingRoutine.hasConfirmedTrainingProgress(
                List.of(new TrainingRoutine.QueueSlot(
                        TrainingRoutine.TroopTypeShape.INFANTRY, TrainingRoutine.QueueMood.IDLE, null)),
                List.of(new TrainingRoutine.QueueSlot(
                        TrainingRoutine.TroopTypeShape.INFANTRY, TrainingRoutine.QueueMood.TRAINING, READY_AT))));
    }

    @Test
    void confirmsProgressWhenACompletedQueueIsObservedTraining() {
        assertTrue(TrainingRoutine.hasConfirmedTrainingProgress(
                List.of(new TrainingRoutine.QueueSlot(
                        TrainingRoutine.TroopTypeShape.LANCER, TrainingRoutine.QueueMood.COMPLETE, null)),
                List.of(new TrainingRoutine.QueueSlot(
                        TrainingRoutine.TroopTypeShape.LANCER, TrainingRoutine.QueueMood.TRAINING, READY_AT))));
    }

    @Test
    void doesNotCountAQueueThatWasAlreadyTraining() {
        assertFalse(TrainingRoutine.hasConfirmedTrainingProgress(
                List.of(new TrainingRoutine.QueueSlot(
                        TrainingRoutine.TroopTypeShape.MARKSMAN, TrainingRoutine.QueueMood.TRAINING, READY_AT)),
                List.of(new TrainingRoutine.QueueSlot(
                        TrainingRoutine.TroopTypeShape.MARKSMAN, TrainingRoutine.QueueMood.TRAINING, READY_AT))));
    }

    @Test
    void doesNotCountAnUnknownPostActionQueueState() {
        assertFalse(TrainingRoutine.hasConfirmedTrainingProgress(
                List.of(new TrainingRoutine.QueueSlot(
                        TrainingRoutine.TroopTypeShape.INFANTRY, TrainingRoutine.QueueMood.COMPLETE, null)),
                List.of(new TrainingRoutine.QueueSlot(
                        TrainingRoutine.TroopTypeShape.INFANTRY, TrainingRoutine.QueueMood.UNKNOWN, null))));
    }

    @Test
    void doesNotCountTrainingInAQueueThatWasNotAttempted() {
        assertFalse(TrainingRoutine.hasConfirmedTrainingProgress(
                List.of(new TrainingRoutine.QueueSlot(
                        TrainingRoutine.TroopTypeShape.INFANTRY, TrainingRoutine.QueueMood.IDLE, null)),
                List.of(new TrainingRoutine.QueueSlot(
                        TrainingRoutine.TroopTypeShape.LANCER, TrainingRoutine.QueueMood.TRAINING, READY_AT))));
    }

    @Test
    void doesNotCountWhenNoEligibleQueueWasAttempted() {
        assertFalse(TrainingRoutine.hasConfirmedTrainingProgress(
                List.of(),
                List.of(new TrainingRoutine.QueueSlot(
                        TrainingRoutine.TroopTypeShape.INFANTRY, TrainingRoutine.QueueMood.TRAINING, READY_AT))));
    }
}
