package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.ocr.OcrEngine;
import dev.frostguard.engine.nav.CommonOCRSettings;
import dev.frostguard.engine.nav.LeftMenuTextSettings;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real Android16 720x1280 selected Research Center, private HUD redacted. */
class ResearchCenterBusyReadTest {
    @Test void actualOccupiedResearchCenterIsRecognizedAsNonUpgradeable()throws Exception {
        BufferedImage img=ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream(
                "/city/researchcenter-active-20261119.png")));
        byte[] rgba=new byte[720*1280*4];int pos=0;
        for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
            int rgb=img.getRGB(x,y);
            rgba[pos++]=(byte)(rgb>>16);rgba[pos++]=(byte)(rgb>>8);
            rgba[pos++]=(byte)rgb;rgba[pos++]=(byte)255;
        }
        RawImageData frame=RawImageData.capture(rgba,720,1280,32);
        String title=OcrEngine.recognizeText(frame,
                UpgradeBuildingsRoutine.BUILDING_NAME_AREA_VALUE.topLeft(),
                UpgradeBuildingsRoutine.BUILDING_NAME_AREA_VALUE.bottomRight(),
                LeftMenuTextSettings.WHITE_SETTINGS).trim();
        String timer=OcrEngine.recognizeText(frame,
                TrainingCampBusyRead.CLOCK_AREA.topLeft(),
                TrainingCampBusyRead.CLOCK_AREA.bottomRight(),
                CommonOCRSettings.MARCH_QUEUE_TIMER_SETTINGS).trim();
        System.out.println("RESEARCHCENTER_BUSY_CAPTURE title="+title+" countdown="+timer);
        assertEquals("ResearchCenter",title);
        assertEquals("00:09:59",timer);
        assertEquals(Duration.ofMinutes(9).plusSeconds(59),
                ResearchCenterBusyRead.positive(title,timer,false));
    }
    @Test void rejectsAnythingButVerifiedBusyResearchCenter() {
        assertNull(ResearchCenterBusyRead.positive("ResearchCenter","",false));
        assertNull(ResearchCenterBusyRead.positive("ResearchCenter","00:09:59",true));
        assertNull(ResearchCenterBusyRead.positive("CommandCenter","00:09:59",false));
        assertNull(ResearchCenterBusyRead.positive("LancerCamp","00:09:59",false));
        assertNull(ResearchCenterBusyRead.positive("ResearchCenter","not a clock",false));
        assertNull(ResearchCenterBusyRead.positive(null,"00:09:59",false));
    }
}
