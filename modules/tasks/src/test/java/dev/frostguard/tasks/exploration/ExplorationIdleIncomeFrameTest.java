package dev.frostguard.tasks.exploration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import javax.imageio.ImageIO;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationIdleIncomeFrameTest {
    @BeforeAll static void initOpenCv() throws Exception {
        try { OpenCvPatternLocator.loadNativeLibrary(); }
        catch (UnsatisfiedLinkError ignored) { }
    }

    @Test void titleAndGreenClaimAreBothPresentInSanitizedRealFrame() throws Exception {
        byte[] png = Objects.requireNonNull(getClass().getResourceAsStream(
                "/exploration/idle-income-claim-masked.png")).readAllBytes();
        assertTrue(OpenCvPatternLocator.locatePattern(png, "/templates/exploration/idleIncomeTitle.png",
                new PointData(140,220), new PointData(550,370), 88).isFound());
        var frame = ImageIO.read(new java.io.ByteArrayInputStream(png));
        assertTrue(GreenLabelActionDetector.detect(frame,
                new GreenLabelActionDetector.Region(213,875,507,972)).isFound());
    }

    @Test void rewardsFrameDoesNotMistakeItsBannerForIdleIncome() throws Exception {
        byte[] png = Objects.requireNonNull(getClass().getResourceAsStream(
                "/arena/arena-rewards-panel-20261022.png")).readAllBytes();
        assertFalse(OpenCvPatternLocator.locatePattern(png, "/templates/exploration/idleIncomeTitle.png",
                new PointData(140,220), new PointData(550,370), 88).isFound());
    }
}
