package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationLiveClaim20261113Test {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void snapshotAfterOuterClaimAlreadyContainsInnerModalAndReadyGreenClaim() throws Exception {
        byte[] data = Objects.requireNonNull(getClass().getResourceAsStream(
                "/exploration/live-20261113/idle-income-modal-redacted.png")).readAllBytes();
        assertTrue(OpenCvPatternLocator.locatePattern(data,
                TemplatesEnum.SERVE_EMU_IDLE_INCOME_TITLE_NOV_2026.getTemplate(),
                new PointData(210,233),new PointData(520,330),90).isFound());
        var image=ImageIO.read(new java.io.ByteArrayInputStream(data));
        assertTrue(GreenLabelActionDetector.detect(image,
                new GreenLabelActionDetector.Region(213,875,507,972)).isFound());
    }
}
