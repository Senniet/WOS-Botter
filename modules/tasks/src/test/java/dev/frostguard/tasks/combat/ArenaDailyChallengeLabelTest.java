package dev.frostguard.tasks.combat;

import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.ocr.OcrEngine;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Saved 720x1280 redacted Arena daily-attempt HUD, not a synthetic glyph. */
class ArenaDailyChallengeLabelTest {
    @Test void onlyAcceptsLabelledCountNotDigitCropOrArbitraryText() {
        assertEquals(1, ArenaRoutine.parseDailyChallengeLabel("Daily challenges: 1"));
        assertEquals(5, ArenaRoutine.parseDailyChallengeLabel("challenges: 5"));
        assertEquals(0, ArenaRoutine.parseDailyChallengeLabel("Daily challenges: 0"));
        for (String invalid : new String[]{"75", "1", "Daily challenges: 75",
                "Your total power 5", "challenge 9", "", "Daily challenges: x"})
            assertEquals(-1, ArenaRoutine.parseDailyChallengeLabel(invalid), invalid);
    }
    @Test void readsActualChallengeCounterWithoutTheFalseSeven() throws Exception {
        BufferedImage b = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream(
                "/arena/challenge-list-daily-chest-4of5-20261109.png")));
        byte[] rgba = new byte[b.getWidth() * b.getHeight() * 4];
        int i=0;
        for (int y=0;y<b.getHeight();y++)for (int x=0;x<b.getWidth();x++) {
            int rgb=b.getRGB(x,y);
            rgba[i++]=(byte)(rgb>>16);rgba[i++]=(byte)(rgb>>8);
            rgba[i++]=(byte)rgb;rgba[i++]=(byte)255;
        }
        var frame=RawImageData.capture(rgba,b.getWidth(),b.getHeight(),32);
        String text=OcrEngine.recognizeText(frame,
                new PointData(170,933),new PointData(455,1005),
                OcrSettingsData.assembler()
                        .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build());
        assertEquals(1,ArenaRoutine.parseDailyChallengeLabel(text),
                "The actual 2026 layout must produce the labelled count: " + text);
    }
}
