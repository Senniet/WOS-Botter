package dev.frostguard.tasks.pets;
import dev.frostguard.api.domain.PointData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PetAdventureChestPositionTest {
 @Test void detectsPersistingCompletedChestWithoutConfusingMovedSpot() {
  assertTrue(PetAdventureChestRoutine.sameChestLocation(new PointData(480,625),new PointData(488,638)));
  assertFalse(PetAdventureChestRoutine.sameChestLocation(new PointData(480,625),new PointData(635,625)));
 }
}
