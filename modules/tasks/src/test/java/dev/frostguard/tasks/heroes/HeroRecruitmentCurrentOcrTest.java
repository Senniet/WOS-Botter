package dev.frostguard.tasks.heroes;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.vision.ocr.OcrEngine;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Nov19 Hero Recruitment HUD, profile/private pixels blacked out. */
class HeroRecruitmentCurrentOcrTest {
    private static RawImageData frame()throws Exception {
        return frame("recruitment-countdown-redacted.png");
    }
    private static RawImageData frame(String filename)throws Exception {
        BufferedImage b=ImageIO.read(Objects.requireNonNull(
                HeroRecruitmentCurrentOcrTest.class.getResourceAsStream(
                  "/heroes/live-20261119/" + filename)));
        byte[] rgba=new byte[b.getWidth()*b.getHeight()*4];int i=0;
        for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++){
            int rgb=b.getRGB(x,y);
            rgba[i++]=(byte)(rgb>>16);rgba[i++]=(byte)(rgb>>8);
            rgba[i++]=(byte)rgb;rgba[i++]=(byte)255;
        }
        return RawImageData.capture(rgba,b.getWidth(),b.getHeight(),32);
    }
    private static String read(RawImageData screen,int x1,int y1,int x2,int y2,
            OcrSettingsData.TextLayout layout,String glyphs)throws Exception {
        return OcrEngine.recognizeText(screen,new PointData(x1,y1),new PointData(x2,y2),
                OcrSettingsData.configurator().textLayout(layout).allowedGlyphs(glyphs).build());
    }
    @Test void existingKeysAreVisibleInCorrectHudRowsNotTheOldLocations()throws Exception{
        var f=frame();
        assertEquals(2,HeroRecruitmentKeyPolicy.countFromOcr(read(f,238,24,279,64,
                OcrSettingsData.TextLayout.SINGLE_WORD,"0123456789")));
        assertEquals(4,HeroRecruitmentKeyPolicy.countFromOcr(read(f,422,24,464,64,
                OcrSettingsData.TextLayout.SINGLE_WORD,"0123456789")));
        assertEquals(-1,HeroRecruitmentKeyPolicy.countFromOcr(read(f,224,89,267,127,
                OcrSettingsData.TextLayout.SINGLE_LINE,"0123456789")),
                "Old ROI below Advanced key HUD must never count as a saved key");
    }
    @Test void liveZeroKeyHudIsRecognizedWithoutInventingPositiveInventory() throws Exception {
        var empty = frame("recruitment-zero-keys-redacted.png");
        var withKeys = frame("recruitment-countdown-redacted.png");
        for(var box:new int[][]{{238,24,279,64},{422,24,464,64}}) {
            var topLeft = new PointData(box[0],box[1]);
            var bottomRight = new PointData(box[2],box[3]);
            assertTrue(HeroRecruitmentZeroHud.isZero(empty,topLeft,bottomRight),
                    "Actual zero glyph must bypass blank OCR and skip unused keys");
            assertFalse(HeroRecruitmentZeroHud.isZero(withKeys,topLeft,bottomRight),
                    "Positive 2/4 keys must NEVER look like zero");
        }
        assertFalse(HeroRecruitmentZeroHud.isZero(null,
                new PointData(238,24),new PointData(279,64)));
    }

    @Test void realFreeTimersIncludeDayLongEpicCountdown()throws Exception{
        var f=frame();
        var advanced=read(f,40,770,350,810,OcrSettingsData.TextLayout.SINGLE_LINE,"0123456789:dD");
        var epic=read(f,53,1130,330,1160,OcrSettingsData.TextLayout.SINGLE_LINE,"0123456789:dD");
        assertEquals(Duration.ofHours(11).plusMinutes(47).plusSeconds(17),
                HeroRecruitmentRoutine.parseModernCooldown(advanced));
        assertEquals(Duration.ofDays(1).plusHours(23).plusMinutes(26).plusSeconds(18),
                HeroRecruitmentRoutine.parseModernCooldown(epic));
    }
}
