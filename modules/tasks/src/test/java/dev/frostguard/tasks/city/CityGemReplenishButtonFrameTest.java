package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CityGemReplenishButtonFrameTest {
    @BeforeAll static void init() throws Exception {OpenCvPatternLocator.loadNativeLibrary();}

    static RawImageData read(String name) throws Exception {
        BufferedImage im = ImageIO.read(Objects.requireNonNull(
                CityGemReplenishButtonFrameTest.class.getResourceAsStream(
                        "/city/live-20261120/" + name)));
        byte[] rgba = new byte[im.getWidth() * im.getHeight() * 4];
        int i=0;
        for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){
            int c=im.getRGB(x,y);
            rgba[i++]=(byte)(c>>16);rgba[i++]=(byte)(c>>8);
            rgba[i++]=(byte)c;rgba[i++]=(byte)255;
        }
        return RawImageData.capture(rgba, im.getWidth(), im.getHeight(), 32);
    }
    @Test void goldBuyUseRecognizedOnRealDisabledReplenishScreen() throws Exception {
        var frame=read("obtain-more-gold-buy-100-redacted.png");
        assertTrue(CityOwnedResourceButtonFrame.isObtainMore(frame));
        assertFalse(CityOwnedResourceButtonFrame.hasEnabledResourceButton(frame));
        assertTrue(CityGemReplenishButtonFrame.hasGoldenBuyAndUse(frame),
                "Disabled Replenish All does not mean the verified top gem offer is absent");
    }
    @Test void unrelatedShopCannotTriggerGemBuy() throws Exception {
        assertFalse(CityGemReplenishButtonFrame.hasGoldenBuyAndUse(
                read("shop-after-wrong-tap-redacted.png")));
        assertFalse(CityGemReplenishButtonFrame.hasGoldenBuyAndUse(null));
    }
}
