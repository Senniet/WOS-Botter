package dev.frostguard.tasks.combat;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.*;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class ArenaCurrentHeaderProbeTest {
 @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
 @Test void actualArenaTitleAndChallengeScores()throws Exception{
  var f=frame("/arena/qa-20261116/arena-challenge.png");
  var old=OpenCvPatternLocator.locatePattern(f,
      TemplatesEnum.SERVE_EMU_ARENA_TITLE.getTemplate(),new PointData(70,70),new PointData(470,150),90);
  var title=OpenCvPatternLocator.locatePattern(f,
      TemplatesEnum.SERVE_EMU_ARENA_TITLE.getTemplate(),new PointData(70,0),new PointData(470,105),90);
  var challenge=OpenCvPatternLocator.locatePattern(f,
      TemplatesEnum.ARENA_CHALLENGE_BUTTON_CURRENT.getTemplate(),new PointData(190,1150),new PointData(535,1270),90);
  var serve=OpenCvPatternLocator.locatePattern(f,
      TemplatesEnum.SERVE_EMU_ARENA_CHALLENGE.getTemplate(),new PointData(190,1150),new PointData(535,1270),90);
  System.out.println("ARENA_PROBE old="+old+" title="+title+" challenge="+challenge+" serve="+serve);
  assertFalse(old.isFound(),"Wrong historic header ROI must reproduce the defect");
  assertTrue(title.isFound(),"Current real 2026 Arena title is correctly visible at y~40");
  assertTrue(challenge.isFound());
  assertTrue(serve.isFound());
 }
 private static RawImageData frame(String name)throws Exception{
  var input=Objects.requireNonNull(ArenaCurrentHeaderProbeTest.class.getResourceAsStream(name));
  var image=ImageIO.read(input);byte[] b=new byte[720*1280*4];
  for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
   int c=image.getRGB(x,y),o=(y*720+x)*4;b[o]=(byte)(c>>16);b[o+1]=(byte)(c>>8);b[o+2]=(byte)c;b[o+3]=(byte)255;
  }
  return RawImageData.capture(b,720,1280,32);
 }
}
