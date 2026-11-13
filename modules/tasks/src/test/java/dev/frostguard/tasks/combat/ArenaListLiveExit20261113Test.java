package dev.frostguard.tasks.combat;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaListLiveExit20261113Test {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void listHeaderAndItsCloseCrossBothVerifiedOnLiveArenaScreenshot() throws Exception {
        byte[] png=Objects.requireNonNull(getClass().getResourceAsStream(
                "/arena/live-20261113/challenge-list-header-redacted.png")).readAllBytes();
        assertTrue(OpenCvPatternLocator.locatePattern(png,TemplatesEnum.SERVE_EMU_ARENA_LIST_TITLE.getTemplate(),
                new PointData(230,170),new PointData(510,280),90).isFound());
        assertTrue(OpenCvPatternLocator.locatePattern(png,TemplatesEnum.SERVE_EMU_ARENA_LIST_CLOSE_2026.getTemplate(),
                new PointData(625,145),new PointData(712,235),90).isFound());
    }
}
