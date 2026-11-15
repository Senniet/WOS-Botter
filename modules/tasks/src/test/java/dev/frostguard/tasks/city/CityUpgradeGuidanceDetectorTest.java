package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class CityUpgradeGuidanceDetectorTest {
    private static final String LIVE_FRAME = "/city/embassy-selected-upgrade-hand-20261115-redacted.png";

    @BeforeAll
    static void initializeMatcher() throws IOException {
        try { OpenCvPatternLocator.loadNativeLibrary(); } catch (UnsatisfiedLinkError ignored) {}
    }

    @Test
    void identifiesHandObscuredUpgradeOnActualSafeLiveFrame() throws Exception {
        byte[] live = resource(LIVE_FRAME);
        var guided = inspect(live);
        assertTrue(guided.actionable(), "Both hand and Details must be independently confirmed");
        assertTrue(guided.hand().getMatchScore() >= 97);
        assertTrue(guided.details().getMatchScore() >= 93);
        assertEquals(360, guided.upgrade().getPoint().getX());
        assertEquals(875, guided.upgrade().getPoint().getY());
        assertNotNull(guided.upgrade().getMatchedArea());
        // Old generic arrow template cannot be relied upon while the hand covers it.
        assertFalse(OpenCvPatternLocator.locatePattern(live,
                TemplatesEnum.BUILDING_BUTTON_UPGRADE, new PointData(0,0),
                new PointData(720,1280), 88).isFound());
    }

    @Test
    void refusesGuidanceWhenHandIsMissing() throws Exception {
        assertFalse(inspect(masked(335, 715, 205, 205)).actionable());
    }

    @Test
    void refusesGuidanceWhenSelectedBuildingDetailsAreMissing() throws Exception {
        assertFalse(inspect(masked(160, 785, 155, 160)).actionable());
    }

    @Test
    void refusesAHandThatPointsOutsideTheAdjacentUpgradeSlot() {
        var result = CityUpgradeGuidanceDetector.inspect((template, area, threshold) ->
                template == TemplatesEnum.BUILDING_BUTTON_DETAILS
                        ? ImageSearchResultData.hit(235, 854, 99)
                        : ImageSearchResultData.hit(480, 784, 99));
        assertFalse(result.actionable());
    }

    private static CityUpgradeGuidanceDetector.Evidence inspect(byte[] frame) {
        return CityUpgradeGuidanceDetector.inspect((template, area, threshold) ->
                OpenCvPatternLocator.locatePattern(frame, template,
                        area.topLeft(), area.bottomRight(), threshold));
    }

    private static byte[] masked(int x, int y, int width, int height) throws IOException {
        var image = ImageIO.read(new ByteArrayInputStream(resource(LIVE_FRAME)));
        var graphics = image.createGraphics();
        graphics.setColor(Color.BLACK);
        graphics.fillRect(x, y, width, height);
        graphics.dispose();
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static byte[] resource(String path) throws IOException {
        try (var in = Objects.requireNonNull(CityUpgradeGuidanceDetectorTest.class.getResourceAsStream(path))) {
            return in.readAllBytes();
        }
    }
}
