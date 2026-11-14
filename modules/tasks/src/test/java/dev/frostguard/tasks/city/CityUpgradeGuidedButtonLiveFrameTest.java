package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CityUpgradeGuidedButtonLiveFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void guideHandStillLeavesDistinctUpgradeActionOnFailedScreenshot() throws Exception {
        assertTrue(guided("guided-redacted.png"));
    }

    @Test void cannotConfuseNormalScreensWithGuidedUpgrade() throws Exception {
        for(String scene:new String[]{"city-redacted.png","world-redacted.png","research-redacted.png"})
            assertFalse(guided(scene),scene);
    }

    private boolean guided(String file) throws Exception {
        byte[] img=Objects.requireNonNull(getClass().getResourceAsStream(
                "/city/guided-upgrade-20261114/"+file)).readAllBytes();
        return OpenCvPatternLocator.locatePattern(img,
                TemplatesEnum.SERVE_EMU_BUILDING_GUIDED_UPGRADE_2026.getTemplate(),
                new PointData(275,790),new PointData(475,965),94).isFound();
    }
}
