package dev.frostguard.tasks.exploration;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationBattleHudFrameTest {
 @BeforeAll static void cv() throws Exception {OpenCvPatternLocator.loadNativeLibrary();}
 @Test void movingBattleFramesRetainPositiveAutoAndSpeedControls()throws Exception {
  assertTrue(ExplorationBattleHudFrame.active(frame("battle-active-a.png")));
  assertTrue(ExplorationBattleHudFrame.active(frame("battle-active-b.png")));
 }
 @Test void victoryAndExplorationScreenMustNeverCountAsActiveBattle()throws Exception {
  assertFalse(ExplorationBattleHudFrame.active(frame("victory-return-city-redacted.png")));
  assertFalse(ExplorationBattleHudFrame.active(frame("main-title.png")));
 }
 private static RawImageData frame(String name)throws Exception{
  try(var input=Objects.requireNonNull(ExplorationBattleHudFrameTest.class.getResourceAsStream(
      "/exploration/qa-20261116/"+name))){
   return ExplorationLatestFrameProbeTest.frame(ImageIO.read(input));
  }
 }
}
