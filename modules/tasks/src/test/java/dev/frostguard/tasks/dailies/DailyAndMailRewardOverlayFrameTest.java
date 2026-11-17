package dev.frostguard.tasks.dailies;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DailyAndMailRewardOverlayFrameTest {
    @BeforeAll static void init() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void onlyRealRewardsPagesAuthorizeDismissal() throws Exception {
        assertTrue(isRewards("/serveemu/rewards-title-only.png"));
        assertFalse(isRewards("/serveemu/mailTitle-title-only.png"));
        assertFalse(isRewards("/dailies/daily-live-masked-20261113.png"));
    }

    private boolean isRewards(String path) throws Exception {
        byte[] png = Objects.requireNonNull(getClass().getResourceAsStream(path)).readAllBytes();
        return OpenCvPatternLocator.locatePattern(png,
                TemplatesEnum.SERVE_EMU_REWARDS_BANNER.getTemplate(),
                new PointData(40,245), new PointData(680,425),90).isFound();
    }
}
