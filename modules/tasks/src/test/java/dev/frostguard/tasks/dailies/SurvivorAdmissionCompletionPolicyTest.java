package dev.frostguard.tasks.dailies;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SurvivorAdmissionCompletionPolicyTest {

    @Test
    void welcomeAndBlueToGreyAllocationRequireVerifiedHomeBeforeSuccess() {
        assertEquals(SurvivorAdmissionCompletionPolicy.Decision.COMPLETE,
                SurvivorAdmissionCompletionPolicy.decide(true,
                        SurvivorAdmissionCompletionPolicy.Assignment.APPLIED, true));
        assertEquals(SurvivorAdmissionCompletionPolicy.Decision.RETRY,
                SurvivorAdmissionCompletionPolicy.decide(true,
                        SurvivorAdmissionCompletionPolicy.Assignment.APPLIED, false));
    }

    @Test
    void fullyOccupiedWorkSlotsAreValidAfterVerifiedAdmissionAndHomeReturn() {
        assertEquals(SurvivorAdmissionCompletionPolicy.Decision.COMPLETE,
                SurvivorAdmissionCompletionPolicy.decide(true,
                        SurvivorAdmissionCompletionPolicy.Assignment.NO_AVAILABLE_SLOTS, true));
        assertEquals(SurvivorAdmissionCompletionPolicy.Decision.RETRY,
                SurvivorAdmissionCompletionPolicy.decide(false,
                        SurvivorAdmissionCompletionPolicy.Assignment.NO_AVAILABLE_SLOTS, true));
    }

    @Test
    void unchangedBlueButtonOrUnknownStateNeverRegistersCompletion() {
        assertEquals(SurvivorAdmissionCompletionPolicy.Decision.RETRY,
                SurvivorAdmissionCompletionPolicy.decide(true,
                        SurvivorAdmissionCompletionPolicy.Assignment.UNCONFIRMED, true));
        assertEquals(SurvivorAdmissionCompletionPolicy.Decision.RETRY,
                SurvivorAdmissionCompletionPolicy.decide(true, null, true));
    }
}
