package dev.frostguard.tasks.events;

import java.util.Collections;
import java.util.List;
import dev.frostguard.api.domain.AreaData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FishingMotionTrackerTest {
    @Test
    void catchCandidatesRequireRepeatedCurrentObservationsNotPredictedGhosts() {
        var tracker = new FishingMotionTracker();
        for (int sample = 0; sample < 3; sample++) {
            tracker.update(List.of(AreaData.of(300, 600 + sample * 20, 380, 640 + sample * 20)),
                    1000 + sample * 50, 0);
            assertEquals(sample == 2 ? 1 : 0, tracker.catchOpportunities(1000 + sample * 50, 0).size());
        }
        tracker.update(List.of(), 1200, 0);
        assertTrue(tracker.catchOpportunities(1200, 0).isEmpty());
    }
    @Test
    void motionEvidenceRequiresThreeRepeatedlyMatchedObjectsAndExpiresImmediatelyWhenUnobserved() {
        var tracker = new FishingMotionTracker();
        for (int sample = 0; sample < 3; sample++) {
            var objects = new java.util.ArrayList<AreaData>();
            for (int object = 0; object < 3; object++) {
                objects.add(AreaData.of(100 + object * 180, 300 + object * 150 + sample * 20,
                        140 + object * 180, 330 + object * 150 + sample * 20));
            }
            tracker.update(objects, 1000 + sample * 50, 0);
            if (sample < 2) assertNull(tracker.relativeVerticalVelocity());
        }
        assertTrue(tracker.relativeVerticalVelocity() > 0.1);
        tracker.update(List.of(), 1150, 0);
        assertNull(tracker.relativeVerticalVelocity(), "Predicted unmatched tracks are not fresh phase evidence");
    }

    @Test
    void refusesOverloadedForegroundRatherThanSilentlyDroppingHazards() {
        var tracker = new FishingMotionTracker();
        assertThrows(IllegalStateException.class, () -> tracker.update(
                Collections.nCopies(257, AreaData.of(100, 300, 140, 320)), 1000, 0));
    }

    @Test
    void overlappingDuplicateDetectionsDoNotExhaustTrackingSlots() {
        var tracker = new FishingMotionTracker();
        var duplicates = Collections.nCopies(200, AreaData.of(100, 300, 140, 320));
        for (int i = 0; i < 10; i++) {
            assertEquals(1, tracker.update(duplicates, 1000 + i * 50, 0).size());
        }
    }

    @Test
    void foregroundObservationsReplaceStalePredictionsWhenTrackerIsFull() {
        var tracker = new FishingMotionTracker();
        var initial = new java.util.ArrayList<AreaData>();
        for (int i = 0; i < 256; i++) {
            int x = 10 + (i % 16) * 42;
            int y = 10 + (i / 16) * 48;
            initial.add(AreaData.of(x, y, x + 12, y + 12));
        }
        assertEquals(256, tracker.update(initial, 1000, 0).size());
        // All the prior objects are unmatched, but still within the prediction TTL.
        // New current objects should never crash the game due to old predictions.
        var newObservation = List.of(AreaData.of(700, 900, 715, 915));
        var result = tracker.update(newObservation, 1050, 0);
        assertEquals(256, result.size());
        assertTrue(result.stream().anyMatch(f -> f.x() > 700 && f.y() >= 900));
    }

    @Test
    void expiresOldTracksAndKeepsFreshUnmatchedThreatsBriefly() {
        var tracker = new FishingMotionTracker();
        assertEquals(1, tracker.update(List.of(AreaData.of(100, 300, 140, 320)), 1000, 0).size());
        assertEquals(1, tracker.update(List.of(), 1200, 0).size());
        assertTrue(tracker.update(List.of(), 1500, 0).isEmpty());
    }
}
