package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.tasks.exploration.ExplorationLatestFrameProbeTest;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.awt.Graphics2D;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual unlocked Growth/Chapter screens with exclusively 'Go' and grey Claim. */
class GrowthMissionRewardsFrameTest {
    @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
    static BufferedImage image(String name)throws Exception{
        try(InputStream input=GrowthMissionRewardsFrameTest.class.getResourceAsStream(
                "/new-rewards-20261118/"+name)){
            assertNotNull(input,name);
            return ImageIO.read(input);
        }
    }
    static RawImageData frame(String name)throws Exception{
        return ExplorationLatestFrameProbeTest.frame(image(name));
    }
    @Test void currentGrowthMissionsAreUnclaimableNotBlueGo()throws Exception{
        RawImageData growth=frame("growth-no-claim-redacted.png");
        assertTrue(GrowthMissionRewardsFrame.growth(growth));
        assertFalse(GrowthMissionRewardsFrame.chapter(growth));
        assertFalse(GrowthMissionRewardsFrame.growthClaim(growth).isFound());
        assertFalse(GrowthMissionRewardsFrame.chapterRewardClaim(growth).isFound());
    }
    @Test void chapterAndDailyAreNotMistakenForGrowth()throws Exception{
        RawImageData chapter=frame("chapter-no-claim-redacted.png");
        assertTrue(GrowthMissionRewardsFrame.chapter(chapter));
        assertFalse(GrowthMissionRewardsFrame.growth(chapter));
        assertFalse(GrowthMissionRewardsFrame.chapterRewardClaim(chapter).isFound());
        assertFalse(GrowthMissionRewardsFrame.growthClaim(chapter).isFound());
        assertFalse(GrowthMissionRewardsFrame.growth(null));
    }
    @Test void buttonFromRealDailyMissionAppearanceOnlyClaimsInsideGrowth()throws Exception{
        BufferedImage growth=image("growth-no-claim-redacted.png");
        BufferedImage actualClaim=image("green-daily-claim-source.png");
        Graphics2D overlay=growth.createGraphics();
        overlay.drawImage(actualClaim.getSubimage(522,527,160,100),522,527,null);
        overlay.dispose();
        RawImageData constructed=ExplorationLatestFrameProbeTest.frame(growth);
        assertTrue(GrowthMissionRewardsFrame.growth(constructed));
        assertTrue(GrowthMissionRewardsFrame.growthClaim(constructed).isFound(),
                "Ready button classifier should find the real green Claim sprite when Growth is complete");
    }
}
