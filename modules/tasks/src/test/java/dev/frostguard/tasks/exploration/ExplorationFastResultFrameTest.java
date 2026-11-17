package dev.frostguard.tasks.exploration;

import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationFastResultFrameTest {
 @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
 @Test void actualBattleAndResultCanBeClassifiedFromOneFrame()throws Exception{
  assertEquals(ExplorationFastResultFrame.State.FIGHTING, classify("battle-active-a.png"));
  assertEquals(ExplorationFastResultFrame.State.FIGHTING, classify("battle-active-b.png"));
  assertEquals(ExplorationFastResultFrame.State.VICTORY, classify("victory-return-city-redacted.png"));
  assertEquals(ExplorationFastResultFrame.State.UNKNOWN, classify("main-title.png"));
 }
 @Test void noTapMayFollowOneUnconfirmedMatch(){
  assertFalse(ExplorationFastResultFrame.sameConfirmedResult(
          ExplorationFastResultFrame.State.VICTORY,ExplorationFastResultFrame.State.FIGHTING));
  assertFalse(ExplorationFastResultFrame.sameConfirmedResult(
          ExplorationFastResultFrame.State.UNKNOWN,ExplorationFastResultFrame.State.UNKNOWN));
  assertTrue(ExplorationFastResultFrame.sameConfirmedResult(
          ExplorationFastResultFrame.State.VICTORY,ExplorationFastResultFrame.State.VICTORY));
 }
 private static ExplorationFastResultFrame.State classify(String file)throws Exception{
  var path=file.equals("main-title.png")?"/exploration/qa-20261116/main-title.png":
          "/exploration/qa-20261116/"+file;
  try(var in=Objects.requireNonNull(ExplorationFastResultFrameTest.class.getResourceAsStream(path))){
   return ExplorationFastResultFrame.classify(
       ExplorationLatestFrameProbeTest.frame(ImageIO.read(in)),false);
  }
 }
}
