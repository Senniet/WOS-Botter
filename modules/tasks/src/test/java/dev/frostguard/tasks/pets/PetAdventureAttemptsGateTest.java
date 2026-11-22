package dev.frostguard.tasks.pets;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PetAdventureAttemptsGateTest {
    @Test void greenDailyBadgeCannotOverridePositiveNoAttemptsUntilNextReset() {
        var until = "2026-11-23T01:00:00";
        assertTrue(PetAdventureAttemptsGate.blockedUntilReset(
                until, LocalDateTime.of(2026,11,22,21,45)));
        assertTrue(PetAdventureAttemptsGate.blockedUntilReset(
                until, LocalDateTime.of(2026,11,23,0,59)));
        assertFalse(PetAdventureAttemptsGate.blockedUntilReset(
                until, LocalDateTime.of(2026,11,23,1,0)));
        assertFalse(PetAdventureAttemptsGate.blockedUntilReset(
                until, LocalDateTime.of(2026,11,23,9,0)));
    }

    @Test void unknownOrInvalidAttemptsEvidenceNeverPretendsExhausted() {
        var now = LocalDateTime.of(2026,11,22,21,0);
        assertFalse(PetAdventureAttemptsGate.blockedUntilReset("", now));
        assertFalse(PetAdventureAttemptsGate.blockedUntilReset(null, now));
        assertFalse(PetAdventureAttemptsGate.blockedUntilReset("not-a-timer", now));
    }
}
