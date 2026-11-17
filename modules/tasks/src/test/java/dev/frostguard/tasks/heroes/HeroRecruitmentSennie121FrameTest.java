package dev.frostguard.tasks.heroes;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.vision.ocr.OcrEngine;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroRecruitmentSennie121FrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void advancedAndEpicKeyOnceButtonsHaveDifferentScreenScopedArtwork() throws Exception {
        RawImageData before=frame("recruit-recruit-current-redacted.png");
        assertTrue(matchesWithThreshold(before,TemplatesEnum.SERVE_EMU_HERO_ADVANCED_KEY_ONCE,45,792,350,903,96));
        assertTrue(matchesWithThreshold(before,TemplatesEnum.SERVE_EMU_HERO_EPIC_KEY_ONCE,45,1147,350,1270,96));
        assertFalse(matchesWithThreshold(before,TemplatesEnum.SERVE_EMU_HERO_ADVANCED_KEY_ONCE,45,1147,350,1270,96));
        assertFalse(matchesWithThreshold(before,TemplatesEnum.SERVE_EMU_HERO_EPIC_KEY_ONCE,45,792,350,903,96));
        // The orange x1 button does NOT disappear after a key recruitment.
        // The runtime must additionally verify the key counter decrement.
        RawImageData after=frame("recruit-after-both-one-redacted.png");
        assertTrue(matchesWithThreshold(after,TemplatesEnum.SERVE_EMU_HERO_ADVANCED_KEY_ONCE,45,792,350,903,96));
        assertTrue(matchesWithThreshold(after,TemplatesEnum.SERVE_EMU_HERO_EPIC_KEY_ONCE,45,1147,350,1270,96));
    }

    @Test void bothRealKeyRecruitRewardsShowVerifiedHeadingAndSafeExit() throws Exception {
        for(String scene:new String[]{"recruit-advanced-rewards-redacted.png",
                "recruit-epic-rewards-ready-redacted.png"}) {
            var img=frame(scene);
            assertTrue(matches(img,TemplatesEnum.SERVE_EMU_HERO_KEY_REWARDS_TITLE,230,215,500,355),scene+" title");
            assertTrue(matches(img,TemplatesEnum.SERVE_EMU_HERO_REWARDS_EXIT,190,1150,525,1240),scene+" exit");
            assertFalse(matches(img,TemplatesEnum.SERVE_EMU_HERO_ADVANCED_KEY_ONCE,45,792,350,903),scene);
        }
    }

    @Test void actualJavaOcrReadsCurrentKeyCountsInBeforeAfterScreens() throws Exception {
        assertEquals(2,ocrCount("recruit-recruit-current-redacted.png",224,89,267,127));
        assertEquals(3,ocrCount("recruit-recruit-current-redacted.png",418,89,461,127));
        assertEquals(1,ocrCount("recruit-after-both-one-redacted.png",224,89,267,127));
        assertEquals(2,ocrCount("recruit-after-both-one-redacted.png",418,89,461,127));
    }

    @Test void pointsChestNotReadyEvenAfterTwoKeyRecruitments() throws Exception {
        BufferedImage scene=image("recruit-after-both-one-redacted.png");
        assertFalse(HeroPointsChestDetector.isReady(scene));
    }

    private int ocrCount(String scene,int x1,int y1,int x2,int y2) throws Exception {
        String text=OcrEngine.recognizeText(frame(scene),new PointData(x1,y1),new PointData(x2,y2),
                OcrSettingsData.configurator().textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
                        .allowedGlyphs("0123456789").build());
        return HeroRecruitmentKeyPolicy.countFromOcr(text);
    }

    private boolean matches(RawImageData scene,TemplatesEnum template,int x1,int y1,int x2,int y2) {
        return matchesWithThreshold(scene,template,x1,y1,x2,y2,91);
    }

    private boolean matchesWithThreshold(RawImageData scene,TemplatesEnum template,
            int x1,int y1,int x2,int y2,int threshold) {
        return OpenCvPatternLocator.locatePattern(scene,template.getTemplate(),
                new PointData(x1,y1),new PointData(x2,y2),threshold).isFound();
    }

    private static BufferedImage image(String scene) throws Exception {
        return ImageIO.read(Objects.requireNonNull(HeroRecruitmentSennie121FrameTest.class.getResource("/heroes/"+scene)));
    }

    private static RawImageData frame(String name) throws Exception {
        BufferedImage screen=image(name);
        byte[] rgba=new byte[screen.getWidth()*screen.getHeight()*4];
        for(int y=0;y<screen.getHeight();y++)for(int x=0;x<screen.getWidth();x++){
            int rgb=screen.getRGB(x,y),i=(y*screen.getWidth()+x)*4;
            rgba[i]=(byte)(rgb>>16);rgba[i+1]=(byte)(rgb>>8);
            rgba[i+2]=(byte)rgb;rgba[i+3]=(byte)255;
        }
        return RawImageData.capture(rgba,screen.getWidth(),screen.getHeight(),32);
    }
}
