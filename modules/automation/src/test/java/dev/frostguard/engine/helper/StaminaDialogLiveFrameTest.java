package dev.frostguard.engine.helper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StaminaDialogLiveFrameTest {
    @BeforeAll static void nativeCv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void liveObtainMoreTitleIsDetected() throws Exception {
        assertTrue(hasStaminaTitle("obtain-more-header-masked.png"));
    }

    @Test void realFailureChiefProfileIsNotStaminaDialog() throws Exception {
        assertFalse(hasStaminaTitle("chief-profile-header-masked.png"));
    }

    private boolean hasStaminaTitle(String name) throws Exception {
        byte[] frame = Objects.requireNonNull(getClass().getResourceAsStream(
                "/stamina/live-20261113/" + name)).readAllBytes();
        return OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_STAMINA_TITLE.getTemplate(),
                new PointData(100, 105), new PointData(650, 310), 90).isFound();
    }
}
