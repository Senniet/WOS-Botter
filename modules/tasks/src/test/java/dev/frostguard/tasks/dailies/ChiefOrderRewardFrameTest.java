package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.tasks.exploration.ExplorationLatestFrameProbeTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Only one safe Rewards-exit click, never on another page. */
class ChiefOrderRewardFrameTest {
    @BeforeAll static void nativeOpenCv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}

    @Test void recognisesActualRushJobRewardsNotSquad()throws Exception{
        assertTrue(ChiefOrderRewardFrame.isRewards(rewards()));
        assertFalse(ChiefOrderRewardFrame.isRewards(squad()));
    }

    @Test void twoFramesOneExitTapAndScreenChanges()throws Exception{
        var reward=rewards();var other=squad();
        var captures=List.of(reward,reward,other);
        var count=new AtomicInteger();var taps=new ArrayList<String>();
        var result=ChiefOrderRewardFrame.dismissIfVisible(
                ()->captures.get(count.getAndIncrement()),
                point->taps.add(point.getX()+","+point.getY()),()->{});
        assertEquals(ChiefOrderRewardFrame.Exit.DISMISSED,result);
        assertEquals(1,taps.size());assertEquals("360,1190",taps.getFirst());
        assertEquals(3,count.get());
    }

    @Test void noTapOnOtherOrChangedRewardScreen()throws Exception{
        var reward=rewards();var other=squad();var taps=new AtomicInteger();
        assertEquals(ChiefOrderRewardFrame.Exit.NOT_PRESENT,
                ChiefOrderRewardFrame.dismissIfVisible(()->other,p->taps.incrementAndGet(),()->{}));
        var i=new AtomicInteger();
        assertEquals(ChiefOrderRewardFrame.Exit.UNVERIFIED,
                ChiefOrderRewardFrame.dismissIfVisible(()->i.getAndIncrement()==0?reward:other,
                        p->taps.incrementAndGet(),()->{}));
        assertEquals(0,taps.get());
    }

    @Test void neverDoubleTapsWhenRewardsStayOpen()throws Exception{
        var reward=rewards();var taps=new AtomicInteger();
        assertEquals(ChiefOrderRewardFrame.Exit.UNVERIFIED,
                ChiefOrderRewardFrame.dismissIfVisible(()->reward,p->taps.incrementAndGet(),()->{}));
        assertEquals(1,taps.get());
    }

    private static RawImageData rewards()throws Exception{
        return ExplorationLatestFrameProbeTest.frame(
                ExplorationLatestFrameProbeTest.image("chief-rewards.png"));
    }
    private static RawImageData squad()throws Exception{
        return ExplorationLatestFrameProbeTest.frame(
                ExplorationLatestFrameProbeTest.image("squad-false-dialog.png"));
    }
}
