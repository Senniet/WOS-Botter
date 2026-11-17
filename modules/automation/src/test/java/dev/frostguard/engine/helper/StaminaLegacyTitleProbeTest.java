package dev.frostguard.engine.helper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StaminaLegacyTitleProbeTest {
    @BeforeAll static void loadCv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test
    void olderEmulatorDialogStillMatchesTheSameSafeStaminaTitleGuard() throws Exception {
        byte[] frame = Objects.requireNonNull(getClass().getResourceAsStream(
                "/stamina/obtain-more-130-of-200.png")).readAllBytes();
        var hit = OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_STAMINA_TITLE.getTemplate(),
                new PointData(100, 105), new PointData(650, 310), 90);
        assertTrue(hit.isFound(), "The dialog guard must not block older emulator Obtain-more layouts");
    }
}
