package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.engine.emulator.EmulatorType;
import dev.frostguard.engine.nav.SidebarFrameClassifier;
import dev.frostguard.vision.convert.GameTimeUtils;
import dev.frostguard.vision.ocr.OcrEngine;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchQueueLiveProbeTest {
    private static RawImageData liveQueue() throws Exception {
        var crop=ImageIO.read(Objects.requireNonNull(ResearchQueueLiveProbeTest.class
                .getResourceAsStream("/research/live-20261117/research-queue-busy.png")));
        var full = new BufferedImage(720,1280,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=full.createGraphics();
        try { g.drawImage(crop,0,685,null); } finally {g.dispose();}
        byte[] pixels=new byte[720*1280*4];
        int i=0;
        for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
            int c=full.getRGB(x,y);
            pixels[i++]=(byte)(c>>16);
            pixels[i++]=(byte)(c>>8);
            pixels[i++]=(byte)c;
            pixels[i++]=(byte)255;
        }
        return RawImageData.capture(pixels,720,1280,32);
    }
    @Test void oldRegionMissesRealResearchTimerButFixedRegionReadsIt() throws Exception {
        RawImageData frame=liveQueue();
        var settings=OcrSettingsData.assembler()
                .charWhitelist("0123456789:")
                .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build();
        var shifted=ResearchRoutine.researchQueueStatusArea(true);
        var classic=ResearchRoutine.researchQueueStatusArea(false);
        String lost=OcrEngine.recognizeText(frame,shifted.topLeft(),shifted.bottomRight(),settings);
        String recognized=OcrEngine.recognizeText(frame,classic.topLeft(),classic.bottomRight(),settings);
        assertTrue(lost.isBlank(),"Shifted status ROI must not be mistaken for classic: "+lost);
        assertEquals("01:09:02",recognized);
        var former=AreaData.of(164,811,303,841);
        String formerRead=OcrEngine.recognizeText(frame,former.topLeft(),former.bottomRight(),settings);
        assertNotEquals(recognized,formerRead,"Former crop should reproduce the missing colon");
        assertEquals(Duration.ofHours(1).plusMinutes(9).plusSeconds(2),
                GameTimeUtils.parseDuration(recognized));
    }
    @Test void currentSidebarRegionAndLegacyRegionRemainDistinct() {
        assertEquals(AreaData.of(165,842,305,892), ResearchRoutine.researchQueueStatusArea(true));
        assertEquals(AreaData.of(165,818,326,853), ResearchRoutine.researchQueueStatusArea(false));
    }
    @Test void shiftedSidebarUsesItsOwnLowerIdleTextRegion() throws Exception {
        var modern = ImageIO.read(Objects.requireNonNull(ResearchQueueLiveProbeTest.class
                .getResourceAsStream("/serveemu/city-queues-masked.jpg")));
        assertTrue(SidebarFrameClassifier.isCurrentLayout(modern));
        var roi=ResearchRoutine.researchQueueStatusArea(true);
        var cropped=modern.getSubimage(roi.topLeft().getX(),roi.topLeft().getY(),
                roi.bottomRight().getX()-roi.topLeft().getX(),
                roi.bottomRight().getY()-roi.topLeft().getY());
        assertTrue(cropped.getHeight()>20);
    }

    @Test void serveEmuRefusesAutomaticResourceItemReplenishment() {
        assertFalse(ResearchRoutine.mayUseResourceReplenishment(EmulatorType.SERVE_EMU));
        assertTrue(ResearchRoutine.mayUseResourceReplenishment(EmulatorType.BLUESTACKS));
    }
}
