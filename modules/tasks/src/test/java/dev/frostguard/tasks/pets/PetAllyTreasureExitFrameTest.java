package dev.frostguard.tasks.pets;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PetAllyTreasureExitFrameTest {
    @BeforeAll static void init() throws Exception {OpenCvPatternLocator.loadNativeLibrary();}
    private static RawImageData frame(String name) throws Exception {
        BufferedImage img=ImageIO.read(Objects.requireNonNull(
                PetAllyTreasureExitFrameTest.class.getResourceAsStream("/pets/live-20261120/"+name)));
        byte[] data = new byte[img.getWidth()*img.getHeight()*4];
        int pos=0;
        for(int y=0;y<img.getHeight();y++)for(int x=0;x<img.getWidth();x++){
            int rgb=img.getRGB(x,y);
            data[pos++]=(byte)(rgb>>>16);
            data[pos++]=(byte)(rgb>>>8);
            data[pos++]=(byte)rgb;
            data[pos++]=(byte)255;
        }
        return RawImageData.capture(data,img.getWidth(),img.getHeight(),32);
    }
    @Test void exactAllyTreasurePanelClosesAtVerifiedX() throws Exception {
        var x=PetAllyTreasureExitFrame.verifiedClose(frame("ally-treasure-header-redacted.png"));
        System.out.println("LIVE Ally Treasure X="+x);
        assertTrue(x.isFound());
    }
    @Test void adventureAndOccupiedChestOverlayCannotTriggerAllyClose() throws Exception {
        assertFalse(PetAllyTreasureExitFrame.verifiedClose(frame("in-adventure-purple.png")).isFound());
        assertFalse(PetAllyTreasureExitFrame.verifiedClose(frame("map-active-with-idle.png")).isFound());
        assertFalse(PetAllyTreasureExitFrame.verifiedClose(null).isFound());
    }
}
