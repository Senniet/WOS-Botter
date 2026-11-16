package dev.frostguard.tasks.exploration;

import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class QuickExplorationFrameTest {
 @BeforeAll static void cv() throws Exception {OpenCvPatternLocator.loadNativeLibrary();}
 @Test void actualTaskCoachQuickProgressHasOneSafeSkipTarget() throws Exception {
  var f=frame("quick-in-progress-redacted.png");
  assertEquals(QuickExplorationFrame.State.IN_PROGRESS,QuickExplorationFrame.classify(f));
  assertTrue(QuickExplorationFrame.skipButton(f).isFound());
  assertNull(QuickExplorationFrame.exitTarget(f));
 }
 @Test void progressTemplateSurvivesMovingBattleAnimation() throws Exception {
  var early=frame("quick-in-progress-redacted.png");
  var late=frame("quick-in-progress-late-redacted.png");
  assertEquals(QuickExplorationFrame.State.IN_PROGRESS,QuickExplorationFrame.classify(early));
  assertEquals(QuickExplorationFrame.State.IN_PROGRESS,QuickExplorationFrame.classify(late),
          "The title background animates while Quick Exploration runs");
  assertTrue(QuickExplorationFrame.skipButton(late).isFound());
 }
 @Test void actualCompletedRewardsCanOnlyBeExitedNotSkipped() throws Exception {
  var f=frame("quick-completed-redacted.png");
  assertEquals(QuickExplorationFrame.State.COMPLETED,QuickExplorationFrame.classify(f));
  assertEquals(355,QuickExplorationFrame.exitTarget(f).getX());
  assertFalse(QuickExplorationFrame.skipButton(f).isFound());
 }
 @Test void explorationSquadAndOtherRewardsDoNotAuthorizeQuickClicks() throws Exception {
  for(var other:new String[]{"squad-false-dialog.png","chief-rewards.png"}){
   var f=ExplorationLatestFrameProbeTest.frame(ExplorationLatestFrameProbeTest.image(other));
   assertEquals(QuickExplorationFrame.State.OTHER,QuickExplorationFrame.classify(f),other);
   assertFalse(QuickExplorationFrame.skipButton(f).isFound());
   assertNull(QuickExplorationFrame.exitTarget(f));
  }
 }
 private static dev.frostguard.api.domain.RawImageData frame(String file)throws Exception {
  try(var in=Objects.requireNonNull(QuickExplorationFrameTest.class.getResourceAsStream(
      "/exploration/qa-20261116/"+file))){return ExplorationLatestFrameProbeTest.frame(ImageIO.read(in));}
 }
}
