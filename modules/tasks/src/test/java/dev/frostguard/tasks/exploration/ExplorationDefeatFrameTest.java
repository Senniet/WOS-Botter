package dev.frostguard.tasks.exploration;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Account-redacted real 2026-11-18 Defeat + Return to City live ADB evidence. */
class ExplorationDefeatFrameTest {
    @BeforeAll static void nativeOpenCv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    static RawImageData load(String name) throws Exception {
        try (InputStream source=ExplorationDefeatFrameTest.class.getResourceAsStream(
                "/exploration/defeat-live/"+name)) {
            assertNotNull(source, name);
            BufferedImage image=ImageIO.read(source);
            return ExplorationLatestFrameProbeTest.frame(image);
        }
    }

    @Test void defeatAndExactReturnToCityAreBothVerifiedOnRealScreen() throws Exception {
        RawImageData frame=load("defeat-20261118-redacted.png");
        assertTrue(ExplorationDefeatFrame.visible(frame));
        var cta=ExplorationDefeatFrame.returnToCity(frame);
        assertTrue(cta.isFound(),"Defeat must have verified blue Return to City action");
        assertTrue(cta.getPoint().getX()<365,"must select left Return to City, not right Retry");
        assertTrue(cta.getPoint().getY()>885 && cta.getPoint().getY()<1010);
        assertEquals(ExplorationFastResultFrame.State.DEFEAT,
                ExplorationFastResultFrame.classify(frame,false));
    }

    @Test void cityAndVictoryDoNotAuthorizeDefeatDismissal() throws Exception {
        RawImageData city=load("city-after-defeat-redacted.png");
        assertFalse(ExplorationDefeatFrame.visible(city));
        assertFalse(ExplorationDefeatFrame.returnToCity(city).isFound());
        assertEquals(ExplorationRootFrame.Root.CITY, ExplorationRootFrame.root(city),
                "After Return to City the actual City HUD must be positively recognized");
        RawImageData victory=ExplorationLatestFrameProbeTest.frame(ImageIO.read(
                ExplorationDefeatFrameTest.class.getResourceAsStream(
                    "/exploration/qa-20261116/victory-return-city-redacted.png")));
        assertFalse(ExplorationDefeatFrame.visible(victory));
        assertFalse(ExplorationDefeatFrame.returnToCity(victory).isFound());
    }
}
