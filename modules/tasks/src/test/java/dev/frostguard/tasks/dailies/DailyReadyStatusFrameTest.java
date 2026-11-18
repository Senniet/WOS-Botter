package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.tasks.exploration.ExplorationLatestFrameProbeTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DailyReadyStatusFrameTest {
 @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
 @Test void actualSidebarReadyItemsDetectedWithoutTinyOcr()throws Exception{
  try(var input=getClass().getResourceAsStream("/daily-free-20261118/daily-sidebar-redacted.png")){
   assertNotNull(input);
   var frame=ExplorationLatestFrameProbeTest.frame(ImageIO.read(input));
   var ready=DailyReadyStatusFrame.actionable(frame);
   System.out.println("GREEN DAILY READY LIVE: "+ready);
   assertEquals(Set.of(TpDailyTaskEnum.PET_ADVENTURE,TpDailyTaskEnum.ALLIANCE_TECH,
     TpDailyTaskEnum.INTEL),ready);
   assertFalse(ready.contains(TpDailyTaskEnum.ARENA));
  }
 }
 @Test void noWosPageCannotDispatchAnything(){assertTrue(DailyReadyStatusFrame.actionable(null).isEmpty());}
}
