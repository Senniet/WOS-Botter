package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class DailyClaimAllFrameTest {
    @BeforeAll static void cv() throws Exception{OpenCvPatternLocator.loadNativeLibrary();}

    private static RawImageData frame(String file) throws Exception {
        BufferedImage image;
        try (var input=Objects.requireNonNull(DailyClaimAllFrameTest.class
                .getResourceAsStream("/daily/qa-20261116/"+file))) {image=ImageIO.read(input);}
        byte[] data=new byte[720*1280*4];
        for (int y=0;y<1280;y++)for(int x=0;x<720;x++) {
            int color=image.getRGB(x,y),i=(y*720+x)*4;
            data[i]=(byte)(color>>16);data[i+1]=(byte)(color>>8);
            data[i+2]=(byte)color;data[i+3]=(byte)255;
        }
        return RawImageData.capture(data,720,1280,32);
    }

    @Test void realClaimAllOnDailyPageIsTappable() throws Exception {
        assertTrue(DailyClaimAllFrame.ready(frame("daily-before-claim.png")).isFound());
    }

    @Test void actualAfterClaimAndOtherPagesCannotTriggerASecondTap() throws Exception {
        for(String file:new String[]{"daily-after-claim.png","daily-rewards.png","city-home.png"})
            assertFalse(DailyClaimAllFrame.ready(frame(file)).isFound(),file);
    }
}
