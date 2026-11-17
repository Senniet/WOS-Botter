package dev.frostguard.tasks.heroes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeroRecruitmentClaimOutcomeTest {

    @Test
    void confirmedClaimCountsAsDailyMissionProgress() {
        assertTrue(HeroRecruitmentRoutine.countsAsProgress(HeroRecruitmentRoutine.ClaimOutcome.CONFIRMED));
    }

    @Test
    void unavailableClaimDoesNotCountAsProgress() {
        assertFalse(HeroRecruitmentRoutine.countsAsProgress(HeroRecruitmentRoutine.ClaimOutcome.NOT_AVAILABLE));
    }

    @Test
    void unknownClaimDoesNotCountAsProgress() {
        assertFalse(HeroRecruitmentRoutine.countsAsProgress(HeroRecruitmentRoutine.ClaimOutcome.UNKNOWN));
    }

    @Test
    void unknownClaimUsesBoundedRetryInsteadOfAnotherTap() {
        assertTrue(HeroRecruitmentRoutine.shouldUseUnknownRetry(HeroRecruitmentRoutine.ClaimOutcome.UNKNOWN));
        assertFalse(HeroRecruitmentRoutine.shouldUseUnknownRetry(HeroRecruitmentRoutine.ClaimOutcome.CONFIRMED));
        assertFalse(HeroRecruitmentRoutine.shouldUseUnknownRetry(HeroRecruitmentRoutine.ClaimOutcome.NOT_AVAILABLE));
    }

    @Test
    void unknownOutcomeFailsOnlyWhenNoRecruitmentClaimWasConfirmed() {
        assertTrue(HeroRecruitmentRoutine.shouldFailOnUnknownOutcome(true, false));
        assertFalse(HeroRecruitmentRoutine.shouldFailOnUnknownOutcome(true, true));
        assertFalse(HeroRecruitmentRoutine.shouldFailOnUnknownOutcome(false, false));
    }
}
