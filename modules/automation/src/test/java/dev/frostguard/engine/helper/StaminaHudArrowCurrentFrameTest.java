package dev.frostguard.engine.helper;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaminaHudArrowCurrentFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void cityAndWorldShowTheCurrentHudArrow() throws Exception {
        assertTrue(hudArrow("city-redacted.png"));
        assertTrue(hudArrow("world-redacted.png"));
    }

    @Test void unrelatedHeroCutsceneMustNotTriggerHudTap() throws Exception {
        assertFalse(hudArrow("hero-chest-redacted.png"));
    }

    private boolean hudArrow(String scene) throws Exception {
        byte[] png=Objects.requireNonNull(getClass().getResourceAsStream(
                "/stamina/live-hud-20261114/"+scene)).readAllBytes();
        return OpenCvPatternLocator.locatePattern(png,
                TemplatesEnum.SERVE_EMU_STAMINA_HUD_ARROW_2026.getTemplate(),
                new PointData(3,110),new PointData(90,174),90).isFound();
    }
}
