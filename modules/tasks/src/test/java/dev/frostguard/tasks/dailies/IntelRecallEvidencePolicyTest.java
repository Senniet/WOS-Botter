package dev.frostguard.tasks.dailies;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntelRecallEvidencePolicyTest {
    @Test void persistentFourOfSixWithoutAnyRecallControlsMustYield() {
        int streak=0,previous=-1;
        for(int n=1;n<=3;n++) {
            streak=IntelRecallEvidencePolicy.nextMissingEvidenceStreak(streak,previous,4);
            previous=4;
            assertEquals(n,streak);
            assertEquals(n==3,IntelRecallEvidencePolicy.shouldStopUnverified(streak,4,6));
        }
    }

    @Test void newlyFreedMarchResetsUnchangedObservation() {
        assertEquals(1,IntelRecallEvidencePolicy.nextMissingEvidenceStreak(2,4,5));
        assertFalse(IntelRecallEvidencePolicy.shouldStopUnverified(1,5,6));
        assertEquals(0,IntelRecallEvidencePolicy.nextMissingEvidenceStreak(2,4,-1));
    }

    @Test void vipAndAllIdleCasesKeepExistingSuccessPolicy() {
        assertFalse(IntelRecallEvidencePolicy.shouldStopUnverified(10,5,6));
        assertFalse(IntelRecallEvidencePolicy.shouldStopUnverified(10,6,6));
        assertFalse(IntelRecallEvidencePolicy.shouldStopUnverified(10,-1,6));
    }
}
