package dev.frostguard.tasks.heroes;

import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroPointsChestDetectorTest {
    @Test void detectsReadyBadgeFromLiveEmulatorButNotAfterClaim() throws Exception {
        assertTrue(HeroPointsChestDetector.isReady(scene("points-chest-ready-red-badge.png")));
        assertFalse(HeroPointsChestDetector.isReady(scene("points-chest-collected.png")));
    }

    @Test void rejectsEmptyOrWrongSizedScreens() {
        assertFalse(HeroPointsChestDetector.isReady(null));
        assertFalse(HeroPointsChestDetector.isReady(new BufferedImage(65, 75, BufferedImage.TYPE_INT_RGB)));
        assertFalse(HeroPointsChestDetector.isReady(new BufferedImage(720, 1280, BufferedImage.TYPE_INT_RGB)));
    }

    private static BufferedImage scene(String patch) throws Exception {
        BufferedImage canvas = new BufferedImage(720, 1280, BufferedImage.TYPE_INT_RGB);
        BufferedImage sample = ImageIO.read(Objects.requireNonNull(
                HeroPointsChestDetectorTest.class.getResource("/heroes/" + patch)));
        var graphics = canvas.createGraphics();
        try { graphics.drawImage(sample, HeroPointsChestDetector.LEFT, HeroPointsChestDetector.TOP, null); }
        finally { graphics.dispose(); }
        return canvas;
    }
}
