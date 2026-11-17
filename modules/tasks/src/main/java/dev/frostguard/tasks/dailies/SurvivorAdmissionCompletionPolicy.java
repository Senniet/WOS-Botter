package dev.frostguard.tasks.dailies;

/** Completion is earned only after admission, assignment-state resolution and verified Home. */
final class SurvivorAdmissionCompletionPolicy {

    enum Assignment {
        APPLIED,
        NO_AVAILABLE_SLOTS,
        UNCONFIRMED
    }

    enum Decision {
        COMPLETE,
        RETRY
    }

    private SurvivorAdmissionCompletionPolicy() {
    }

    static Decision allocationOnly(Assignment assignment, boolean homeVerified) {
        if (assignment == null || !homeVerified) return Decision.RETRY;
        return assignment == Assignment.UNCONFIRMED ? Decision.RETRY : Decision.COMPLETE;
    }

    static Decision decide(boolean welcomeDismissed, Assignment assignment, boolean homeVerified) {
        if (!welcomeDismissed || assignment == null || !homeVerified) {
            return Decision.RETRY;
        }
        return switch (assignment) {
            case APPLIED, NO_AVAILABLE_SLOTS -> Decision.COMPLETE;
            case UNCONFIRMED -> Decision.RETRY;
        };
    }
}
