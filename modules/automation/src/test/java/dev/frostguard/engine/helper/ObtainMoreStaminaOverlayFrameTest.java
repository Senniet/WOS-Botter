package dev.frostguard.engine.helper;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObtainMoreStaminaOverlayFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void detectsTheExactLiveBlockedChiefStaminaDialog() throws Exception {
        assertTrue(ObtainMoreStaminaOverlayFrame.verifiedClose(
                frame("live-20261122/obtain-more-stuck-20261122.png")).isFound());
    }

    @Test void detectsTheOlderObtainMoreLayout() throws Exception {
        assertTrue(ObtainMoreStaminaOverlayFrame.verifiedClose(
                frame("obtain-more-130-of-200.png")).isFound());
    }

    @Test void refusesResourceObtainMoreWithSameHeaderAndX() throws Exception {
        assertFalse(ObtainMoreStaminaOverlayFrame.verifiedClose(
                frame("live-20261122/obtain-more-iron-resources.png")).isFound());
    }

    @Test void rejectsCityHudAndUnrelatedDialog() throws Exception {
        assertFalse(ObtainMoreStaminaOverlayFrame.verifiedClose(
                frame("live-hud-20261114/city-redacted.png")).isFound());
        assertFalse(ObtainMoreStaminaOverlayFrame.verifiedClose(
                frame("warm-welcome/claim-visible.png")).isFound());
        assertFalse(ObtainMoreStaminaOverlayFrame.verifiedClose(null).isFound());
    }

    private static RawImageData frame(String name) throws Exception {
        BufferedImage im;
        try(var in=Objects.requireNonNull(
                ObtainMoreStaminaOverlayFrameTest.class
                        .getResourceAsStream("/stamina/"+name))) {
            im=ImageIO.read(in);
        }
        byte[] rgba=new byte[im.getWidth()*im.getHeight()*4];
        for(int y=0;y<im.getHeight();y++) for(int x=0;x<im.getWidth();x++) {
            int argb=im.getRGB(x,y),i=(y*im.getWidth()+x)*4;
            rgba[i]=(byte)(argb>>16);rgba[i+1]=(byte)(argb>>8);
            rgba[i+2]=(byte)argb;rgba[i+3]=(byte)255;
        }
        return RawImageData.capture(rgba,im.getWidth(),im.getHeight(),32);
    }
}
