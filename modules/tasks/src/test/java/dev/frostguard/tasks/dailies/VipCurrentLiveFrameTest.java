package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VipCurrentLiveFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void vipLockedDailyBundleIsRecognizedAndNotClaimedBlindly() throws Exception {
        BufferedImage screen=frame("vip-current-locked-redacted.png");
        assertTrue(matches(screen, TemplatesEnum.SERVE_EMU_VIP_TITLE, 78,70,195,150));
        assertTrue(matches(screen, TemplatesEnum.SERVE_EMU_VIP_LOCKED, 515,760,675,886));
        assertTrue(matches(screen, TemplatesEnum.SERVE_EMU_VIP_CALENDAR, 568,285,690,403));
        assertFalse(GreenLabelActionDetector.detect(screen,
                new GreenLabelActionDetector.Region(505,755,688,882)).isFound());
    }

    @Test void tomorrowSignInBannerDoesNotLookLikeFreeClaim() throws Exception {
        BufferedImage screen=frame("vip-current-signin-redacted.png");
        assertTrue(matches(screen, TemplatesEnum.SERVE_EMU_VIP_SIGNIN, 40,265,510,385));
        assertFalse(GreenLabelActionDetector.detect(screen,
                new GreenLabelActionDetector.Region(180,985,545,1148)).isFound());
    }

    private BufferedImage frame(String name) throws Exception {
        return ImageIO.read(Objects.requireNonNull(getClass().getResource("/vip/"+name)));
    }

    private boolean matches(BufferedImage image, TemplatesEnum template, int x1,int y1,int x2,int y2) {
        byte[] rgba=new byte[image.getWidth()*image.getHeight()*4];
        for (int y=0;y<image.getHeight();y++) for (int x=0;x<image.getWidth();x++) {
            int rgb=image.getRGB(x,y),i=(y*image.getWidth()+x)*4;
            rgba[i]=(byte)(rgb>>16);rgba[i+1]=(byte)(rgb>>8);
            rgba[i+2]=(byte)rgb;rgba[i+3]=(byte)255;
        }
        return OpenCvPatternLocator.locatePattern(
                RawImageData.capture(rgba,image.getWidth(),image.getHeight(),32),
                template.getTemplate(),new PointData(x1,y1),new PointData(x2,y2),90).isFound();
    }
}
