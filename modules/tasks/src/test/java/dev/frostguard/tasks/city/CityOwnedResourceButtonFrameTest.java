package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CityOwnedResourceButtonFrameTest {
    @BeforeAll static void init() throws Exception {OpenCvPatternLocator.loadNativeLibrary();}

    static RawImageData frame(String name) throws Exception {
        BufferedImage im=ImageIO.read(Objects.requireNonNull(
                CityOwnedResourceButtonFrameTest.class.getResourceAsStream("/city/live-20261120/"+name)));
        byte[] rgba=new byte[im.getWidth()*im.getHeight()*4];int i=0;
        for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){
            int rgb=im.getRGB(x,y);
            rgba[i++]=(byte)(rgb>>16);rgba[i++]=(byte)(rgb>>8);
            rgba[i++]=(byte)rgb;rgba[i++]=(byte)255;
        }
        return RawImageData.capture(rgba,im.getWidth(),im.getHeight(),32);
    }

    @Test void realUnavailableItemsHaveGreyReplenishButton() throws Exception {
        var screenshot=frame("obtain-more-disabled-redacted.png");
        assertTrue(CityOwnedResourceButtonFrame.isObtainMore(screenshot));
        assertFalse(CityOwnedResourceButtonFrame.hasEnabledResourceButton(screenshot),
                "The actual grey button must never trigger a resource or VIP Shop tap");
    }
    @Test void vipShopAfterWrongTapIsNotObtainMore() throws Exception {
        var screenshot=frame("shop-after-wrong-tap-redacted.png");
        assertFalse(CityOwnedResourceButtonFrame.isObtainMore(screenshot));
        assertFalse(CityOwnedResourceButtonFrame.hasEnabledResourceButton(screenshot));
    }
    @Test void onlyColoredResourceButtonOnExactPageCanPassFirstGate() throws Exception {
        var frame=frame("obtain-more-disabled-redacted.png");
        var data=frame.getData();
        for (int y=1100;y<1161;y++)for(int x=240;x<490;x++){
            int offset=(y*720+x)*4;
            data[offset]=(byte)55; data[offset+1]=(byte)163;
            data[offset+2]=(byte)230;
        }
        assertTrue(CityOwnedResourceButtonFrame.hasEnabledResourceButton(frame));
    }
    @Test void missingFrameNeverAuthorizesAnAction() {
        assertFalse(CityOwnedResourceButtonFrame.hasEnabledResourceButton(null));
    }
}
