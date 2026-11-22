package dev.frostguard.tasks.heroes;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroRecruitmentExitFrameTest {
    @BeforeAll static void load() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }
    static RawImageData load(String name) throws Exception {
        BufferedImage im=ImageIO.read(Objects.requireNonNull(
            HeroRecruitmentExitFrameTest.class.getResourceAsStream("/heroes/live-20261119/"+name)));
        byte[] rgba=new byte[im.getWidth()*im.getHeight()*4];int i=0;
        for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){
            int rgb=im.getRGB(x,y);
            rgba[i++]=(byte)(rgb>>16);rgba[i++]=(byte)(rgb>>8);rgba[i++]=(byte)rgb;rgba[i++]=(byte)255;
        }
        return RawImageData.capture(rgba,im.getWidth(),im.getHeight(),32);
    }
    @Test void bothCurrentHeroHeadersAllowVerifiedDirectBack() throws Exception {
        for (String file:new String[]{"recruitment-header-redacted.png","heroes-header-redacted.png"}) {
            var frame=load(file);
            var arrow=HeroRecruitmentExitFrame.back(frame);
            assertTrue(arrow.isFound(),"Current header must contain a verified arrow: "+file);
            assertTrue(arrow.getPoint().getX()>=12 && arrow.getPoint().getX()<=80,file);
            assertTrue(arrow.getPoint().getY()>=10 && arrow.getPoint().getY()<=76,file);
        }
    }

    @Test void cityHeaderMustNotBeMistakenForHeroBackNavigation() throws Exception {
        assertFalse(HeroRecruitmentExitFrame.back(load("city-header-redacted.png")).isFound());
    }
}
