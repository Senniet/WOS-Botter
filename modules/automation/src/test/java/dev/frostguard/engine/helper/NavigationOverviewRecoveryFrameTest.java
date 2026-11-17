package dev.frostguard.engine.helper;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavigationOverviewRecoveryFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void liveResourceProductionOverviewHasBothSafeExitAnchors() throws Exception {
        assertTrue(title("overview-redacted.png"));
        assertTrue(close("overview-redacted.png"));
    }

    @Test void doNotCloseOnCityWorldOrHeroChestAnimation() throws Exception {
        for (String scene : new String[]{"city-redacted.png", "world-redacted.png",
                "hero-animation-redacted.png"}) {
            assertFalse(title(scene), scene + " title false positive");
            assertFalse(close(scene), scene + " close false positive");
        }
    }

    private boolean title(String scene) throws Exception {
        return OpenCvPatternLocator.locatePattern(frame(scene),
                TemplatesEnum.SERVE_EMU_RESOURCES_OVERVIEW_TITLE_2026.getTemplate(),
                new PointData(0,315),new PointData(250,420),92).isFound();
    }

    private boolean close(String scene) throws Exception {
        return OpenCvPatternLocator.locatePattern(frame(scene),
                TemplatesEnum.SERVE_EMU_RESOURCES_OVERVIEW_CLOSE_2026.getTemplate(),
                new PointData(610,310),new PointData(718,420),92).isFound();
    }

    private byte[] frame(String scene) throws Exception {
        return Objects.requireNonNull(getClass().getResourceAsStream(
                "/navigation/overview-20261114/" + scene)).readAllBytes();
    }
}
