package dev.frostguard.tasks.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BearTrapExecutionOutcomePolicyTest {

    @Test
    void defersToNextEventWindowAfterCompletedExecution() {
        assertTrue(BearTrapExecutionOutcomePolicy.shouldDeferToNextWindow(
                BearTrapExecutionOutcome.COMPLETED));
    }

    @Test
    void doesNotDeferFailedExecutionAsIfTheEventCompleted() {
        assertFalse(BearTrapExecutionOutcomePolicy.shouldDeferToNextWindow(
                BearTrapExecutionOutcome.FAILED));
    }

    @Test
    void doesNotDeferPreemptedExecutionAsIfTheEventCompleted() {
        assertFalse(BearTrapExecutionOutcomePolicy.shouldDeferToNextWindow(
                BearTrapExecutionOutcome.PREEMPTED));
    }
}
