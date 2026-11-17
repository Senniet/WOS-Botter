package dev.frostguard.tasks.exploration;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationEntryWaitTest {
 @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
 @Test void actual20261116OpeningAnimationIsNotAnExplorationFailure()throws Exception {
  var transition=frame("entry-transition-redacted.png");
  var loaded=frame("entry-loaded-redacted.png");
  assertFalse(ExplorationEntryFrame.ready(transition));
  assertTrue(ExplorationEntryFrame.ready(loaded));
  var screens=List.of(transition,loaded,loaded);
  var i=new AtomicInteger();var settles=new AtomicInteger();
  assertTrue(ExplorationEntryWait.untilReady(
      ()->screens.get(Math.min(i.getAndIncrement(),2)),settles::incrementAndGet,6));
  assertEquals(3,i.get());
  assertEquals(2,settles.get());
 }
 @Test void noMoreClicksOnUnknownOrUnstablePage()throws Exception {
  var transition=frame("entry-transition-redacted.png");
  var loaded=frame("entry-loaded-redacted.png");
  var i=new AtomicInteger();
  assertFalse(ExplorationEntryWait.untilReady(()->i.getAndIncrement()%2==0?loaded:transition,
      ()->{},6));
  assertFalse(ExplorationEntryWait.untilReady(()->transition,()->{},4));
 }
 private static RawImageData frame(String f)throws Exception{
  try(var input=Objects.requireNonNull(getClassStream(f))){
   return ExplorationLatestFrameProbeTest.frame(ImageIO.read(input));
  }
 }
 private static java.io.InputStream getClassStream(String name){
  return ExplorationEntryWaitTest.class.getResourceAsStream("/exploration/qa-20261116/"+name);
 }
}
