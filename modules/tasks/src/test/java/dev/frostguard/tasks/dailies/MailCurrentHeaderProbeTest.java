package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.*;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class MailCurrentHeaderProbeTest {
 @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
 @Test void currentHeaderIsAtTopNotInOldRoi()throws Exception{
  var stream=Objects.requireNonNull(getClass().getResourceAsStream("/mail/qa-20261116/mail-header.png"));
  var image=ImageIO.read(stream);byte[] b=new byte[720*1280*4];
  for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
   int c=image.getRGB(x,y),o=(y*720+x)*4;b[o]=(byte)(c>>16);b[o+1]=(byte)(c>>8);b[o+2]=(byte)c;b[o+3]=(byte)255;
  }
  var frame=RawImageData.capture(b,720,1280,32);
  var old=OpenCvPatternLocator.locatePattern(frame,TemplatesEnum.SERVE_EMU_MAIL_TITLE.getTemplate(),
      new PointData(75,75),new PointData(210,150),90);
  var corrected=OpenCvPatternLocator.locatePattern(frame,TemplatesEnum.SERVE_EMU_MAIL_TITLE.getTemplate(),
      new PointData(65,0),new PointData(245,100),90);
  System.out.println("MAIL_PROBE old="+old+" corrected="+corrected);
  assertFalse(old.isFound(),"Old y75..150 ROI causes repeated misses");
  assertTrue(corrected.isFound());
 }
}
