package dev.frostguard.tasks.dailies;

import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.tasks.exploration.ExplorationLatestFrameProbeTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

/** Actual current Daily sidebar, with unrelated game pixels blacked out. */
class SmartDailyOverviewLiveFrameTest {
    @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
    @Test void visibleDailySidebarReportsRealReadinessWithoutGuessingHiddenItems()throws Exception{
        try(var in=getClass().getResourceAsStream("/daily-free-20261118/daily-sidebar-redacted.png")){
            assertNotNull(in);
            var frame=ExplorationLatestFrameProbeTest.frame(ImageIO.read(in));
            assertTrue(DailyOverviewFrame.isDailyOverview(frame));
            String text=DailyOverviewFrame.readVisibleText(frame);
            System.out.println("SANITIZED DAILY OCR: "+text.replaceAll("[\\r\\n]+"," "));
            var ready=DailyOverviewDispatchPolicy.actionable(text);
            System.out.println("SMART DAILY READY FROM LIVE OCR: "+ready);
            assertFalse(ready.contains(dev.frostguard.api.configs.TpDailyTaskEnum.ARENA));
            assertFalse(ready.contains(dev.frostguard.api.configs.TpDailyTaskEnum.ONLINE_REWARDS));
        }
    }
}
