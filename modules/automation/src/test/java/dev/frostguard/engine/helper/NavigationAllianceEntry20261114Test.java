package dev.frostguard.engine.helper;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavigationAllianceEntry20261114Test {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void capturedIntelPageHasBackArrowButNoCityHomeAnchor() throws Exception {
        byte[] frame=frame("intel-redacted.png");
        assertTrue(OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_HEADER_BACK_INTEL_2026.getTemplate(),
                new PointData(0,0),new PointData(110,105),90).isFound());
        assertFalse(city(frame));
    }

    @Test void capturedCityPageIsEligibleForAllianceNavigation() throws Exception {
        assertTrue(city(frame("city-redacted.png")));
    }

    @Test void homeMustBeVerifiedBeforeTouchingTheAllianceButton() throws Exception {
        Path source=Path.of("src/main/java/dev/frostguard/engine/helper/NavigationHelper.java");
        String code=Files.readString(source);
        int start=code.indexOf("public boolean navigateToAllianceMenu(AllianceMenu menu)");
        assertTrue(start>=0);
        int guard=code.indexOf("ensureCorrectScreenLocation(LaunchPoint.HOME);",start);
        int tap=code.indexOf("taps.tapInside(ButtonConstants.BOTTOM_MENU_ALLIANCE_BUTTON)",start);
        assertTrue(guard>start && tap>guard,"Alliance must never tap while on the Intel page");
    }

    private boolean city(byte[] frame) throws Exception {
        return OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_CITY_ANCHOR.getTemplate(),
                new PointData(590,1145),new PointData(715,1275),90).isFound();
    }

    private byte[] frame(String name) throws Exception {
        return Objects.requireNonNull(getClass().getResourceAsStream(
                "/navigation/alliance-entry-20261114/"+name)).readAllBytes();
    }
}
