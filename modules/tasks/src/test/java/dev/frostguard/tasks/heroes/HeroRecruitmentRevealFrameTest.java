package dev.frostguard.tasks.heroes;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class HeroRecruitmentRevealFrameTest {
    @BeforeAll static void cv() throws Exception {
        OpenCvPatternLocator.loadNativeLibrary();
    }

    @Test void alreadyOwnedCharlieCardConfirmed() throws Exception {
        assertTrue(HeroRecruitmentRevealFrame.alreadyOwned(
                fixture("/heroes/live-20261121/hero-already-owned-charlie.png")));
    }

    @Test void modernRecruitmentPageIsNotAReveal() throws Exception {
        assertFalse(HeroRecruitmentRevealFrame.alreadyOwned(
                fixture("/heroes/live-20261119/recruitment-countdown-redacted.png")));
    }

    @Test void ordinaryRewardExitIsNotAReveal() throws Exception {
        assertFalse(HeroRecruitmentRevealFrame.alreadyOwned(
                fixture("/heroes/live-20261119/rewards-exit-redacted.png")));
    }

    @Test void unexpectedFrameDoesNotAuthorizeAnyTap() {
        assertFalse(HeroRecruitmentRevealFrame.alreadyOwned(null));
    }

    private RawImageData fixture(String name) throws Exception {
        BufferedImage frame;
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream(name))) {
            frame=ImageIO.read(input);
        }
        int w=frame.getWidth(), h=frame.getHeight();
        byte[] rgba=new byte[w*h*4];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int color=frame.getRGB(x,y),i=(y*w+x)*4;
            rgba[i]=(byte)(color>>>16);
            rgba[i+1]=(byte)(color>>>8);
            rgba[i+2]=(byte)color;
            rgba[i+3]=(byte)255;
        }
        return RawImageData.capture(rgba,w,h,32);
    }
}
