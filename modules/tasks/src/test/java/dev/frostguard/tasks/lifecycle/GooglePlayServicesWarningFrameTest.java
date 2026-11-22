package dev.frostguard.tasks.lifecycle;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GooglePlayServicesWarningFrameTest {
    @BeforeAll static void load() throws Exception {
        OpenCvPatternLocator.loadNativeLibrary();
    }
    private static RawImageData frame(String name) throws Exception {
        BufferedImage b=ImageIO.read(Objects.requireNonNull(
            GooglePlayServicesWarningFrameTest.class.getResourceAsStream("/startup/"+name)));
        byte[] rgba=new byte[b.getWidth()*b.getHeight()*4];int i=0;
        for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++){
            int rgb=b.getRGB(x,y);
            rgba[i++]=(byte)(rgb>>16);rgba[i++]=(byte)(rgb>>8);
            rgba[i++]=(byte)rgb;rgba[i++]=(byte)255;
        }
        return RawImageData.capture(rgba,b.getWidth(),b.getHeight(),32);
    }
    @Test void actualUnsupportedGoogleServicesModalHasExactSafeOkAction() throws Exception {
        var match=GooglePlayServicesWarningFrame.verifiedOk(frame("gms-warning-nov19.png"));
        assertTrue(match.isFound(), "Real Serve-Emu dialog must be recoverable");
        assertTrue(match.getPoint().getX()>300 && match.getPoint().getX()<435);
        assertTrue(match.getPoint().getY()>775 && match.getPoint().getY()<845);
    }
    @Test void cityWithoutSystemModalCannotTriggerAnyOkTap() throws Exception {
        assertFalse(GooglePlayServicesWarningFrame.verifiedOk(
                frame("gms-warning-dismissed-city-nov19.png")).isFound());
    }
    @Test void nullFrameIsNotAnAuthorization() {
        assertFalse(GooglePlayServicesWarningFrame.verifiedOk(null).isFound());
    }
}
