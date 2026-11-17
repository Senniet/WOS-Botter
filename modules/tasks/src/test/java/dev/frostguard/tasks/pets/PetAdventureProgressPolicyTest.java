package dev.frostguard.tasks.pets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetAdventureProgressPolicyTest {

    @Test
    void countsOnlyAStartConfirmedByTheInAdventureState() {
        assertTrue(PetAdventureChestRoutine.countsAsProgress(
                PetAdventureChestRoutine.ChestStartResult.STARTED));
    }

    @Test
    void doesNotCountExhaustedAttemptsAsProgress() {
        assertFalse(PetAdventureChestRoutine.countsAsProgress(
                PetAdventureChestRoutine.ChestStartResult.NO_ATTEMPTS));
    }

    @Test
    void doesNotCountMissingChestAsProgress() {
        assertFalse(PetAdventureChestRoutine.countsAsProgress(
                PetAdventureChestRoutine.ChestStartResult.NOT_FOUND));
    }

    @Test
    void doesNotCountUnknownStartOutcomeAsProgress() {
        assertFalse(PetAdventureChestRoutine.countsAsProgress(
                PetAdventureChestRoutine.ChestStartResult.UNKNOWN));
    }
}
