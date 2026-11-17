package dev.frostguard.tasks.exploration;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class QuickExplorationFlowTest {
 @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
 @Test void skipsOnlyVerifiedQuickProgressThenClosesVerifiedCompletedRewards() throws Exception {
  var progress=frame("quick-in-progress-redacted.png");
  var completed=frame("quick-completed-redacted.png");
  var main=frame("main-title.png");
  List<RawImageData> frames=List.of(progress,progress,completed,completed,main);
  var idx=new AtomicInteger();var taps=new java.util.ArrayList<PointData>();
  var result=QuickExplorationFlow.finish(()->frames.get(Math.min(idx.getAndIncrement(),frames.size()-1)),
          taps::add,()->{});
  assertEquals(QuickExplorationFlow.Outcome.REWARDS_CLOSED,result);
  assertEquals(2,taps.size());
  assertTrue(taps.get(0).getY()>1100,"First tap must be real Skip CTA");
  assertEquals(new PointData(355,1155),taps.get(1));
 }
 @Test void resumedCompletedOverlayClosesWithoutSkipOrFight() throws Exception {
  var completed=frame("quick-completed-redacted.png");var main=frame("main-title.png");
  var idx=new AtomicInteger();var taps=new java.util.ArrayList<PointData>();
  var sequence=List.of(completed,completed,main);
  assertEquals(QuickExplorationFlow.Outcome.REWARDS_CLOSED,
      QuickExplorationFlow.finish(()->sequence.get(Math.min(idx.getAndIncrement(),2)),taps::add,()->{}));
  assertEquals(List.of(new PointData(355,1155)),taps);
 }
 @Test void unrelatedOrChangingScreensNeverReceiveBlindClicks() throws Exception {
  var squad=ExplorationLatestFrameProbeTest.frame(ExplorationLatestFrameProbeTest.image("squad-false-dialog.png"));
  var completed=frame("quick-completed-redacted.png");
  var taps=new AtomicInteger();
  assertEquals(QuickExplorationFlow.Outcome.UNVERIFIED,
      QuickExplorationFlow.finish(()->squad,x->taps.incrementAndGet(),()->{}));
  assertEquals(0,taps.get());
  var reads=new AtomicInteger();
  assertEquals(QuickExplorationFlow.Outcome.UNVERIFIED,
      QuickExplorationFlow.finish(()->reads.getAndIncrement()==0?completed:squad,
          x->taps.incrementAndGet(),()->{}));
  assertEquals(0,taps.get());
 }
 @Test void rewardsNotClosedCannotBeMarkedComplete() throws Exception {
  var completed=frame("quick-completed-redacted.png");var taps=new AtomicInteger();
  assertEquals(QuickExplorationFlow.Outcome.UNVERIFIED,
      QuickExplorationFlow.finish(()->completed,x->taps.incrementAndGet(),()->{}));
  assertEquals(1,taps.get());
 }
 private static RawImageData frame(String file)throws Exception{
  try(var in=Objects.requireNonNull(QuickExplorationFlowTest.class.getResourceAsStream(
      "/exploration/qa-20261116/"+file))){return ExplorationLatestFrameProbeTest.frame(ImageIO.read(in));}
 }
}
