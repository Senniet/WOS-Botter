package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.RawImageData;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TriumphWeeklyRewardOcrProbeTest {
 @Test void actualWeeklyRewardsOverlayRecognized() throws Exception {
  assertTrue(TriumphWeeklyRewardsFrame.visible(frame()));
  assertTrue(TriumphWeeklyRewardsFrame.hasOverlayText("Weekly Rewards","Tap anywhere to close"));
  assertFalse(TriumphWeeklyRewardsFrame.hasOverlayText("Rewards","Tap anywhere to exit"));
  assertFalse(TriumphWeeklyRewardsFrame.hasOverlayText("Weekly Rewards","Buy now"));
 }
 @Test void oneTapOnConfirmedNamedOverlayThenVerifyItDisappeared() throws Exception {
  var reward=frame();var absent=RawImageData.capture(new byte[720*1280*4],720,1280,32);
  var frames=List.of(reward,reward,absent);var reads=new AtomicInteger();var taps=new AtomicInteger();
  var result=TriumphWeeklyRewardsFrame.dismiss(
   ()->frames.get(reads.getAndIncrement()),pt->{assertEquals(360,pt.getX());assertEquals(1180,pt.getY());taps.incrementAndGet();},()->{});
  assertEquals(TriumphWeeklyRewardsFrame.Result.DISMISSED,result);
  assertEquals(1,taps.get());assertEquals(3,reads.get());
 }
 @Test void noTapOnUnverifiedOrPersistentOverlay() throws Exception {
  var reward=frame();var absent=RawImageData.capture(new byte[720*1280*4],720,1280,32);
  var taps=new AtomicInteger();var reads=new AtomicInteger();
  assertEquals(TriumphWeeklyRewardsFrame.Result.ABSENT,
   TriumphWeeklyRewardsFrame.dismiss(()->absent,p->taps.incrementAndGet(),()->{}));
  assertEquals(TriumphWeeklyRewardsFrame.Result.UNVERIFIED,
   TriumphWeeklyRewardsFrame.dismiss(()->reads.getAndIncrement()==0?reward:absent,
      p->taps.incrementAndGet(),()->{}));
  assertEquals(0,taps.get());
  assertEquals(TriumphWeeklyRewardsFrame.Result.UNVERIFIED,
   TriumphWeeklyRewardsFrame.dismiss(()->reward,p->taps.incrementAndGet(),()->{}));
  assertEquals(1,taps.get());
 }
 private static RawImageData frame()throws Exception{
  var stream=Objects.requireNonNull(TriumphWeeklyRewardOcrProbeTest.class.getResourceAsStream(
   "/triumph/qa-20261116/weekly-rewards.png"));
  var img=ImageIO.read(stream);byte[] b=new byte[720*1280*4];
  for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
   int c=img.getRGB(x,y),i=(y*720+x)*4;b[i]=(byte)(c>>16);b[i+1]=(byte)(c>>8);b[i+2]=(byte)c;b[i+3]=(byte)255;
  }
  return RawImageData.capture(b,720,1280,32);
 }
}
