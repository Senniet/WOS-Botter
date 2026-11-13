package dev.frostguard.tasks.events;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Privacy-safe title-only reproductions of live 720x1280 serve-emu diagnostics. */
class ServeEmuTitleRecognitionTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void dailyMissionTitleMatchesOnlyDailyScreen() throws Exception {
        assertTrue(match("daily", TemplatesEnum.SERVE_EMU_DAILY_TITLE, 170, 105, 545, 210));
        assertFalse(match("exploration", TemplatesEnum.SERVE_EMU_DAILY_TITLE, 170, 105, 545, 210));
    }

    @Test void explorationTitleMatchesOnlyExplorationScreen() throws Exception {
        assertTrue(match("exploration", TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE, 50, 65, 330, 160));
        assertFalse(match("daily", TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE, 50, 65, 330, 160));
    }

    @Test void incomeTitleMatchesOnlyIdleIncomeOverlay() throws Exception {
        assertTrue(match("income", TemplatesEnum.SERVE_EMU_IDLE_INCOME_TITLE, 235, 275, 555, 385));
        assertFalse(match("daily", TemplatesEnum.SERVE_EMU_IDLE_INCOME_TITLE, 235, 275, 555, 385));
    }

    private static boolean match(String scene, TemplatesEnum template, int left, int top, int right, int bottom)
            throws Exception {
        BufferedImage image = ImageIO.read(Objects.requireNonNull(
                ServeEmuTitleRecognitionTest.class.getResource("/serveemu/" + scene + "-title-only.png")));
        byte[] pixels = new byte[image.getWidth() * image.getHeight() * 4];
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int rgb = image.getRGB(x, y), i = (y * image.getWidth() + x) * 4;
            pixels[i] = (byte) (rgb >> 16);
            pixels[i + 1] = (byte) (rgb >> 8);
            pixels[i + 2] = (byte) rgb;
            pixels[i + 3] = (byte) 255;
        }
        return OpenCvPatternLocator.locatePattern(RawImageData.capture(pixels, image.getWidth(),
                image.getHeight(), 32), template.getTemplate(), new PointData(left, top),
                new PointData(right, bottom), 90).isFound();
    }
}
