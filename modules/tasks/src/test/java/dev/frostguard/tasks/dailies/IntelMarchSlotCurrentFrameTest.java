package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntelMarchSlotCurrentFrameTest {
    @BeforeAll static void init() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void currentWosSennie121MarchQueueShowsBothLockedRows() throws Exception {
        byte[] png=Objects.requireNonNull(getClass().getResourceAsStream(
                "/intel/march-4-idle-2-locked-20261114-redacted.png")).readAllBytes();
        assertTrue(OpenCvPatternLocator.locatePattern(png,
                TemplatesEnum.MARCH_QUEUE_STATUS_UNLOCK.getTemplate(),
                new PointData(150,640),new PointData(300,713),90).isFound());
        assertTrue(OpenCvPatternLocator.locatePattern(png,
                TemplatesEnum.MARCH_QUEUE_STATUS_UNAVAILABLE.getTemplate(),
                new PointData(150,710),new PointData(320,790),90).isFound());
    }
}
