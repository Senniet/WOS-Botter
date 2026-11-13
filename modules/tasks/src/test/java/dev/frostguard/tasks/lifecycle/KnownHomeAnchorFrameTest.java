package dev.frostguard.tasks.lifecycle;

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

class KnownHomeAnchorFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void modernCityAnchorIsSpecificToLiveHomeNotNestedSquadPage() throws Exception {
        assertTrue(city(frame("home-city-anchor-redacted.png")));
        assertFalse(city(frame("squad-settings-page-redacted.png")));
        assertFalse(city(frame("recruit-page-redacted.png")));
    }

    @Test void currentLiveWorldScreenMatchesWorldAnchorButNotKnownNestedPages() throws Exception {
        assertTrue(world(frame("home-world-anchor-redacted.png")));
        assertFalse(world(frame("squad-settings-page-redacted.png")));
        assertFalse(world(frame("recruit-page-redacted.png")));
    }

    private static boolean world(RawImageData f) throws Exception {
        return OpenCvPatternLocator.locatePattern(f,TemplatesEnum.SERVE_EMU_WORLD_ANCHOR.getTemplate(),
                new PointData(590,1145),new PointData(715,1275),90).isFound()
                || OpenCvPatternLocator.locatePattern(f,TemplatesEnum.SERVE_EMU_WORLD_ANCHOR_CURRENT.getTemplate(),
                new PointData(590,1145),new PointData(715,1275),90).isFound();
    }

    private static boolean city(RawImageData f) throws Exception {
        return OpenCvPatternLocator.locatePattern(f,TemplatesEnum.SERVE_EMU_CITY_ANCHOR.getTemplate(),
                new PointData(590,1145),new PointData(715,1275),90).isFound();
    }

    private static RawImageData frame(String name) throws Exception {
        BufferedImage image=ImageIO.read(Objects.requireNonNull(
                KnownHomeAnchorFrameTest.class.getResource("/initialize/"+name)));
        byte[] rgba=new byte[image.getWidth()*image.getHeight()*4];
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            int rgb=image.getRGB(x,y),i=(y*image.getWidth()+x)*4;
            rgba[i]=(byte)(rgb>>16);rgba[i+1]=(byte)(rgb>>8);
            rgba[i+2]=(byte)rgb;rgba[i+3]=(byte)255;
        }
        return RawImageData.capture(rgba,image.getWidth(),image.getHeight(),32);
    }
}
