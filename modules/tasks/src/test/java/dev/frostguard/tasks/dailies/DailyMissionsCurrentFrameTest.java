package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class DailyMissionsCurrentFrameTest {
 @BeforeAll static void cv() throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
 @Test void currentDailyTitleAndRealCloseButtonWorkBeforeAndAfterClaims()throws Exception{
  for(String f:new String[]{"daily-before-claim.png","daily-after-claim.png"}){
   var s=frame(f);
   assertTrue(DailyMissionsCurrentFrame.title(s),f);
   var x=DailyMissionsCurrentFrame.close(s);
   assertTrue(x.isFound(),f);
   assertTrue(x.getPoint().getX()>660 && x.getPoint().getY()<160,"Close must hit top-right X, not Rewards or tab");
  }
 }
 @Test void rewardsAndCityNeverAuthorizeClose()throws Exception{
  for(String f:new String[]{"daily-rewards.png","city-home.png"}){
   var s=frame(f);
   assertFalse(DailyMissionsCurrentFrame.title(s));
   assertFalse(DailyMissionsCurrentFrame.close(s).isFound());
  }
 }
 private static RawImageData frame(String name)throws Exception{
  BufferedImage img;
  try(var input=Objects.requireNonNull(DailyMissionsCurrentFrameTest.class.getResourceAsStream("/daily/qa-20261116/"+name))){img=ImageIO.read(input);}
  var b=new byte[720*1280*4];
  for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
   int c=img.getRGB(x,y),i=(y*720+x)*4;
   b[i]=(byte)(c>>16);b[i+1]=(byte)(c>>8);b[i+2]=(byte)c;b[i+3]=(byte)255;
  }
  return RawImageData.capture(b,720,1280,32);
 }
}
