package dev.frostguard.engine.helper;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreenLabelActionDetectorTest {
    private static final GreenLabelActionDetector.Region REGION =
            new GreenLabelActionDetector.Region(230, 985, 495, 1070);

    private static BufferedImage screen(boolean green, boolean label) {
        BufferedImage frame = new BufferedImage(720,1280, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = frame.createGraphics();
        g.setColor(Color.DARK_GRAY);g.fillRect(0,0,720,1280);
        g.setColor(green ? new Color(40,195,55) : new Color(75,140,225));
        g.fillRect(239,995,247,66);
        if(label){g.setColor(Color.WHITE);g.fillRect(310,1018,110,15);}
        g.dispose();return frame;
    }

    @Test void acceptsLabelledGreenButton() {
        assertTrue(GreenLabelActionDetector.detect(screen(true,true),REGION).isFound());
    }
    @Test void refusesPlainGreenAndBlueButtons() {
        assertFalse(GreenLabelActionDetector.detect(screen(true,false),REGION).isFound());
        assertFalse(GreenLabelActionDetector.detect(screen(false,true),REGION).isFound());
    }
    @Test void refusesDifferentResolution() {
        assertFalse(GreenLabelActionDetector.detect(new BufferedImage(1080,1920,1),REGION).isFound());
    }
}
