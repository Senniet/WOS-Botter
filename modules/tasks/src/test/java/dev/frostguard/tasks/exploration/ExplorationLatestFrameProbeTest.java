package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.*;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

/** Replays the real, account-masked Task Coach 1.1.68 screen after Explore. */
public class ExplorationLatestFrameProbeTest {
    @BeforeAll static void nativeOpenCv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}

    @Test void squadSettingsFalseQuickModalMustNotBlockVerifiedFight()throws Exception{
        BufferedImage image=image("squad-false-dialog.png");
        assertNull(DoExplorationRoutine.quickConfirmationButton(image),
                "Hero cards at y~=1025 must never count as mid-screen modal buttons");
        RawImageData frame=frame(image);
        assertTrue(ExplorationSquadFrame.isSquadReady(frame),
                "Specific Squad Settings title and actual Fight CTA are both present");
        assertTrue(OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.EXPLORATION_QUICK_DEPLOY_LIVE.getTemplate(),
                new PointData(30,1145),new PointData(360,1275),90).isFound());
        assertTrue(OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.INTEL_JOURNEY_FIGHT_LIVE.getTemplate(),
                new PointData(365,1145),new PointData(710,1275),90).isFound());
    }

    @Test void unrelatedRewardsScreenNeverAuthorisesSquadActions()throws Exception{
        RawImageData frame=frame(image("chief-rewards.png"));
        assertFalse(ExplorationSquadFrame.isSquadReady(frame));
    }

    public static BufferedImage image(String filename)throws Exception{
        try(var source=Objects.requireNonNull(ExplorationLatestFrameProbeTest.class.getResourceAsStream(
                "/exploration/qa-20261115/"+filename))){return Objects.requireNonNull(ImageIO.read(source));}
    }

    public static RawImageData frame(BufferedImage image){
        assertEquals(720,image.getWidth());assertEquals(1280,image.getHeight());
        byte[] data=new byte[720*1280*4];
        for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
            int color=image.getRGB(x,y),i=(y*720+x)*4;
            data[i]=(byte)(color>>16);data[i+1]=(byte)(color>>8);data[i+2]=(byte)color;data[i+3]=(byte)255;
        }
        return RawImageData.capture(data,720,1280,32);
    }
}
