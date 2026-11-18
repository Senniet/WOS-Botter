package dev.frostguard.tasks.pets;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.tasks.exploration.ExplorationLatestFrameProbeTest;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AllianceTreeCareFrameTest {
    @BeforeAll static void init()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
    static RawImageData frame(String name)throws Exception {
        try(InputStream in=AllianceTreeCareFrameTest.class.getResourceAsStream(
                "/alliance-tree-20261118/"+name)){
            assertNotNull(in,name);
            return ExplorationLatestFrameProbeTest.frame(ImageIO.read(in));
        }
    }
    @Test void blueAllianceHelpIconsAreDetectedDirectlyOnWorld()throws Exception{
        var world=frame("world-badges-redacted.png");
        assertTrue(AllianceTreeCareFrame.world(world));
        var icon=AllianceTreeCareFrame.worldCareBadge(world);
        assertTrue(icon.isFound(),"2026 alliance Tree of Life care icon visible directly above members' cities");
        assertTrue(icon.getPoint().getX()>60&&icon.getPoint().getX()<628);
        assertTrue(icon.getPoint().getY()>200&&icon.getPoint().getY()<1090);
        assertFalse(AllianceTreeCareFrame.island(world));
    }
    @Test void chiefIslandConfirmationRequiresExactText()throws Exception{
        var question=frame("visit-confirm-redacted.png");
        assertTrue(AllianceTreeCareFrame.visitQuestion(question));
        assertFalse(AllianceTreeCareFrame.worldCareBadge(question).isFound());
        assertFalse(AllianceTreeCareFrame.island(question));
    }
    @Test void islandCareIconAndRemainingAttemptsPrecedeClaim()throws Exception{
        var ready=frame("island-ready-redacted.png");
        assertFalse(AllianceTreeCareFrame.world(ready));
        assertTrue(AllianceTreeCareFrame.island(ready));
        assertTrue(AllianceTreeCareFrame.islandCareBadge(ready).isFound());
        assertEquals(3,AllianceTreeCareFrame.availableCareAttempts(ready));
        assertFalse(AllianceTreeCareFrame.helpedToast(ready));
    }
    @Test void aDifferentAllyIslandIsRecognizedDespiteDifferentTreeAndBackground()throws Exception{
        var second=frame("another-island-ready-redacted.png");
        assertTrue(AllianceTreeCareFrame.island(second));
        assertTrue(AllianceTreeCareFrame.islandCareBadge(second).isFound());
        assertEquals(2,AllianceTreeCareFrame.availableCareAttempts(second));
    }
    @Test void differentlyAnimatedTreeLeavesStillPreserveExactBlueCaringIcon()throws Exception{
        var frame=frame("animated-other-island-ready-redacted.png");
        assertTrue(AllianceTreeCareFrame.island(frame));
        assertTrue(AllianceTreeCareFrame.islandCareBadge(frame).isFound());
        assertEquals(2,AllianceTreeCareFrame.availableCareAttempts(frame));
    }
    @Test void oneRemainingIsDetectedOnAThirdIndependentIsland()throws Exception{
        var f=frame("last-island-ready-redacted.png");
        assertTrue(AllianceTreeCareFrame.island(f));
        assertTrue(AllianceTreeCareFrame.islandCareBadge(f).isFound());
        assertEquals(1,AllianceTreeCareFrame.availableCareAttempts(f));
    }
    @Test void oneRemainingIsRecognizedOnFourthAllyIsland()throws Exception{
        var f=frame("last-island-another-ready-redacted.png");
        assertTrue(AllianceTreeCareFrame.island(f));
        assertTrue(AllianceTreeCareFrame.islandCareBadge(f).isFound());
        assertEquals(1,AllianceTreeCareFrame.availableCareAttempts(f));
    }
    @Test void verifiedSuccessRemovesCareIconAndReducesAttempts()throws Exception{
        var done=frame("island-cared-redacted.png");
        assertTrue(AllianceTreeCareFrame.island(done));
        assertFalse(AllianceTreeCareFrame.islandCareBadge(done).isFound());
        assertEquals(2,AllianceTreeCareFrame.availableCareAttempts(done));
        assertTrue(AllianceTreeCareFrame.helpedToast(done));
    }
    @Test void unknownOrDifferentScreenNeverAuthorizesCare(){
        assertFalse(AllianceTreeCareFrame.world(null));
        assertFalse(AllianceTreeCareFrame.visitQuestion(null));
        assertFalse(AllianceTreeCareFrame.islandCareBadge(null).isFound());
        assertEquals(-1,AllianceTreeCareFrame.availableCareAttempts(null));
    }
}
