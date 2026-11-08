package dev.frostguard.tasks.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorehouseProgressPolicyTest {

    @Test
    void chestCollectionCountsAsDailyMissionProgress() {
        assertTrue(StorehouseChestRoutine.hasConfirmedProgress(1, 0));
    }

    @Test
    void staminaCollectionCountsAsDailyMissionProgress() {
        assertTrue(StorehouseChestRoutine.hasConfirmedProgress(0, 1));
    }

    @Test
    void noConfirmedCollectionsDoNotCountAsProgress() {
        assertFalse(StorehouseChestRoutine.hasConfirmedProgress(0, 0));
    }

    @Test
    void unknownVisitWithoutProgressIsPropagatedAsFailure() {
        assertTrue(StorehouseChestRoutine.shouldPropagateUnknownOutcome(
                StorehouseVisitFlow.VisitState.RETRY_ON_ERROR, false));
    }

    @Test
    void partialConfirmedProgressIsPreservedEvenWhenVisitNeedsRetry() {
        assertFalse(StorehouseChestRoutine.shouldPropagateUnknownOutcome(
                StorehouseVisitFlow.VisitState.RETRY_ON_ERROR, true));
    }

    @Test
    void normalCooldownWithoutRewardsIsNotAnUnknownFailure() {
        assertFalse(StorehouseChestRoutine.shouldPropagateUnknownOutcome(
                StorehouseVisitFlow.VisitState.WAITING_COOLDOWN, false));
    }
}
