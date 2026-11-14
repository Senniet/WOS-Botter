package dev.frostguard.tasks.lifecycle;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnownGameScreenResumeFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void identifiesLiveRecruitmentAndTheNestedHeroesMenuForQuickSecondStart() throws Exception {
        assertEquals(KnownGameScreenResume.Page.HERO_RECRUITMENT,
                KnownGameScreenResume.findSafeBackPage(frame("recruit-page-redacted.png")));
        assertEquals(KnownGameScreenResume.Page.HEROES,
                KnownGameScreenResume.findSafeBackPage(frame("heroes-page-redacted.png")));
    }

    @Test void recognizesLiveSquadSettingsScreenFromSecondStartWithoutSendingInput() throws Exception {
        assertEquals(KnownGameScreenResume.Page.SQUAD_SETTINGS,
                KnownGameScreenResume.findSafeBackPage(frame("squad-settings-page-redacted.png")));
    }

    @Test void neverIssuesBackOnRewardsOrCityWithoutAConfirmedPageTitle() throws Exception {
        assertNull(KnownGameScreenResume.findSafeBackPage(frame("recruit-rewards-redacted.png")));
        assertNull(KnownGameScreenResume.findSafeBackPage(frame("recruit-city-redacted.png")));
    }

    @Test void recognizesBothLiveAllianceAndWarPagesWithoutExitingTheGame() throws Exception {
        assertEquals(KnownGameScreenResume.Page.ALLIANCE,
                KnownGameScreenResume.findSafeBackPage(frame(
                        "alliance-war-20261114/alliance-header-redacted.png")));
        assertEquals(KnownGameScreenResume.Page.WAR,
                KnownGameScreenResume.findSafeBackPage(frame(
                        "alliance-war-20261114/war-header-redacted.png")));
    }

    @Test void allianceAndWarScreensDoNotMislabelCityWorldOrIntel() throws Exception {
        for(String f:new String[]{"city", "world", "intel"}) {
            assertNull(KnownGameScreenResume.findSafeBackPage(frame(
                    "alliance-war-20261114/"+f+"-header-redacted.png")));
        }
    }

    @Test void requiresTwoMatchingFreshTitleObservationsAndCorrectViewport() throws Exception {
        var page=KnownGameScreenResume.Page.HERO_RECRUITMENT;
        assertTrue(KnownGameScreenResume.confirmsSamePage(page,page));
        assertFalse(KnownGameScreenResume.confirmsSamePage(page,KnownGameScreenResume.Page.HEROES));
        assertFalse(KnownGameScreenResume.confirmsSamePage(null,page));
        assertNull(KnownGameScreenResume.findSafeBackPage(
                RawImageData.capture(new byte[4],1,1,32)));
    }

    private RawImageData frame(String name) throws Exception {
        BufferedImage image=ImageIO.read(Objects.requireNonNull(
                getClass().getResource("/initialize/"+name)));
        byte[] rgba=new byte[image.getWidth()*image.getHeight()*4];
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            int rgb=image.getRGB(x,y),i=(y*image.getWidth()+x)*4;
            rgba[i]=(byte)(rgb>>16);rgba[i+1]=(byte)(rgb>>8);
            rgba[i+2]=(byte)rgb;rgba[i+3]=(byte)255;
        }
        return RawImageData.capture(rgba,image.getWidth(),image.getHeight(),32);
    }
}
