package dev.frostguard.tasks.heroes;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroRecruitmentLiveEntry20261113Test {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void modernRecruitmentTitleIsVisibleOnReportedHeroScreen() throws Exception {
        byte[] png = Objects.requireNonNull(getClass().getResourceAsStream(
                "/heroes/live-20261113/hero-unrecognized-title-redacted.png")).readAllBytes();
        assertTrue(OpenCvPatternLocator.locatePattern(png,
                TemplatesEnum.SERVE_EMU_HERO_MODERN_RECRUIT_TITLE.getTemplate(),
                new PointData(390, 70), new PointData(715, 330), 90).isFound());
    }
}
