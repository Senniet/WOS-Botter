package dev.frostguard.tasks.exploration;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationRootFrameTest {
 @BeforeAll static void cv() throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
 @Test void bothCurrentRootTabsCanUseSameDirectExplorationShortcut()throws Exception {
  assertEquals(ExplorationRootFrame.Root.CITY,root("home-city-anchor-redacted.png"));
  assertEquals(ExplorationRootFrame.Root.WORLD,root("home-world-anchor-redacted.png"));
  assertTrue(ExplorationRootFrame.stable(ExplorationRootFrame.Root.CITY,ExplorationRootFrame.Root.CITY));
  assertTrue(ExplorationRootFrame.stable(ExplorationRootFrame.Root.WORLD,ExplorationRootFrame.Root.WORLD));
  assertFalse(ExplorationRootFrame.stable(ExplorationRootFrame.Root.CITY,ExplorationRootFrame.Root.WORLD));
  assertFalse(ExplorationRootFrame.stable(ExplorationRootFrame.Root.UNKNOWN,ExplorationRootFrame.Root.UNKNOWN));
 }
 @Test void worldRootStillWorksWhenCurrentColourChangesToGrey()throws Exception {
  try(var stream=Objects.requireNonNull(ExplorationRootFrameTest.class.getResourceAsStream(
       "/exploration/qa-20261116/world-grayscale-redacted.png"))){
   var f=ExplorationLatestFrameProbeTest.frame(ImageIO.read(stream));
   assertEquals(ExplorationRootFrame.Root.WORLD,ExplorationRootFrame.root(f),
           "Keep real recorded mono/scaled fallback for current World anchor");
  }
 }
 @Test void unknownScreenCannotAuthorizeShortcut()throws Exception {
  var f=ExplorationLatestFrameProbeTest.frame(ExplorationLatestFrameProbeTest.image("squad-false-dialog.png"));
  assertEquals(ExplorationRootFrame.Root.UNKNOWN,ExplorationRootFrame.root(f));
 }
 private static ExplorationRootFrame.Root root(String name)throws Exception{
  try(var in=Objects.requireNonNull(ExplorationRootFrameTest.class.getResourceAsStream("/initialize/"+name))){
   RawImageData f=ExplorationLatestFrameProbeTest.frame(ImageIO.read(in));
   return ExplorationRootFrame.root(f);
  }
 }
}
