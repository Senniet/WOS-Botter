package dev.frostguard.tasks.dailies;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class IntelJourneyStageNov20Test {
 @BeforeAll static void init() throws Exception {OpenCvPatternLocator.loadNativeLibrary();}
 byte[] load(int n) throws Exception {
   return Objects.requireNonNull(getClass().getResourceAsStream(
       "/intel/live-20261120/journey-stage-"+n+"-redacted.png")).readAllBytes();
 }
 dev.frostguard.api.domain.RawImageData rawFrame(int n) throws Exception {
    java.awt.image.BufferedImage im=javax.imageio.ImageIO.read(Objects.requireNonNull(
        getClass().getResourceAsStream(
          "/intel/live-20261120/journey-stage-"+n+"-redacted.png")));
    byte[] rgba=new byte[im.getWidth()*im.getHeight()*4];
    int i=0;
    for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){
      int pixel=im.getRGB(x,y);
      rgba[i++]=(byte)(pixel>>16);rgba[i++]=(byte)(pixel>>8);
      rgba[i++]=(byte)pixel;rgba[i++]=(byte)255;
    }
    return dev.frostguard.api.domain.RawImageData.capture(rgba,
        im.getWidth(),im.getHeight(),32);
 }
 @Test void currentFightAndSquadTitleTogetherAreRecognizedOnlyAfterExplore() throws Exception {
   for (int n:new int[]{0,2}) {
     byte[] frame=load(n);
     var title=OpenCvPatternLocator.locatePattern(frame,
         TemplatesEnum.SERVE_EMU_SQUAD_SETTINGS_TITLE,
         new PointData(0,0),new PointData(350,155),85);
     var fight=OpenCvPatternLocator.locatePattern(frame,
         TemplatesEnum.INTEL_JOURNEY_FIGHT_LIVE,
         new PointData(365,1145),new PointData(705,1275),88);
     System.out.println("JOURNEY FRAME="+n+" title="+title+" fight="+fight);
     if (n==0) {assertFalse(title.isFound());assertFalse(fight.isFound());}
     else {assertTrue(title.isFound());assertTrue(fight.isFound());}
     var verified=IntelJourneyFightStageFrame.verifiedFight(rawFrame(n));
     if (n==0) assertFalse(verified.isFound(),"Explore cannot authorize Fight");
     else {
        assertTrue(verified.isFound(),"Live green Fight must be authorized on Squad Settings");
        assertTrue(verified.getPoint().getX()>445 && verified.getPoint().getX()<630);
        assertTrue(verified.getPoint().getY()>1170 && verified.getPoint().getY()<1240);
     }
   }
 }
}
