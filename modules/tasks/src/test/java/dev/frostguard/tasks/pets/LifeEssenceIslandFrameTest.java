package dev.frostguard.tasks.pets;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class LifeEssenceIslandFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    private RawImageData image(String resource) throws Exception {
        BufferedImage im;
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream(resource))) {
            im = ImageIO.read(stream);
        }
        byte[] rgba = new byte[im.getWidth()*im.getHeight()*4];
        int index=0;
        for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++) {
            int color=im.getRGB(x,y);
            rgba[index++]=(byte)(color>>>16);
            rgba[index++]=(byte)(color>>>8);
            rgba[index++]=(byte) color;
            rgba[index++]=(byte)255;
        }
        return RawImageData.capture(rgba,im.getWidth(),im.getHeight(),32);
    }

    @Test void detectsOwnIslandAtFirstClaim() throws Exception {
        assertTrue(LifeEssenceIslandFrame.ownIsland(
                image("/live-regressions-20260818/life-essence-claim.png")));
    }

    @Test void detectsOwnIslandWithPartiallyFilledMarker() throws Exception {
        assertTrue(LifeEssenceIslandFrame.ownIsland(
                image("/live-regressions-20260923/life-essence-partially-filled.png")));
    }

    @Test void detectsOwnIslandAtAnotherMarkerPosition() throws Exception {
        assertTrue(LifeEssenceIslandFrame.ownIsland(
                image("/live-regressions-20260922/life-essence-available-marker.png")));
    }

    @Test void rejectsArenaScreen() throws Exception {
        assertFalse(LifeEssenceIslandFrame.ownIsland(
                image("/live-regressions-20260818/arena.png")));
    }

    @Test void rejectsAllyIslandAndUnknownScreen() throws Exception {
        assertFalse(LifeEssenceIslandFrame.ownIsland(
                image("/alliance-tree-20261118/island-ready-redacted.png")));
        assertFalse(LifeEssenceIslandFrame.ownIsland(null));
    }
}
