package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntelClaimRewardsFrameTest {
    @BeforeAll static void load() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }
    @Test void rewardTitleCanBeDistinguishedFromIntelMap() throws Exception {
        byte[] rewarded = image("/serveemu/rewards-title-only.png");
        byte[] intel = image("/intel/intel-claim-all-live.png");
        var detected = OpenCvPatternLocator.locatePattern(rewarded,
                TemplatesEnum.SERVE_EMU_REWARDS_BANNER.getTemplate(),
                new PointData(40,245), new PointData(680,425),90);
        var rejected = OpenCvPatternLocator.locatePattern(intel,
                TemplatesEnum.SERVE_EMU_REWARDS_BANNER.getTemplate(),
                new PointData(40,245), new PointData(680,425),90);
        assertTrue(detected.isFound(), "Saved Rewards overlay must match the gated dismiss step");
        assertFalse(rejected.isFound(), "Intel map must never be dismissed as Rewards");
    }
    private byte[] image(String path) throws Exception {
        return Objects.requireNonNull(getClass().getResourceAsStream(path)).readAllBytes();
    }
}
