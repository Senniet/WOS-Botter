package dev.frostguard.tasks.exploration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExplorationSquadActionsLiveFrameTest {
    @BeforeAll static void nativeCv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void liveExplorationSquadHasBothActions() throws Exception {
        byte[] screenshot = Objects.requireNonNull(getClass().getResourceAsStream(
                "/exploration/live-20261113/squad-action-buttons-masked.png")).readAllBytes();
        assertTrue(OpenCvPatternLocator.locatePattern(screenshot,
                TemplatesEnum.EXPLORATION_QUICK_DEPLOY_LIVE.getTemplate(),
                new PointData(30,1145),new PointData(360,1275),90).isFound());
        assertTrue(OpenCvPatternLocator.locatePattern(screenshot,
                TemplatesEnum.INTEL_JOURNEY_FIGHT_LIVE.getTemplate(),
                new PointData(365,1145),new PointData(710,1275),90).isFound());
    }

    @Test void unrelatedRewardFrameCannotMasqueradeAsSquadSettings() throws Exception {
        byte[] screenshot = Objects.requireNonNull(getClass().getResourceAsStream(
                "/serveemu/rewards-title-only.png")).readAllBytes();
        assertFalse(OpenCvPatternLocator.locatePattern(screenshot,
                TemplatesEnum.EXPLORATION_QUICK_DEPLOY_LIVE.getTemplate(),
                new PointData(30,1145),new PointData(360,1275),90).isFound());
    }
}
