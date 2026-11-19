package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchCenterConstructionProbeTest {
    private static final String FIXTURE="/research/live-20261119/research-center-upgrading.png";
    @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
    private static RawImageData screenshot()throws Exception{ return screenshot(FIXTURE); }
    private static RawImageData screenshot(String path)throws Exception{
        BufferedImage b=ImageIO.read(Objects.requireNonNull(ResearchCenterConstructionProbeTest.class.getResourceAsStream(path)));
        byte[] rgba=new byte[b.getWidth()*b.getHeight()*4];int i=0;
        for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++){
            int rgb=b.getRGB(x,y);rgba[i++]=(byte)(rgb>>16);rgba[i++]=(byte)(rgb>>8);
            rgba[i++]=(byte)rgb;rgba[i++]=(byte)255;
        }
        return RawImageData.capture(rgba,b.getWidth(),b.getHeight(),32);
    }
    @Test void liveConstructionShowsResearchCenterBuildingUpgradeAndQueueTwoClock()throws Exception{
        var reading=ResearchCenterConstructionProbe.inspect(screenshot());
        assertNotNull(reading,"Constructing Research Center in actual City panel must be detected");
        assertEquals(2,reading.queue());
        assertEquals(Duration.ofHours(7).plusMinutes(33).plusSeconds(10),reading.remaining());
        assertTrue(ResearchCenterConstructionProbe.agrees(reading,reading));
        System.out.println("RESEARCH_CONSTRUCTION_OBSERVED "+reading);
    }
    @Test void researchCenterUpgradingAlsoWorksInFirstConstructionQueue()throws Exception {
        var reading=ResearchCenterConstructionProbe.inspect(screenshot(
                "/research/live-20261119/research-center-upgrading-queue1.png"));
        assertNotNull(reading);
        assertEquals(1,reading.queue());
        assertEquals(Duration.ofHours(7).plusMinutes(33).plusSeconds(10),reading.remaining());
    }
    @Test void noScreenAndUnrelatedScreenDoNotClaimBuildingConstruction()throws Exception{
        assertNull(ResearchCenterConstructionProbe.inspect(null));
        assertNull(ResearchCenterConstructionProbe.inspect(RawImageData.capture(new byte[720*1280*4],720,1280,32)));
    }
    @Test void refusesConflictingQueueOrClockStates(){
        var first=new ResearchCenterConstructionProbe.Observation(2,Duration.ofHours(7),428);
        assertTrue(ResearchCenterConstructionProbe.agrees(first,new ResearchCenterConstructionProbe.Observation(2,Duration.ofHours(7).minusSeconds(3),428)));
        assertTrue(ResearchCenterConstructionProbe.agrees(first,new ResearchCenterConstructionProbe.Observation(2,Duration.ofHours(7).minusSeconds(3),430)));
        assertFalse(ResearchCenterConstructionProbe.agrees(first,new ResearchCenterConstructionProbe.Observation(2,Duration.ofHours(7).minusSeconds(3),433)));
        assertFalse(ResearchCenterConstructionProbe.agrees(first,new ResearchCenterConstructionProbe.Observation(1,Duration.ofHours(7),360)));
        assertFalse(ResearchCenterConstructionProbe.agrees(first,new ResearchCenterConstructionProbe.Observation(2,Duration.ofHours(1),428)));
        assertFalse(ResearchCenterConstructionProbe.agrees(first,new ResearchCenterConstructionProbe.Observation(2,null,428)));
    }
}
