package dev.frostguard.tasks.dailies;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
class IntelJourneyViewLiveFrameTest {
 @BeforeAll static void load() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }
 @Test void liveJourneyViewIsRecognizedAtCurrentPopup() throws Exception {
  byte[] p=Objects.requireNonNull(getClass().getResourceAsStream("/intel/live-20261113/journey-view-masked.png")).readAllBytes();
  var m=OpenCvPatternLocator.locatePattern(p,TemplatesEnum.INTEL_VIEW.getTemplate(),new PointData(205,850),new PointData(535,1015),88);
  org.junit.jupiter.api.Assertions.assertTrue(m.isFound());
 }
 @Test void fightAppearsOnSquadSettingsButNotBeforeIt() throws Exception {
  byte[] positive=Objects.requireNonNull(getClass().getResourceAsStream("/intel/live-20261113/journey-squad-fight-masked.png")).readAllBytes();
  byte[] negative=Objects.requireNonNull(getClass().getResourceAsStream("/intel/live-20261113/journey-before-squad-masked.png")).readAllBytes();
  var yes=OpenCvPatternLocator.locatePattern(positive, TemplatesEnum.INTEL_JOURNEY_FIGHT_LIVE.getTemplate(),new PointData(365,1145),new PointData(705,1275),90);
  var no=OpenCvPatternLocator.locatePattern(negative, TemplatesEnum.INTEL_JOURNEY_FIGHT_LIVE.getTemplate(),new PointData(365,1145),new PointData(705,1275),90);
  org.junit.jupiter.api.Assertions.assertTrue(yes.isFound(),"Fight action must match saved Squad Settings screenshot");
  org.junit.jupiter.api.Assertions.assertFalse(no.isFound(),"Fight may not be detected on the earlier Explore modal");
 }
 @Test void fightRegionAndVictoryExitAreGroundedInLiveSequence() {
  var fight=IntelligenceRoutine.INTEL_JOURNEY_FIGHT_ACTION_SEARCH;
  org.junit.jupiter.api.Assertions.assertEquals(90,fight.getThreshold());
  org.junit.jupiter.api.Assertions.assertEquals(365,fight.getArea().topLeft().getX());
  org.junit.jupiter.api.Assertions.assertEquals(1145,fight.getArea().topLeft().getY());
  var exit=IntelligenceRoutine.journeyVictoryContinueArea();
  org.junit.jupiter.api.Assertions.assertTrue(exit.topLeft().getY()>=1100,
    "Victory footer tap must be below the reward content");
 }
}
