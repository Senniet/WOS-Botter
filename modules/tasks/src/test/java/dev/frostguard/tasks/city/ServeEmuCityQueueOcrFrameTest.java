package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.nav.LeftMenuTextSettings;
import dev.frostguard.vision.ocr.OcrEngine;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Status labels recorded from the active 720x1280 serve-emu City sidebar. */
class ServeEmuCityQueueOcrFrameTest {
    @Test void recognizesAllCurrentIdleQueueLabelsInsteadOfTheirRowTitles() throws Exception {
        RawImageData frame = screenshot();
        for (AreaData area : new AreaData[] {
                TrainingRoutine.currentSidebarQueueArea(TrainingRoutine.TroopTypeShape.INFANTRY),
                TrainingRoutine.currentSidebarQueueArea(TrainingRoutine.TroopTypeShape.LANCER),
                TrainingRoutine.currentSidebarQueueArea(TrainingRoutine.TroopTypeShape.MARKSMAN),
                ResearchRoutine.researchQueueStatusArea(true)}) {
            String text = OcrEngine.recognizeText(frame, area.topLeft(), area.bottomRight(),
                    LeftMenuTextSettings.WHITE_SETTINGS).trim();
            assertTrue(text.toLowerCase().contains("idle"), area + " OCR=" + text);
        }
    }

    private RawImageData screenshot() throws Exception {
        BufferedImage image = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream(
                "/serveemu/city-queues-masked.jpg")));
        byte[] pixels = new byte[image.getWidth() * image.getHeight() * 4];
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int i = (y * image.getWidth() + x) * 4, rgb = image.getRGB(x, y);
            pixels[i] = (byte) (rgb >> 16); pixels[i+1] = (byte) (rgb >> 8);
            pixels[i+2] = (byte) rgb; pixels[i+3] = (byte) 255;
        }
        return RawImageData.capture(pixels,image.getWidth(),image.getHeight(),32);
    }
}
