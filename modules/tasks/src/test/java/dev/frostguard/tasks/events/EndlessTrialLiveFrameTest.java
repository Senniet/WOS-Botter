package dev.frostguard.tasks.events;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.tasks.dailies.DailyOverviewFrame;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests against actual November 18 Serve-Emu 720x1280 frames. */
class EndlessTrialLiveFrameTest {
    @BeforeAll static void nativeOpenCv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    static RawImageData frame(String name) throws Exception {
        BufferedImage src=ImageIO.read(EndlessTrialLiveFrameTest.class.getResourceAsStream("/endlesstrial/"+name));
        assertEquals(720,src.getWidth());assertEquals(1280,src.getHeight());
        byte[] rgba=new byte[720*1280*4];int i=0;
        for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
            int pixel=src.getRGB(x,y);
            rgba[i++]=(byte)(pixel>>16);rgba[i++]=(byte)(pixel>>8);
            rgba[i++]=(byte)pixel;rgba[i++]=(byte)255;
        }
        return RawImageData.capture(rgba,720,1280,32);
    }

    @Test void recognizesActualEventAndDailyDialogWithoutConfusingTheTwo() throws Exception {
        var event=frame("trial-entry.jpg");
        var daily=frame("daily-challenges.jpg");
        var strip=frame("events-strip.jpg");
        assertTrue(EndlessTrialLiveFrame.isTrialPage(event),"event page must match actual header");
        assertFalse(EndlessTrialLiveFrame.isDailyChallenges(event));
        assertTrue(EndlessTrialLiveFrame.isDailyChallenges(daily),"modal must match actual header");
        assertFalse(EndlessTrialLiveFrame.isTrialPage(daily));
        assertFalse(EndlessTrialLiveFrame.isTrialPage(strip));
        assertTrue(EndlessTrialLiveFrame.isEventCarousel(strip));
        assertFalse(EndlessTrialLiveFrame.isEventCarousel(event));
    }

    @Test void readsActualZeroOfThreeAndPreventsPrematureClaim() throws Exception {
        var daily=frame("daily-challenges.jpg");
        var progress=EndlessTrialLiveFrame.readModalProgress(daily);
        System.out.println("LIVE DAILY COUNTER="+progress);
        assertTrue(progress.verified(),"real 0/3 counter must parse before any attack");
        assertEquals(0,progress.completedAttacks());
        assertFalse(EndlessTrialLiveFrame.thirdClaimReady(daily),"must never claim at 0/3");
    }

    @Test void identifiesTenHourEventTimerAndNavigationGo() throws Exception {
        var entry=frame("trial-entry.jpg");
        var go=EndlessTrialLiveFrame.readGo(entry);
        System.out.println("LIVE TRIAL GO="+go);
        assertEquals(EndlessTrialLiveFrame.GoState.TIMED_GO,go.state());
        assertTrue(go.countdown() == null || go.countdown().toMinutes()>500);
    }

    @Test void realOneOfThreeUsesOnlyFirstEligibleClaim() throws Exception {
        var screenshot=frame("daily-one-of-three.jpg");
        var progress=EndlessTrialLiveFrame.readModalProgress(screenshot);
        assertTrue(progress.verified(),progress.toString());
        assertEquals(1,progress.completedAttacks(),progress.toString());
        assertTrue(EndlessTrialLiveFrame.claimButtonEnabled(screenshot,1));
        assertFalse(EndlessTrialLiveFrame.claimButtonEnabled(screenshot,2));
        assertFalse(EndlessTrialLiveFrame.claimButtonEnabled(screenshot,3));
        assertFalse(EndlessTrialLiveFrame.thirdClaimReady(screenshot));
    }

    @Test void realTwoOfThreeMustNotAuthorizeThirdClaim() throws Exception {
        var frame=frame("daily-two-of-three.jpg");
        var progress=EndlessTrialLiveFrame.readModalProgress(frame);
        assertTrue(progress.verified(),progress.toString());
        assertEquals(2,progress.completedAttacks(),progress.toString());
        assertFalse(EndlessTrialLiveFrame.thirdClaimReady(frame));
    }

    @Test void realThreeOfThreeAuthorizesExactlyOneThirdClaim() throws Exception {
        var frame=frame("daily-three-of-three.jpg");
        var progress=EndlessTrialLiveFrame.readModalProgress(frame);
        assertTrue(progress.verified(),progress.toString());
        assertEquals(3,progress.completedAttacks(),progress.toString());
        assertTrue(EndlessTrialLiveFrame.thirdClaimReady(frame),"3/3 and green third row Claim required");
    }

    @Test void liveAllClaimedFrameMustBeSuccessfulWithoutAnotherTap() throws Exception {
        var frame=frame("daily-after-claim.jpg");
        assertTrue(EndlessTrialLiveFrame.isDailyChallenges(frame));
        assertTrue(EndlessTrialLiveFrame.allDailyRewardsClaimed(frame));
        assertTrue(EndlessTrialLiveFrame.readModalProgress(frame).verified());
        assertEquals(3,EndlessTrialLiveFrame.readModalProgress(frame).completedAttacks());
        assertFalse(EndlessTrialLiveFrame.thirdClaimReady(frame));
    }

    @Test void eventTabTemplateMatchesActualUnselectedCarousel() throws Exception {
        var strip = frame("events-strip.jpg");
        var tab = OpenCvPatternLocator.locatePattern(strip,
                dev.frostguard.api.configs.TemplatesEnum.ENDLESS_TRIAL_TAB.getTemplate(),
                new dev.frostguard.api.domain.PointData(0,80),
                new dev.frostguard.api.domain.PointData(720,210),90);
        assertTrue(tab.isFound(),"Endless Trial carousel tab must be recognized on real screenshot");
    }

    @Test void disabledRewardsCannotBeClaimedEvenWithProgressVisible() throws Exception {
        var daily = frame("daily-challenges.jpg");
        for (int row=1;row<=3;row++)assertFalse(EndlessTrialLiveFrame.claimButtonEnabled(daily,row));
    }

    @Test void goParserRefusesUnknownStatesAndParsesReadyGo() {
        assertEquals(EndlessTrialLiveFrame.GoState.TIMED_GO,
                EndlessTrialLiveFrame.parseGo("Go 10:09:49").state());
        assertEquals(EndlessTrialLiveFrame.GoState.READY,
                EndlessTrialLiveFrame.parseGo("Go").state());
        assertEquals(EndlessTrialLiveFrame.GoState.UNKNOWN,
                EndlessTrialLiveFrame.parseGo("PURCHASE 50 GEMS").state());
        assertFalse(EndlessTrialLiveFrame.parseDailyProgress("0/2").verified());
        assertEquals(3,EndlessTrialLiveFrame.parseDailyProgress("3/3").completedAttacks());
    }

    @Test void readsRealDailySidebarWithoutStartingAnyTasks() throws Exception {
        var daily=frame("daily-overview.jpg");
        assertTrue(DailyOverviewFrame.isDailyOverview(daily));
        var summary=DailyOverviewFrame.summarize(daily);
        System.out.println("LIVE OVERVIEW="+summary);
        assertTrue(summary.arenaVisible());
        assertTrue(summary.petAdventureVisible());
        assertTrue(summary.lighthouseVisible());
    }
}
