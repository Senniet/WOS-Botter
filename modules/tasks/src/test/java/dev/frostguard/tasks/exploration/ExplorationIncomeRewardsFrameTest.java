package dev.frostguard.tasks.exploration;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import dev.frostguard.vision.convert.ImageConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;
class ExplorationIncomeRewardsFrameTest {
 @BeforeAll static void cv()throws Exception {OpenCvPatternLocator.loadNativeLibrary();}
 @Test void identifiesActualNewRewardsSheet()throws Exception{
  try(var input=Objects.requireNonNull(getClass().getResourceAsStream(
      "/exploration/qa-20261116/idle-rewards-redacted.png"))){
   assertTrue(ExplorationIncomeRewardsFrame.visible(ExplorationLatestFrameProbeTest.frame(ImageIO.read(input))));
  }
 }
 @Test void oldRealIdleIncomeContainsCorrectTitleAndGreenInnerClaim()throws Exception {
  try(var input=Objects.requireNonNull(getClass().getResourceAsStream(
      "/exploration/idle-income-claim-masked.png"))){
   var frame=ExplorationLatestFrameProbeTest.frame(ImageIO.read(input));
   assertTrue(GreenLabelActionDetector.detect(ImageConverter.toBufferedImage(frame),
           new GreenLabelActionDetector.Region(213,875,507,972)).isFound());
  }
 }
 @Test void currentIdleIncomeHeaderMatchesRecorded2026Frame()throws Exception {
  try(var input=Objects.requireNonNull(getClass().getResourceAsStream(
          "/exploration/live-20261113/idle-income-modal-redacted.png"))){
   var frame=ExplorationLatestFrameProbeTest.frame(ImageIO.read(input));
   assertTrue(OpenCvPatternLocator.locatePattern(frame,
           TemplatesEnum.SERVE_EMU_IDLE_INCOME_TITLE_NOV_2026.getTemplate(),
           new PointData(210,233),new PointData(520,330),90).isFound());
  }
 }
 @Test void doesNotMistakeChiefOrQuickRewards()throws Exception{
  var chief=ExplorationLatestFrameProbeTest.frame(ExplorationLatestFrameProbeTest.image("chief-rewards.png"));
  assertFalse(ExplorationIncomeRewardsFrame.visible(chief));
  try(var input=Objects.requireNonNull(getClass().getResourceAsStream(
      "/exploration/qa-20261116/quick-completed-redacted.png"))){
   assertFalse(ExplorationIncomeRewardsFrame.visible(ExplorationLatestFrameProbeTest.frame(ImageIO.read(input))));
  }
 }
}
