package dev.frostguard.tasks.exploration;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationVictoryFrameTest {
 @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
 @Test void actual2026VictoryRequiresSpecificReturnToCityAction()throws Exception{
  var f=frame("victory-return-city-redacted.png");
  var match=ExplorationVictoryFrame.returnToCity(f);
  assertTrue(match.isFound(),"Actual victory result must expose its Return to City button");
  assertTrue(match.getPoint().getY()>940 && match.getPoint().getY()<1060);
 }
 @Test void battleFrameAndExplorationMenuCannotAuthorizeReturnToCity()throws Exception{
  var f=frame("main-title.png");
  assertFalse(ExplorationVictoryFrame.returnToCity(f).isFound());
  var squad=ExplorationLatestFrameProbeTest.frame(ExplorationLatestFrameProbeTest.image("squad-false-dialog.png"));
  assertFalse(ExplorationVictoryFrame.returnToCity(squad).isFound());
 }
 private static RawImageData frame(String file)throws Exception{
  try(var in=Objects.requireNonNull(ExplorationVictoryFrameTest.class.getResourceAsStream(
      "/exploration/qa-20261116/"+file))){
   return ExplorationLatestFrameProbeTest.frame(ImageIO.read(in));
  }
 }
}
