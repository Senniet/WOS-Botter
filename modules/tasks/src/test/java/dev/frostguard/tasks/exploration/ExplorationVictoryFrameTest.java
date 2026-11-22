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
 @Test void capturedNov19LiveVictoryLocatesOnlyLeftReturnAction() throws Exception {
  var f=ExplorationLatestFrameProbeTest.frame(ImageIO.read(Objects.requireNonNull(
      getClass().getResourceAsStream(
          "/exploration/live-20261119/victory-return-city-20261119.png"))));
  assertTrue(ExplorationVictoryFrame.visible(f),"Current Victory header must be detected");
  var action=ExplorationVictoryFrame.returnToCity(f);
  assertTrue(action.isFound(),"Current left-side Return to City must be detected");
  assertTrue(action.getPoint().getX()>75 && action.getPoint().getX()<330,
      "Must never click the adjacent right-side Continue button: "+action.getPoint());
  assertTrue(action.getPoint().getY()>960 && action.getPoint().getY()<1055);
 }
 @Test void ocrMayContainOutlineSeparatorsButMustNameExactAction(){
  for(String txt:new String[]{"Return to City","Return|to/City","Return\\\\to City"})
      assertTrue(ExplorationVictoryFrame.containsReturnToCityLabel(txt),txt);
  for(String txt:new String[]{"Continue","Retry","Return to City Continue","Return to Home",null})
      assertFalse(ExplorationVictoryFrame.containsReturnToCityLabel(txt),String.valueOf(txt));
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
