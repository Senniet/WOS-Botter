package dev.frostguard.tasks.heroes;

import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.ocr.OcrEngine;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.time.Duration;
import java.time.LocalDateTime;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroRecruitmentCooldown20261115Test {
    @Test void schedulesForRealCooldownInsteadOfBlindFiveMinuteRetry() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 15, 0, 5);
        Duration advanced = Duration.ofMinutes(54).plusSeconds(27);
        Duration epic = Duration.ofHours(10).plusMinutes(4).plusSeconds(21);
        assertEquals(now.plus(advanced).plusSeconds(12),
                HeroRecruitmentRoutine.nextModernCheck(now, advanced, epic,
                        true, true, false));
        assertEquals(now.plusMinutes(10),
                HeroRecruitmentRoutine.nextModernCheck(now, advanced, epic,
                        true, true, true));
        assertEquals(now.plusMinutes(5),
                HeroRecruitmentRoutine.nextModernCheck(now, null, epic,
                        true, true, false));
        assertEquals(Duration.ofDays(1).plusHours(23).plusMinutes(26).plusSeconds(18),
                HeroRecruitmentRoutine.parseModernCooldown(":1d23:26:18"));
        assertEquals(Duration.ofDays(1).plusHours(23).plusMinutes(37).plusSeconds(47),
                HeroRecruitmentRoutine.parseModernCooldown(":123:37:47"));
        assertEquals(Duration.ofDays(1).plusHours(23).plusMinutes(26).plusSeconds(18),
                HeroRecruitmentRoutine.parseModernCooldown("Next free 1d 23:26:18"));
        assertNull(HeroRecruitmentRoutine.parseModernCooldown(":199:99:99"));
        assertNull(HeroRecruitmentRoutine.parseModernCooldown("Next Free: unavailable"));
        assertNull(HeroRecruitmentRoutine.parseModernCooldown("99:99:99"));
    }

    @Test void readsActualCoolDownTextWithSameJavaOcrBackend() throws Exception {
        BufferedImage im=ImageIO.read(Objects.requireNonNull(getClass()
                .getResource("/initialize/hero-recruitment-cooldown-20261115.png")));
        byte[] rgba=new byte[im.getWidth()*im.getHeight()*4];
        for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){
            int rgb=im.getRGB(x,y),i=(y*im.getWidth()+x)*4;
            rgba[i]=(byte)(rgb>>16);rgba[i+1]=(byte)(rgb>>8);
            rgba[i+2]=(byte)rgb;rgba[i+3]=(byte)255;
        }
        RawImageData screen=RawImageData.capture(rgba,im.getWidth(),im.getHeight(),32);
        var config=OcrSettingsData.configurator()
                .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
                .allowedGlyphs("0123456789:").build();
        String advanced=OcrEngine.recognizeText(screen,
                new PointData(40,770),new PointData(350,810),config);
        String epic=OcrEngine.recognizeText(screen,
                new PointData(53,1130),new PointData(330,1160),config);
        System.out.println("Advanced cooldown OCR='"+advanced+"' Epic cooldown OCR='"+epic+"'");
        assertEquals(Duration.ofMinutes(54).plusSeconds(27),
                HeroRecruitmentRoutine.parseModernCooldown(advanced));
        assertEquals(Duration.ofHours(10).plusMinutes(4).plusSeconds(21),
                HeroRecruitmentRoutine.parseModernCooldown(epic));
    }
    @org.junit.jupiter.api.Test
    void skipsMissingFreeTemplateOnlyWithIndependentlyConfirmedLongCooldown() {
        org.junit.jupiter.api.Assertions.assertFalse(
                HeroRecruitmentRoutine.shouldProbeFreeButtonAfterPassiveMiss(
                        java.time.Duration.ofHours(12)));
        org.junit.jupiter.api.Assertions.assertFalse(
                HeroRecruitmentRoutine.shouldProbeFreeButtonAfterPassiveMiss(
                        java.time.Duration.ofMinutes(2)));
        org.junit.jupiter.api.Assertions.assertTrue(
                HeroRecruitmentRoutine.shouldProbeFreeButtonAfterPassiveMiss(null));
        org.junit.jupiter.api.Assertions.assertTrue(
                HeroRecruitmentRoutine.shouldProbeFreeButtonAfterPassiveMiss(
                        java.time.Duration.ofSeconds(15)));
        org.junit.jupiter.api.Assertions.assertTrue(
                HeroRecruitmentRoutine.shouldProbeFreeButtonAfterPassiveMiss(
                        java.time.Duration.ZERO));
    }
}
