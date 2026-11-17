package dev.frostguard.tasks.exploration;

import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.Test;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationRewardsBannerFrameTest {
    @Test void rewardsBannerMatchesExistingArenaRewardPanel() throws Exception {
        try { OpenCvPatternLocator.loadNativeLibrary(); } catch (UnsatisfiedLinkError ignored) { }
        var frame = Objects.requireNonNull(getClass().getResourceAsStream(
                "/arena/arena-rewards-panel-20261022.png")).readAllBytes();
        ImageSearchResultData hit = OpenCvPatternLocator.locatePattern(frame,
                "/templates/arena/arenaRewardsBanner.png",new PointData(30,245),
                new PointData(690,400),85);
        assertTrue(hit.isFound(), "Game's Rewards banner should be recognisable: " + hit);
    }
}
