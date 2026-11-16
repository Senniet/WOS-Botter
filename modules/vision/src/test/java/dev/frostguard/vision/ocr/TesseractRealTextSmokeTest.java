package dev.frostguard.vision.ocr;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/** A real Tesseract LSTM test rather than a mocked string extractor. */
class TesseractRealTextSmokeTest {
    @Test void bundledTessdataCanReadClearlyDrawnNumbers() throws OcrException {
        var image=new BufferedImage(460,92,BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();
        graphics.setColor(Color.WHITE);graphics.fillRect(0,0,460,92);
        graphics.setFont(new Font("DejaVu Sans",Font.BOLD,49));
        graphics.setColor(Color.BLACK);graphics.drawString("TRAIN 160",24,65);
        graphics.dispose();
        String text=new TesseractOcrProvider().recognizeText(image,"eng");
        assertNotNull(text);
        assertTrue(text.replaceAll("\\s+", "").toUpperCase().contains("TRAIN160"),
                "Expected TRAIN 160; real Tesseract yielded: "+text);
    }
}
