package dev.frostguard.tasks.pets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;

class PetAdventureStartOutcomeTest {

    @Test
    void rechecksAnUnconfirmedStartAfterTenMinutesWithoutTappingAgain() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 28, 12, 0);

        assertEquals(now.plusMinutes(20), PetAdventureChestRoutine.unverifiedStartRecheck(now));
    }

    @Test
    void timedPendingStateSurvivesRestartsAndLegacyCoordinatesStillParse() {
        var now=java.time.Instant.parse("2026-11-22T14:44:00Z");
        var pos=new PointData(222,308);
        String saved=PetAdventureChestRoutine.encodeTimedPendingStart(pos,now);
        assertEquals(pos,PetAdventureChestRoutine.parsePendingStart(saved));
        assertEquals(now,PetAdventureChestRoutine.parsePendingStartInstant(saved));
        assertEquals(pos,PetAdventureChestRoutine.parsePendingStart("222,308"));
        assertEquals(null,PetAdventureChestRoutine.parsePendingStartInstant("222,308"));
        assertEquals(null,PetAdventureChestRoutine.parsePendingStartInstant("malformed"));
    }

    @Test
    void pendingStartCannotBlockIndefinitelyButExpiresOnlyAfterFullAdventure() {
        var begin=java.time.Instant.parse("2026-11-22T10:00:00Z");
        assertFalse(PetAdventureDecisions.pendingHasExpired(begin,begin.plusSeconds(2*3600)));
        assertFalse(PetAdventureDecisions.pendingHasExpired(begin,begin.plusSeconds(3*3600)));
        assertTrue(PetAdventureDecisions.pendingHasExpired(begin,begin.plusSeconds(3*3600+15*60)));
        assertFalse(PetAdventureDecisions.pendingHasExpired(null,begin));
    }

    @Test
    void treatsATimerUnderThePinAsAnOccupiedChest() {
        PointData chest = new PointData(180, 360);
        ImageSearchResultData timer = ImageSearchResultData.hit(175, 450, 95);
        assertTrue(PetAdventureDecisions.occupiedByTimer(chest, List.of(timer)));
    }

    @Test
    void leavesAChestIdleWhenTheTimerBelongsToAnotherPin() {
        PointData chest = new PointData(600, 700);
        ImageSearchResultData timer = ImageSearchResultData.hit(175, 450, 95);
        assertFalse(PetAdventureDecisions.occupiedByTimer(chest, List.of(timer)));
    }

    @Test
    void preservesExactPendingChestPositionAndRejectsInvalidRecords() {
        PointData initial = new PointData(602, 803);
        PointData restored = PetAdventureChestRoutine.parsePendingStart(
                PetAdventureChestRoutine.encodePendingStart(initial));
        assertEquals(initial.getX(), restored.getX());
        assertEquals(initial.getY(), restored.getY());
        assertEquals(null, PetAdventureChestRoutine.parsePendingStart("nope"));
        assertEquals(null, PetAdventureChestRoutine.parsePendingStart("720,1280"));
        assertEquals(null, PetAdventureChestRoutine.parsePendingStart(""));
    }
}
