package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrainingCampTabCurrentFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void trainingPageShowsCampTabWhereWorldAndCityDoNot() throws Exception {
        assertTrue(tab("training-redacted.png"));
        assertFalse(tab("world-redacted.png"));
        assertFalse(tab("city-redacted.png"));
    }

    private boolean tab(String name) throws Exception {
        byte[] png = Objects.requireNonNull(getClass().getResourceAsStream(
                "/training/live-tab-20261114/" + name)).readAllBytes();
        PointData a=new PointData(0,1160),b=new PointData(248,1275);
        return OpenCvPatternLocator.locatePattern(png,
                TemplatesEnum.TRAINING_CAMP_INFANTRY_TAB_LABEL_2026.getTemplate(),a,b,92).isFound()
                || OpenCvPatternLocator.locatePattern(png,
                TemplatesEnum.TRAINING_CAMP_INFANTRY_TAB_ACTIVE.getTemplate(),a,b,90).isFound()
                || OpenCvPatternLocator.locatePattern(png,
                TemplatesEnum.TRAINING_CAMP_INFANTRY_TAB_INACTIVE.getTemplate(),a,b,90).isFound();
    }
}
