package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 18-Nov-2026 verified Command Center button, after its city UI shifted. */
final class CommandCenterButtonProbeTest {
    private static final String FRAME = "/city/command-center-selected-upgrade-hand-20261118-redacted.png";

    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    private static byte[] read() throws Exception {
        try (var in=CommandCenterButtonProbeTest.class.getResourceAsStream(FRAME)) {
            assertNotNull(in,FRAME);
            return in.readAllBytes();
        }
    }

    private static CityUpgradeGuidanceDetector.Evidence inspect(byte[] frame) {
        return CityUpgradeGuidanceDetector.inspect((template,area,threshold)->
                OpenCvPatternLocator.locatePattern(frame,template.getTemplate(),
                        area.topLeft(),area.bottomRight(),threshold));
    }

    @Test void newCommandCenterLayoutHasTwoPreciseAnchorsAndOneSafeUpgradeTarget() throws Exception {
        var evidence=inspect(read());
        assertTrue(evidence.actionable(),"actual selected building should expose an upgrade target");
        assertTrue(evidence.details().getMatchScore()>93);
        assertTrue(evidence.hand().getMatchScore()>97);
        assertEquals(300,evidence.details().getPoint().getX());
        assertEquals(880,evidence.details().getPoint().getY());
        assertEquals(491,evidence.hand().getPoint().getX());
        assertEquals(791,evidence.hand().getPoint().getY());
        assertEquals(416,evidence.upgrade().getPoint().getX());
        assertEquals(880,evidence.upgrade().getPoint().getY());
        // Original generic Upgrade artwork is completely obscured by the
        // tutorial hand, explaining CONTROL_NOT_RECOGNIZED x12 in production.
        var direct=OpenCvPatternLocator.locatePattern(read(),
                TemplatesEnum.BUILDING_BUTTON_UPGRADE.getTemplate(),
                new dev.frostguard.api.domain.PointData(350,820),
                new dev.frostguard.api.domain.PointData(490,950),88);
        assertFalse(direct.isFound());
    }

    @Test void noDetailsOrNoTutorialHandMeansNoClick() throws Exception {
        assertFalse(inspect(mask(248,831,110,111)).actionable(),"Details is essential");
        assertFalse(inspect(mask(450,726,115,128)).actionable(),"hand is essential");
    }

    private static byte[] mask(int x,int y,int width,int height) throws Exception {
        var image=ImageIO.read(new ByteArrayInputStream(read()));
        var g=image.createGraphics();
        g.setColor(Color.BLACK);g.fillRect(x,y,width,height);g.dispose();
        var out=new ByteArrayOutputStream();
        ImageIO.write(image,"png",out);
        return out.toByteArray();
    }
}
