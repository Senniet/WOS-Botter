package dev.frostguard.tasks.events;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.tasks.exploration.ExplorationLatestFrameProbeTest;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 2026-11-18 actual Serve-Emu King of Icefield Earned Claim evidence. */
class KingIcefieldMilestoneFrameTest {
    @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
    static RawImageData frame(String file)throws Exception{
        try(InputStream input=KingIcefieldMilestoneFrameTest.class.getResourceAsStream(
                "/new-rewards-20261118/"+file)){
            assertNotNull(input,file);
            BufferedImage image=ImageIO.read(input);
            return ExplorationLatestFrameProbeTest.frame(image);
        }
    }
    @Test void oneActuallyClaimableEventMilestoneIsPreciselyMatched()throws Exception{
        RawImageData ready=frame("king-ready-redacted.png");
        assertTrue(KingIcefieldMilestoneFrame.eventsPanel(ready));
        assertTrue(KingIcefieldMilestoneFrame.kingPage(ready));
        var claim=KingIcefieldMilestoneFrame.unclaimedMilestone(ready);
        assertTrue(claim.isFound(),"Green Claim observed at the Stage 3 159k reward");
        assertTrue(claim.getPoint().getX()>530 && claim.getPoint().getX()<650);
        assertTrue(claim.getPoint().getY()>765 && claim.getPoint().getY()<850);
        assertFalse(KingIcefieldMilestoneFrame.claimedAt(ready,claim.getPoint()));
    }
    @Test void actualAfterClaimFrameHasCheckmarkNotAClickableReward()throws Exception{
        RawImageData claimed=frame("king-claimed-redacted.png");
        assertTrue(KingIcefieldMilestoneFrame.kingPage(claimed));
        assertFalse(KingIcefieldMilestoneFrame.unclaimedMilestone(claimed).isFound());
        assertTrue(KingIcefieldMilestoneFrame.claimedAt(claimed,
                new dev.frostguard.api.domain.PointData(583,799)));
    }
    @Test void cityHudHasVerifiedEventsButtonBeforeEntry()throws Exception{
        var home=frame("home-event-icon-redacted.png");
        assertTrue(KingIcefieldMilestoneFrame.eventClosed(home));
        var button=OpenCvPatternLocator.locatePattern(home,
                dev.frostguard.api.configs.TemplatesEnum.HOME_EVENTS_BUTTON.getTemplate(),
                new dev.frostguard.api.domain.PointData(575,78),
                new dev.frostguard.api.domain.PointData(719,255),87);
        assertTrue(button.isFound(),"The live 2026 City HUD must match the Events shortcut");
        assertFalse(KingIcefieldMilestoneFrame.eventsPanel(home));
    }
    @Test void calendarAndUnknownScreensCannotAuthorizeAClick()throws Exception{
        RawImageData calendar=frame("events-calendar-redacted.png");
        assertTrue(KingIcefieldMilestoneFrame.eventsPanel(calendar));
        assertFalse(KingIcefieldMilestoneFrame.kingPage(calendar));
        assertTrue(KingIcefieldMilestoneFrame.kingTab(calendar).isFound());
        assertFalse(KingIcefieldMilestoneFrame.unclaimedMilestone(calendar).isFound());
        assertFalse(KingIcefieldMilestoneFrame.kingPage(null));
    }
}
