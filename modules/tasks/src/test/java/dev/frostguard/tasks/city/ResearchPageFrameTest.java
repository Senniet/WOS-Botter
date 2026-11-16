package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class ResearchPageFrameTest {
    @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}

    @Test void actualTechResearchHeaderMatchesInCorrectTopRoi()throws Exception{
        RawImageData research=frame("research-title.png");
        assertTrue(ResearchPageFrame.titleVisible(research));
        assertTrue(ResearchPageFrame.safeBack(research).isFound());
    }
    @Test void cannotTapResearchBackOnCityOrInvalidFrame()throws Exception{
        RawImageData city=frame("city-home.png");
        assertFalse(ResearchPageFrame.titleVisible(city));
        assertFalse(ResearchPageFrame.safeBack(city).isFound());
        assertFalse(ResearchPageFrame.safeBack(null).isFound());
    }
    private static RawImageData frame(String name)throws Exception {
        BufferedImage img;
        try(var stream=Objects.requireNonNull(ResearchPageFrameTest.class.getResourceAsStream("/research/qa-20261116/"+name))){img=ImageIO.read(stream);}
        assertEquals(720,img.getWidth());assertEquals(1280,img.getHeight());
        byte[] b=new byte[720*1280*4];
        for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
            int a=img.getRGB(x,y),o=(y*720+x)*4;
            b[o]=(byte)(a>>16);b[o+1]=(byte)(a>>8);b[o+2]=(byte)a;b[o+3]=(byte)255;
        }
        return RawImageData.capture(b,720,1280,32);
    }
}
