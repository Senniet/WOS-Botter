package dev.frostguard.tasks.exploration;

import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationChestActionFrameTest {
    @BeforeAll static void nativeCv() throws Exception {OpenCvPatternLocator.loadNativeLibrary();}

    private static BufferedImage liveReady() throws Exception {
        try(var stream=Objects.requireNonNull(ExplorationChestActionFrameTest.class
                .getResourceAsStream("/exploration/live-20261117/claim-ready-redacted.png"))) {
            return ImageIO.read(stream);
        }
    }

    @Test void live20261117GreenClaimIsReadyAndNotDisabled() throws Exception {
        var frame=ExplorationLatestFrameProbeTest.frame(liveReady());
        assertTrue(ExplorationPageFrame.title(frame));
        assertTrue(ExplorationChestActionFrame.enabled(frame).isFound(),
                "Real 5554 Claim button must be recognized by GREEN body, not text only");
        assertFalse(ExplorationChestActionFrame.disabled(frame),
                "Grey Claim template also resembles active green Claim artwork");
    }

    @Test void live20261117DisabledAfterClaimIsNotTapEligible() throws Exception {
        try(var stream=Objects.requireNonNull(ExplorationChestActionFrameTest.class
                .getResourceAsStream("/exploration/live-20261117/claim-disabled-redacted.png"))) {
            var frame=ExplorationLatestFrameProbeTest.frame(ImageIO.read(stream));
            assertTrue(ExplorationPageFrame.title(frame));
            assertFalse(ExplorationChestActionFrame.enabled(frame).isFound(),
                    "After live Idle Income claim, grey button must not trigger another Claim tap");
            assertTrue(ExplorationChestActionFrame.disabled(frame),
                    "After claiming, the grey Claim must count as no reward available");
        }
    }

    @Test void sameClaimTextWithoutGreenFillMustNotAuthorizeTap() throws Exception {
        var screenshot=liveReady();
        for(int y=830;y<909;y++)for(int x=535;x<701;x++) {
            int rgb=screenshot.getRGB(x,y);
            int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;
            if(g>125 && g>r+45 && g>b+30)
                screenshot.setRGB(x,y,0xff7d7d7d);
        }
        var frame=ExplorationLatestFrameProbeTest.frame(screenshot);
        assertTrue(ExplorationPageFrame.title(frame));
        assertFalse(ExplorationChestActionFrame.enabled(frame).isFound(),
                "The text Claim is NOT sufficient when the button is grey");
    }

    @Test void otherPageCannotAuthorizeExplorationButton() throws Exception {
        var screenshot=liveReady();
        for(int y=0;y<120;y++)for(int x=0;x<400;x++)screenshot.setRGB(x,y,0xff000000);
        assertFalse(ExplorationChestActionFrame.enabled(
                ExplorationLatestFrameProbeTest.frame(screenshot)).isFound());
    }
}
