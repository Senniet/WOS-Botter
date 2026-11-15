package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.diagnostics.TrainingUnlockOverlay;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

/** Captured 15 Nov 2026 from actual stuck troop-training diagnostic. */
class TrainingUnlockOverlayFrameTest {
    @BeforeAll static void loadNative() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void acceptsTwoAnimatedFramesWithExplicitContinueInstruction() throws Exception {
        assertTrue(TrainingUnlockOverlay.visible(screenshot("/serveemu/training-unlock-163919-masked.png")));
        assertTrue(TrainingUnlockOverlay.visible(screenshot("/serveemu/training-unlock-164146-masked.png")));
    }

    @Test void ignoresNormalCitySidebarAndUnlabelledBlueBackground() throws Exception {
        assertFalse(TrainingUnlockOverlay.visible(screenshot("/serveemu/city-queues-masked.jpg")));
        BufferedImage blank = new BufferedImage(720, 1280, BufferedImage.TYPE_INT_RGB);
        var paint = blank.createGraphics();
        paint.setColor(new java.awt.Color(92, 147, 185));
        paint.fillRect(0,0,720,1280);
        paint.dispose();
        assertFalse(TrainingUnlockOverlay.visible(toRaw(blank)));
    }

    private RawImageData screenshot(String resource) throws Exception {
        try (var file = Objects.requireNonNull(getClass().getResourceAsStream(resource))) {
            return toRaw(Objects.requireNonNull(ImageIO.read(file)));
        }
    }

    private static RawImageData toRaw(BufferedImage image) {
        byte[] rgba = new byte[image.getWidth() * image.getHeight() * 4];
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int pixel = image.getRGB(x,y), i = (y * image.getWidth() + x) * 4;
            rgba[i] = (byte)(pixel >>> 16);
            rgba[i + 1] = (byte)(pixel >>> 8);
            rgba[i + 2] = (byte)pixel;
            rgba[i + 3] = (byte)255;
        }
        return RawImageData.capture(rgba,image.getWidth(),image.getHeight(),32);
    }
}
