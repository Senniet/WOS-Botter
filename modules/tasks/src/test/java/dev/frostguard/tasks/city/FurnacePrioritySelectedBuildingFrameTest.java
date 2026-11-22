package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FurnacePrioritySelectedBuildingFrameTest {
    @BeforeAll static void initialize() throws Exception {
        OpenCvPatternLocator.loadNativeLibrary();
    }
    private RawImageData load(String path) throws Exception {
        BufferedImage img;
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream(path))) {
            img=ImageIO.read(in);
        }
        byte[] rgba=new byte[img.getWidth()*img.getHeight()*4];
        int k=0;
        for(int y=0;y<img.getHeight();y++)for(int x=0;x<img.getWidth();x++){
            int rgb=img.getRGB(x,y);
            rgba[k++]=(byte)(rgb>>16);rgba[k++]=(byte)(rgb>>8);
            rgba[k++]=(byte)rgb;rgba[k++]=(byte)255;
        }
        return RawImageData.capture(rgba,img.getWidth(),img.getHeight(),32);
    }

    @Test void matchesActualSelectedEmbassy() throws Exception {
        assertTrue(FurnacePrioritySelectedBuildingFrame.embassy(
                load("/city/embassy-selected-upgrade-hand-20261115-redacted.png")));
    }
    @Test void doesNotMatchCommandCenter() throws Exception {
        assertFalse(FurnacePrioritySelectedBuildingFrame.embassy(
                load("/city/command-center-selected-upgrade-hand-20261118-redacted.png")));
    }
    @Test void doesNotMatchFurnaceUpgradeOrMissingFrame() throws Exception {
        assertFalse(FurnacePrioritySelectedBuildingFrame.embassy(
                load("/city/furnace-detail-upgrade-20260927.png")));
        assertFalse(FurnacePrioritySelectedBuildingFrame.embassy(null));
    }
}
