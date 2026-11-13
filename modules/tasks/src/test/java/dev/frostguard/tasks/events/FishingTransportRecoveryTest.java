package dev.frostguard.tasks.events;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FishingTransportRecoveryTest {
    @Test void knownVideoOrInputFailuresAreRecoverable() {
        for (String message : new String[]{
                "Android video stream ended",
                "No fresh Fishing frame within five seconds",
                "Fishing observation stale; no input sent",
                "Android touch session unavailable"}) {
            assertTrue(FishingMinigameRoutine.isTransportFailure(
                    new IllegalStateException(message)), message);
        }
    }
    @Test void uncertainGoFishResumptionIsDeferredWithoutNewBait() {
        for (String reason : new String[]{
                "Go Fish resume action unverified; no bait selected",
                "Go Fish not stable across fresh frames; no bait selected",
                "Fishing retry control timed out: FISHING_PAUSE"}) {
            assertTrue(FishingMinigameRoutine.isFishingStageUnverified(
                    new IllegalStateException(reason)));
        }
        assertFalse(FishingMinigameRoutine.isFishingStageUnverified(
                new IllegalStateException("Normal Cast failed")));
    }

    @Test void gameStateAndProgrammingFailuresAreNotMisclassified() {
        assertFalse(FishingMinigameRoutine.isTransportFailure(
                new IllegalStateException("Suspended retry stage unverified; no replacement bait authorized")));
        assertFalse(FishingMinigameRoutine.isTransportFailure(
                new IllegalStateException("Unrecognized Fishing phase")));
        assertFalse(FishingMinigameRoutine.isTransportFailure(null));
    }
}
