package dev.frostguard.tasks.combat;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaFormationNov20FrameTest {
    @BeforeAll static void load() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }
    private RawImageData capture(String path) throws Exception {
        BufferedImage im=ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream(path)));
        byte[] rgba=new byte[im.getWidth()*im.getHeight()*4];
        int p=0;
        for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){
            int c=im.getRGB(x,y);
            rgba[p++]=(byte)(c>>>16);rgba[p++]=(byte)(c>>>8);
            rgba[p++]=(byte)c;rgba[p++]=(byte)255;
        }
        return RawImageData.capture(rgba,im.getWidth(),im.getHeight(),32);
    }
    @Test void currentFormationHasMovedEqualizeAndPreBalancedBars() throws Exception {
        RawImageData frame=capture("/arena/live-20261120/arena-formation-current-redacted.png");
        assertTrue(ArenaLiveFrame.formation(frame),"This is verified Offensive Formation Setup");
        assertTrue(ArenaLiveFrame.equalize(frame).isFound(),"The moved Equalize is recognized");
        assertTrue(ArenaLiveFrame.balanced(frame),"Three green troop bars are already balanced");
    }
    @Test void cityCannotBeMistakenForFormationOrEqualize() throws Exception {
        RawImageData frame=capture("/arena/live-20261120/arena-home-header-redacted.png");
        assertFalse(ArenaLiveFrame.formation(frame));
        assertFalse(ArenaLiveFrame.equalize(frame).isFound());
    }
}
