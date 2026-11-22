package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CityUpgradeBonusExitFrameTest {
    @BeforeAll static void init() throws Exception {OpenCvPatternLocator.loadNativeLibrary();}
    static RawImageData read(String file) throws Exception {
        BufferedImage im=ImageIO.read(Objects.requireNonNull(
                CityUpgradeBonusExitFrameTest.class.getResourceAsStream("/city/live-20261120/"+file)));
        byte[] rgba=new byte[im.getWidth()*im.getHeight()*4];int i=0;
        for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){
            int c=im.getRGB(x,y);
            rgba[i++]=(byte)(c>>16);rgba[i++]=(byte)(c>>8);
            rgba[i++]=(byte)c;rgba[i++]=(byte)255;
        }
        return RawImageData.capture(rgba,im.getWidth(),im.getHeight(),32);
    }
    @Test void trueModernUpgradeBonusHasVerifiedX() throws Exception {
        var close=CityUpgradeBonusExitFrame.verifiedClose(
                read("building-upgrade-bonus-header-redacted.png"));
        assertTrue(close.isFound(),"Actual live Upgrade Bonus X should be recognized");
    }
    @Test void otherPanelNeverReceivesUpgradeClose() throws Exception {
        assertFalse(CityUpgradeBonusExitFrame.verifiedClose(
                read("obtain-more-disabled-redacted.png")).isFound());
        assertFalse(CityUpgradeBonusExitFrame.verifiedClose(null).isFound());
    }
}
