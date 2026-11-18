package dev.frostguard.tasks.exploration;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DailyLabyrinthFrameTest {
    @BeforeAll static void opencv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
    private static RawImageData capture(String name)throws Exception{
        try(InputStream in=DailyLabyrinthFrameTest.class.getResourceAsStream("/labyrinth-20261119/"+name)){
            assertNotNull(in,name);
            return ExplorationLatestFrameProbeTest.frame(ImageIO.read(in));
        }
    }
    @Test void labyrinthOverviewMustNotBeMistakenForTheCave(){
        assertFalse(DailyLabyrinthFrame.overview(null));
        assertFalse(DailyLabyrinthFrame.cave(null));
        assertFalse(DailyLabyrinthFrame.deploy(null).isFound());
        assertFalse(DailyLabyrinthFrame.victory(null));
    }
    @Test void overviewRequiresHeaderAndCurrentCaveEntrance()throws Exception{
        var overview=capture("overview-redacted.png");
        assertTrue(DailyLabyrinthFrame.overview(overview));
        assertFalse(DailyLabyrinthFrame.cave(overview));
        assertFalse(DailyLabyrinthFrame.challenge(overview).isFound());
    }
    @Test void caveEntranceStillMatchesWhenTheCountdownTimerChanges()throws Exception{
        var newer=capture("overview-countdown-shifted-redacted.png");
        assertTrue(DailyLabyrinthFrame.overviewHeader(newer));
        assertTrue(DailyLabyrinthFrame.overview(newer),"Dynamic portal countdown must not invalidate entrance");
    }
    @Test void caveReadyIsNotFormationNorBattleResult()throws Exception{
        var ready=capture("cave-ready-redacted.png");
        assertTrue(DailyLabyrinthFrame.cave(ready));
        assertTrue(DailyLabyrinthFrame.challenge(ready).isFound());
        assertFalse(DailyLabyrinthFrame.formation(ready));
        assertFalse(DailyLabyrinthFrame.victory(ready));
    }
    @Test void deploymentRequiresCorrectTroopFormationAndDeployButton()throws Exception{
        var formation=capture("formation-redacted.png");
        assertTrue(DailyLabyrinthFrame.formation(formation));
        assertTrue(DailyLabyrinthFrame.deploy(formation).isFound());
        assertFalse(DailyLabyrinthFrame.challenge(formation).isFound());
        assertFalse(DailyLabyrinthFrame.victory(formation));
    }
    @Test void victoryAndNextArePositiveProofOfCompletedChallenge()throws Exception{
        var win=capture("victory-redacted.png");
        assertTrue(DailyLabyrinthFrame.victory(win));
        assertTrue(DailyLabyrinthFrame.nextStage(win).isFound());
        assertFalse(DailyLabyrinthFrame.challenge(win).isFound());
    }
    @Test void nextStageFourAfterVictoryUsesSameSafeCaveHeaderAndChallenge()throws Exception{
        var next=capture("next-stage-after-victory-redacted.png");
        assertTrue(DailyLabyrinthFrame.cave(next));
        assertTrue(DailyLabyrinthFrame.challenge(next).isFound());
        assertFalse(DailyLabyrinthFrame.victory(next));
    }
    @Test void nextAdvancesTheStageButLeavesVictoryOverlayUntilExplicitDismiss()throws Exception{
        var nextPressed=capture("victory-after-next-redacted.png");
        assertTrue(DailyLabyrinthFrame.victory(nextPressed),
                "Next by itself does not dismiss the victory overlay");
        assertTrue(DailyLabyrinthFrame.nextStage(nextPressed).isFound());
        assertFalse(DailyLabyrinthFrame.cave(nextPressed));
    }
    @Test void caveReappearsAfterVictoryWasDismissed()throws Exception{
        var after=capture("cave-after-victory-redacted.png");
        assertTrue(DailyLabyrinthFrame.cave(after));
        assertFalse(DailyLabyrinthFrame.victory(after));
    }
}
