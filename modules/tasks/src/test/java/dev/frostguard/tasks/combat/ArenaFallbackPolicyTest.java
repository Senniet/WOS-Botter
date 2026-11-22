package dev.frostguard.tasks.combat;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaFallbackPolicyTest {
    record Candidate(int slot, boolean green, double power) {}

    private static List<Candidate> sort(List<Candidate> allowed) {
        return ArenaFallbackPolicy.preferGreenBeforeRed(allowed, Candidate::green,
                Comparator.comparingDouble(Candidate::power));
    }

    @Test void greenAlwaysWinsEvenIfRedHasLowerReportedPower() {
        assertEquals(2,sort(List.of(new Candidate(1,false,1.0),
                new Candidate(2,true,3.0))).get(0).slot());
    }

    @Test void allRedFallsBackToLowestRecognizedPower() {
        assertEquals(3,sort(List.of(new Candidate(1,false,9.0),
                new Candidate(2,false,8.0),new Candidate(3,false,4.0)))
                .get(0).slot());
    }

    @Test void strongerIsRejectedUntilLastResort() {
        assertFalse(ArenaFallbackPolicy.selectablePower(false,true,false));
        assertTrue(ArenaFallbackPolicy.selectablePower(false,true,true));
        assertTrue(ArenaFallbackPolicy.selectablePower(true,false,false));
        assertTrue(ArenaFallbackPolicy.selectablePower(true,false,true));
    }

    @Test void unidentifiedStrengthNeverAllowsAttack() {
        assertFalse(ArenaFallbackPolicy.selectablePower(false,false,true));
    }

    @Test void noVerifiedCandidatesMeansNoBlindBattle() {
        assertTrue(sort(List.of()).isEmpty());
    }
}
