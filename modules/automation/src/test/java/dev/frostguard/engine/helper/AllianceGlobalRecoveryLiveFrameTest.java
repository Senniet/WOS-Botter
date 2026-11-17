package dev.frostguard.engine.helper;

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

/** Live emulator fixtures for the title + arrow gate used by global navigation. */
class AllianceGlobalRecoveryLiveFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void realAllianceOverviewHasTitleAndArrowButNotTechTitle() throws Exception {
        RawImageData frame=frame("alliance-live-20261115.png");
        assertTrue(match(frame,TemplatesEnum.SERVE_EMU_ALLIANCE_HEADER_LIVE_2026));
        assertTrue(arrow(frame));
        assertFalse(match(frame,TemplatesEnum.SERVE_EMU_ALLIANCE_TECH_HEADER_LIVE_2026));
    }

    @Test void realAllianceTechHasTitleAndArrowButNotOverviewTitle() throws Exception {
        RawImageData frame=frame("alliance-tech-live-20261115.png");
        assertTrue(match(frame,TemplatesEnum.SERVE_EMU_ALLIANCE_TECH_HEADER_LIVE_2026));
        assertTrue(arrow(frame));
        assertFalse(match(frame,TemplatesEnum.SERVE_EMU_ALLIANCE_HEADER_LIVE_2026));
    }

    @Test void cityHomeCannotAuthorizeAllianceBack() throws Exception {
        RawImageData frame=frame("recruit-city-redacted.png");
        assertFalse(match(frame,TemplatesEnum.SERVE_EMU_ALLIANCE_HEADER_LIVE_2026));
        assertFalse(match(frame,TemplatesEnum.SERVE_EMU_ALLIANCE_TECH_HEADER_LIVE_2026));
        assertFalse(arrow(frame));
    }

    private static boolean match(RawImageData frame,TemplatesEnum title) {
        return OpenCvPatternLocator.locatePattern(frame,title.getTemplate(),
                new PointData(85,10),new PointData(270,75),91).isFound();
    }
    private static boolean arrow(RawImageData frame) {
        return OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_ALLIANCE_BACK_LIVE_2026.getTemplate(),
                new PointData(0,0),new PointData(110,105),91).isFound();
    }
    private RawImageData frame(String name) throws Exception {
        BufferedImage image=ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream(
                "/navigation/"+name)));
        byte[] rgba=new byte[image.getWidth()*image.getHeight()*4];
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            int rgb=image.getRGB(x,y),i=(y*image.getWidth()+x)*4;
            rgba[i]=(byte)(rgb>>16);rgba[i+1]=(byte)(rgb>>8);
            rgba[i+2]=(byte)rgb;rgba[i+3]=(byte)255;
        }
        return RawImageData.capture(rgba,image.getWidth(),image.getHeight(),32);
    }
}
