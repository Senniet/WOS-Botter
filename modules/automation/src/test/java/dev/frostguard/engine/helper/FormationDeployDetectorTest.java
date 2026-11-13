package dev.frostguard.engine.helper;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FormationDeployDetectorTest {
    private static BufferedImage formation(boolean label) {
        BufferedImage image = new BufferedImage(720, 1280, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(56, 98, 147));
        g.fillRect(0, 0, 720, 1280);
        g.setColor(new Color(79, 165, 252));
        g.fillRect(420, 1178, 270, 80);
        if (label) {
            g.setColor(Color.WHITE);
            g.fillRect(520, 1190, 35, 9);
            g.fillRect(560, 1190, 30, 9);
        }
        g.dispose();
        return image;
    }

    @Test void recognizesConfirmedBlueActionWithLightLabel() {
        assertTrue(FormationDeployDetector.detect(formation(true)).isFound());
    }

    @Test void rejectsUnlabelledBluePanel() {
        assertFalse(FormationDeployDetector.detect(formation(false)).isFound());
    }

    @Test void rejectsOtherResolution() {
        assertFalse(FormationDeployDetector.detect(
                new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB)).isFound());
    }
}
