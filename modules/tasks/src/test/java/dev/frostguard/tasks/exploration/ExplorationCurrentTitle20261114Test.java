package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationCurrentTitle20261114Test {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void recognizesVisibleExplorationTitleFromFailedEntrySnapshot() throws Exception {
        assertTrue(isCurrentTitle("exploration-header-redacted.png"));
    }

    @Test void rejectsWorldCityAndChestScreens() throws Exception {
        for(String frame : new String[]{"city-header-redacted.png",
                "world-header-redacted.png","blue-chest-header-redacted.png"}) {
            assertFalse(isCurrentTitle(frame),frame);
        }
    }

    private boolean isCurrentTitle(String name) throws Exception {
        byte[] image=Objects.requireNonNull(getClass().getResourceAsStream(
                "/exploration/live-entry-20261114/"+name)).readAllBytes();
        return OpenCvPatternLocator.locatePattern(image,
                TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE_NOV_2026.getTemplate(),
                new PointData(65,0),new PointData(335,115),92).isFound();
    }
}
