package dev.frostguard.engine.helper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.io.IOException;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NavigationResourceSearchFrameTest {
    @BeforeAll static void init() throws IOException { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void identifiesResourcePanelOnRealScreensButNotWorld() throws Exception {
        assertTrue(hasResourcePanel("panel-visible.png"));
        assertTrue(hasResourcePanel("panel-with-result.png"));
        assertFalse(hasResourcePanel("panel-absent.png"));
    }

    @Test void gatherModalIsOnlyPresentAfterSuccessfulSearch() throws Exception {
        assertTrue(hasGatherModal("panel-with-result.png"));
        assertFalse(hasGatherModal("panel-visible.png"));
        assertFalse(hasGatherModal("panel-absent.png"));
    }

    private boolean hasResourcePanel(String name) throws Exception {
        byte[] png = data(name);
        return match(png, TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_MEAT, 0, 835, 720, 995, 90)
                || match(png, TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_WOOD, 0, 835, 720, 995, 90);
    }

    private boolean hasGatherModal(String name) throws Exception {
        return match(data(name), TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_GATHER,
                180, 545, 530, 700, 86);
    }

    private byte[] data(String name) throws Exception {
        return Objects.requireNonNull(getClass().getResourceAsStream(
                "/navigation/resource-search-live/" + name)).readAllBytes();
    }

    private boolean match(byte[] frame, TemplatesEnum template,
            int x1, int y1, int x2, int y2, int threshold) throws Exception {
        return OpenCvPatternLocator.locatePattern(frame, template.getTemplate(),
                new PointData(x1, y1), new PointData(x2, y2), threshold).isFound();
    }
}
