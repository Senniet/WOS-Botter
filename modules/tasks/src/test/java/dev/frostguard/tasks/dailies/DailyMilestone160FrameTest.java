package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class DailyMilestone160FrameTest {
 @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
 @Test void chestNotAvailableBelow160OrAfterClaim()throws Exception{
  assertFalse(DailyMilestone160Frame.ready(frame("daily-milestone-before.png")).isFound());
  assertFalse(DailyMilestone160Frame.ready(frame("daily-milestone-after.png")).isFound());
  assertFalse(DailyMilestone160Frame.ready(frame("daily-rewards.png")).isFound());
  assertTrue(DailyMilestone160Frame.ready(frame("daily-milestone-available-composite.png")).isFound(),
      "Positive simulated: threshold crossed while 160 chest still closed");
 }
 private static RawImageData frame(String file)throws Exception{
  var in=Objects.requireNonNull(DailyMilestone160FrameTest.class.getResourceAsStream("/daily/qa-20261116/"+file));
  var img=ImageIO.read(in);var data=new byte[720*1280*4];
  for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
   int c=img.getRGB(x,y),i=(y*720+x)*4;data[i]=(byte)(c>>16);data[i+1]=(byte)(c>>8);data[i+2]=(byte)c;data[i+3]=(byte)255;
  }
  return RawImageData.capture(data,720,1280,32);
 }
}
