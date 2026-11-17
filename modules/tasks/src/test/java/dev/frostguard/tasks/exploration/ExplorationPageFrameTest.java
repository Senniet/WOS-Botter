package dev.frostguard.tasks.exploration;

import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationPageFrameTest {
    @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
    @Test void actualExplorationTopHeaderProvidesSafeBack()throws Exception{
        var stream=Objects.requireNonNull(getClass().getResourceAsStream("/exploration/qa-20261116/main-title.png"));
        var f=ExplorationLatestFrameProbeTest.frame(ImageIO.read(stream));
        assertTrue(ExplorationPageFrame.title(f));
        assertTrue(ExplorationPageFrame.verifiedBackCandidate(f).isFound());
    }
    @Test void squadSettingsCannotAuthorizeExplorationBack()throws Exception{
        var f=ExplorationLatestFrameProbeTest.frame(
                ExplorationLatestFrameProbeTest.image("squad-false-dialog.png"));
        assertFalse(ExplorationPageFrame.title(f));
        assertFalse(ExplorationPageFrame.verifiedBackCandidate(f).isFound());
    }
}
