package dev.frostguard.tasks.combat;

final class BearTrapExecutionOutcomePolicy {

    private BearTrapExecutionOutcomePolicy() {
    }

    static boolean shouldDeferToNextWindow(BearTrapExecutionOutcome outcome) {
        return outcome == BearTrapExecutionOutcome.COMPLETED;
    }
}
