package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class IntelDeployModernFrameTest {
    @BeforeAll static void openCv() throws Exception {
        try { OpenCvPatternLocator.loadNativeLibrary(); }
        catch (UnsatisfiedLinkError ignored) { }
    }

    private byte[] screenshot(String path) throws Exception {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream(path))) {
            return stream.readAllBytes();
        }
    }

    @Test void modernDeployLabelMatchesOnlyWithinFormationBottomRight() throws Exception {
        var hit = OpenCvPatternLocator.locatePattern(
                screenshot("/intel/deploy-modern-masked.png"),
                "/templates/intel/deployModern.png",
                new PointData(425,1135), new PointData(710,1279), 88);
        assertTrue(hit.isFound(), "Known Intel Deploy artwork should be accepted");
        assertEquals(550,hit.getPoint().getX());
        assertEquals(1203,hit.getPoint().getY());
    }

    @Test void explorationClaimScreenIsNotMistakenForIntelDeploy() throws Exception {
        var miss = OpenCvPatternLocator.locatePattern(
                screenshot("/exploration/idle-income-claim-masked.png"),
                "/templates/intel/deployModern.png",
                new PointData(425,1135), new PointData(710,1279), 88);
        assertFalse(miss.isFound());
    }
}
