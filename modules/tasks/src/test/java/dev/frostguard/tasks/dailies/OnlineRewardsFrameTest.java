package dev.frostguard.tasks.dailies;

import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.tasks.exploration.ExplorationLatestFrameProbeTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class OnlineRewardsFrameTest {
    @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
    @Test void claimedHiddenDailyOnlineRewardCannotCauseAnyUnrelatedTap()throws Exception{
        try(var in=getClass().getResourceAsStream("/daily-free-20261118/daily-sidebar-redacted.png")){
            assertNotNull(in);
            var f=ExplorationLatestFrameProbeTest.frame(ImageIO.read(in));
            assertTrue(DailyOverviewFrame.isDailyOverview(f));
            assertEquals(-1,OnlineRewardsFrame.onlineRewardsRow(f));
            assertFalse(OnlineRewardsFrame.onlinePage(f));
            assertFalse(OnlineRewardsFrame.freeClaim(f).isFound());
        }
    }
    @Test void onlyExplicitReadyFlagCanOpenTheRewardRoute(){
        assertTrue(OnlineRewardsFrame.dailyOnlineReady("Online Rewards Ready"));
        assertTrue(OnlineRewardsFrame.dailyOnlineReady("Online Rewards Claimable"));
        assertFalse(OnlineRewardsFrame.dailyOnlineReady("Online Rewards 01:32:45"));
        assertFalse(OnlineRewardsFrame.dailyOnlineReady("Online Rewards Completed"));
        assertFalse(OnlineRewardsFrame.dailyOnlineReady("Alliance Contribution Ready"));
    }
}
