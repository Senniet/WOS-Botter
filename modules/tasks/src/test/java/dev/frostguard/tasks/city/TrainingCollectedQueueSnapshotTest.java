package dev.frostguard.tasks.city;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** One targeted verification should update only its own queue snapshot. */
class TrainingCollectedQueueSnapshotTest {
    private static TrainingRoutine.QueueSlot slot(TrainingRoutine.TroopTypeShape t,
                                                  TrainingRoutine.QueueMood m) {
        return new TrainingRoutine.QueueSlot(t, m, null);
    }

    @Test void verifiedClaimUpdatesOnlyCollectedQueueAndReusesOtherKnownStates() {
        var queues = new ArrayList<>(List.of(
                slot(TrainingRoutine.TroopTypeShape.INFANTRY, TrainingRoutine.QueueMood.COMPLETE),
                slot(TrainingRoutine.TroopTypeShape.LANCER, TrainingRoutine.QueueMood.TRAINING),
                slot(TrainingRoutine.TroopTypeShape.MARKSMAN, TrainingRoutine.QueueMood.IDLE)));
        var beforeLancer=queues.get(1);
        var beforeMarksman=queues.get(2);
        assertTrue(TrainingRoutine.recordVerifiedCollectedQueue(queues, 0,
                slot(TrainingRoutine.TroopTypeShape.INFANTRY, TrainingRoutine.QueueMood.IDLE)));
        assertEquals(TrainingRoutine.QueueMood.IDLE, queues.get(0).status());
        assertSame(beforeLancer,queues.get(1),"Lancer must not be rescanned/reassigned");
        assertSame(beforeMarksman,queues.get(2),"Marksman must not be rescanned/reassigned");
    }

    @Test void unknownTrainingOrWrongTroopCannotClaimCompleted() {
        var queues=new ArrayList<>(List.of(
                slot(TrainingRoutine.TroopTypeShape.INFANTRY, TrainingRoutine.QueueMood.COMPLETE)));
        for(var mood:List.of(TrainingRoutine.QueueMood.UNKNOWN,TrainingRoutine.QueueMood.COMPLETE,
                TrainingRoutine.QueueMood.TRAINING,TrainingRoutine.QueueMood.UPGRADING)) {
            assertFalse(TrainingRoutine.recordVerifiedCollectedQueue(queues,0,
                    slot(TrainingRoutine.TroopTypeShape.INFANTRY,mood)));
        }
        assertFalse(TrainingRoutine.recordVerifiedCollectedQueue(queues,0,
                slot(TrainingRoutine.TroopTypeShape.LANCER,TrainingRoutine.QueueMood.IDLE)));
        assertFalse(TrainingRoutine.recordVerifiedCollectedQueue(queues,-1,
                slot(TrainingRoutine.TroopTypeShape.INFANTRY,TrainingRoutine.QueueMood.IDLE)));
        assertEquals(TrainingRoutine.QueueMood.COMPLETE,queues.get(0).status());
    }

    @Test void cannotCollectAQueueTwiceUsingStaleVerification() {
        var queues=new ArrayList<>(List.of(
                slot(TrainingRoutine.TroopTypeShape.INFANTRY,TrainingRoutine.QueueMood.COMPLETE)));
        var idle=slot(TrainingRoutine.TroopTypeShape.INFANTRY,TrainingRoutine.QueueMood.IDLE);
        assertTrue(TrainingRoutine.recordVerifiedCollectedQueue(queues,0,idle));
        assertFalse(TrainingRoutine.recordVerifiedCollectedQueue(queues,0,idle));
    }
}
