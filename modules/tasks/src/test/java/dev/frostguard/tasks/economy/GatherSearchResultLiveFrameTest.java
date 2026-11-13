package dev.frostguard.tasks.economy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GatherSearchResultLiveFrameTest {
    @BeforeAll
    static void loadNative() throws Exception {
        OpenCvPatternLocator.loadNativeLibrary();
    }

    @Test
    void actualSearchResultShowsGatherControl() throws Exception {
        assertTrue(hasGatherButton("/gather/search-after-result-20261113-masked.png"));
    }

    @Test
    void openSearchPanelBeforeSearchHasNoGatherControl() throws Exception {
        assertFalse(hasGatherButton("/gather/search-before-result-20261113-masked.png"));
    }

    private boolean hasGatherButton(String path) throws Exception {
        byte[] png = Objects.requireNonNull(getClass().getResourceAsStream(path)).readAllBytes();
        return OpenCvPatternLocator.locatePattern(png,
                TemplatesEnum.GAME_HOME_SHORTCUTS_FARM_GATHER.getTemplate(),
                new PointData(180, 545), new PointData(530, 700), 86).isFound();
    }
}
