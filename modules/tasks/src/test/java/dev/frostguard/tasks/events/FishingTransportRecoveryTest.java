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
    @Test void gameStateAndProgrammingFailuresAreNotMisclassified() {
        assertFalse(FishingMinigameRoutine.isTransportFailure(
                new IllegalStateException("Suspended retry stage unverified; no replacement bait authorized")));
        assertFalse(FishingMinigameRoutine.isTransportFailure(
                new IllegalStateException("Unrecognized Fishing phase")));
        assertFalse(FishingMinigameRoutine.isTransportFailure(null));
    }
}
