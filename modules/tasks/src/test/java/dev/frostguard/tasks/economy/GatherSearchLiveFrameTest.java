package dev.frostguard.tasks.economy;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GatherSearchLiveFrameTest {
    @BeforeAll static void load() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void resourcePanelShowsMeatAndWoodOnLiveEmulator() throws Exception {
        byte[] png = image("/gather/resource-search-20261113-masked.png");
        assertTrue(matches(png, TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_MEAT));
        assertTrue(matches(png, TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_WOOD));
    }

    @Test void worldWithoutSearchPanelDoesNotMatchResourceTabs() throws Exception {
        byte[] png = image("/gather/world-without-search-20261113-masked.png");
        assertFalse(matches(png, TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_MEAT));
        assertFalse(matches(png, TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_WOOD));
    }

    private static byte[] image(String path) throws Exception {
        return Objects.requireNonNull(GatherSearchLiveFrameTest.class
                .getResourceAsStream(path)).readAllBytes();
    }

    private static boolean matches(byte[] png, TemplatesEnum template) throws Exception {
        return OpenCvPatternLocator.locatePattern(png, template.getTemplate(),
                new PointData(0, 835), new PointData(720, 995), 90).isFound();
    }
}
