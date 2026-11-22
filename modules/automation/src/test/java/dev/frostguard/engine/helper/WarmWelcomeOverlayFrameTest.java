package dev.frostguard.engine.helper;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class WarmWelcomeOverlayFrameTest {
    @BeforeAll static void cv() throws Exception {
        OpenCvPatternLocator.loadNativeLibrary();
    }

    @Test void recognizesStorehouseStaminaClaimPopup() throws Exception {
        assertTrue(WarmWelcomeOverlayFrame.verifiedClose(
                frame("/stamina/warm-welcome/claim-visible.png")).isFound());
    }

    @Test void recognizesUnacceptedStaminaClaimForSafeDismissal() throws Exception {
        assertTrue(WarmWelcomeOverlayFrame.verifiedClose(
                frame("/stamina/warm-welcome/claim-unaccepted.png")).isFound());
    }

    @Test void doesNotDismissOrdinaryCityScreenOrUnknownFrame() throws Exception {
        assertFalse(WarmWelcomeOverlayFrame.verifiedClose(
                frame("/stamina/live-hud-20261114/city-redacted.png")).isFound());
        assertFalse(WarmWelcomeOverlayFrame.verifiedClose(null).isFound());
    }

    private static RawImageData frame(String path) throws Exception {
        BufferedImage im;
        try(var in=Objects.requireNonNull(
                WarmWelcomeOverlayFrameTest.class.getResourceAsStream(path))) {
            im=ImageIO.read(in);
        }
        byte[] raw=new byte[im.getWidth()*im.getHeight()*4];
        for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++) {
            int i=(y*im.getWidth()+x)*4;
            int argb=im.getRGB(x,y);
            raw[i]=(byte)(argb>>16);
            raw[i+1]=(byte)(argb>>8);
            raw[i+2]=(byte)argb;
            raw[i+3]=(byte)255;
        }
        return RawImageData.capture(raw,im.getWidth(),im.getHeight(),32);
    }
}
