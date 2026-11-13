package dev.frostguard.tasks.heroes;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroPointsChestRewardFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void rewardAndSafeExitLabelsAreVisibleOnActualClaimScreen() throws Exception {
        BufferedImage screen=ImageIO.read(Objects.requireNonNull(
                getClass().getResource("/heroes/points-chest-rewards-redacted.png")));
        byte[] rgba=new byte[screen.getWidth()*screen.getHeight()*4];
        for(int y=0;y<screen.getHeight();y++) for(int x=0;x<screen.getWidth();x++) {
            int argb=screen.getRGB(x,y),i=(y*screen.getWidth()+x)*4;
            rgba[i]=(byte)(argb>>16);rgba[i+1]=(byte)(argb>>8);
            rgba[i+2]=(byte)argb;rgba[i+3]=(byte)255;
        }
        RawImageData image=RawImageData.capture(rgba,screen.getWidth(),screen.getHeight(),32);
        assertTrue(OpenCvPatternLocator.locatePattern(image, TemplatesEnum.SERVE_EMU_REWARDS_BANNER.getTemplate(),
                new PointData(220,280),new PointData(505,440),88).isFound());
        assertTrue(OpenCvPatternLocator.locatePattern(image, TemplatesEnum.SERVE_EMU_HERO_POINTS_REWARD_EXIT.getTemplate(),
                new PointData(190,1140),new PointData(530,1240),89).isFound());
    }
}
