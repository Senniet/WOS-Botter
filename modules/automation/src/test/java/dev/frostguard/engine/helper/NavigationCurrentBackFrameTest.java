package dev.frostguard.engine.helper;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavigationCurrentBackFrameTest {
    @BeforeAll static void loadCv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void intelHeaderFromRecordedFailureMatchesWithoutBlindAndroidBack() throws Exception {
        assertTrue(back("intel-header-only.png"));
    }

    @Test void eventHeaderFromTundraTruckFailureAlsoHasVisibleBackArrow() throws Exception {
        assertTrue(back("tundra-header-only.png", TemplatesEnum.SERVE_EMU_HEADER_BACK_EVENTS_2026));
    }

    @Test void heroRecruitmentCanUseItsSkyBackgroundArrowToReturnHome() throws Exception {
        assertTrue(back("hero-header-only.png", TemplatesEnum.SERVE_EMU_HEADER_BACK_HERO_2026));
    }

    @Test void arenaHeaderHasItsOwnDarkBlueBackArrow() throws Exception {
        assertTrue(back("arena-header-only.png", TemplatesEnum.SERVE_EMU_HEADER_BACK_EVENTS_2026));
    }

    @Test void rewardsOverlayCannotBeMistakenForAnyHeaderArrow() throws Exception {
        for (TemplatesEnum candidate : new TemplatesEnum[] {
                TemplatesEnum.SERVE_EMU_HEADER_BACK_INTEL_2026,
                TemplatesEnum.SERVE_EMU_HEADER_BACK_EVENTS_2026,
                TemplatesEnum.SERVE_EMU_HEADER_BACK_HERO_2026}) {
            assertFalse(back("exploration-idle-header-only.png", candidate),
                    "Blurred background must not authorize Back for " + candidate);
        }
    }

    private boolean back(String file) throws Exception {
        return back(file, TemplatesEnum.SERVE_EMU_HEADER_BACK_INTEL_2026);
    }

    private boolean back(String file, TemplatesEnum tpl) throws Exception {
        byte[] png = Objects.requireNonNull(getClass().getResourceAsStream(
                "/navigation/recovery-20261113/" + file)).readAllBytes();
        return OpenCvPatternLocator.locatePattern(png,
                tpl.getTemplate(),
                new PointData(0,0),new PointData(110,105),90).isFound();
    }
}
