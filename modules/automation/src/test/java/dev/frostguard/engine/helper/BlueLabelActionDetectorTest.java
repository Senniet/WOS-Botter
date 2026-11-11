package dev.frostguard.engine.helper;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlueLabelActionDetectorTest {
    private static final BlueLabelActionDetector.Region ENACT =
            new BlueLabelActionDetector.Region(212, 881, 508, 965, 322, 893, 406, 919);

    private static BufferedImage panel(boolean text) {
        var image = new BufferedImage(720, 1280, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(175, 160, 135));
        g.fillRect(0, 0, 720, 1280);
        g.setColor(new Color(79, 165, 252));
        g.fillRect(212, 881, 296, 84);
        if (text) {
            g.setColor(Color.WHITE);
            g.fillRect(330, 897, 50, 14);
        }
        g.dispose();
        return image;
    }

    @Test void recognisesLabelledBlueButton() {
        assertTrue(BlueLabelActionDetector.detect(panel(true), ENACT).isFound());
    }

    @Test void refusesBlueRectangleWithoutLabel() {
        assertFalse(BlueLabelActionDetector.detect(panel(false), ENACT).isFound());
    }

    @Test void refusesWrongScreenGeometry() {
        assertFalse(BlueLabelActionDetector.detect(
                new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB), ENACT).isFound());
    }

    @Test void refusesUnboundedRegions() {
        assertThrows(IllegalArgumentException.class,
                () -> new BlueLabelActionDetector.Region(-1, 0, 100, 100, 5, 5, 50, 20));
    }
}
