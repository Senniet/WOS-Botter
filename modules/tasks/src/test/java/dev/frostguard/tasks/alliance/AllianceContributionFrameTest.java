package dev.frostguard.tasks.alliance;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class AllianceContributionFrameTest {
    @BeforeAll static void cv() throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
    private static BufferedImage image() throws Exception {
        try(var source=Objects.requireNonNull(AllianceContributionFrameTest.class
                .getResourceAsStream("/alliance/live-20261117/contribution-ready-redacted.png"))){
            return ImageIO.read(source);
        }
    }
    private static RawImageData frame(BufferedImage image) {
        var bytes=new byte[720*1280*4];
        for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
            int c=image.getRGB(x,y),i=(y*720+x)*4;
            bytes[i]=(byte)(c>>16);bytes[i+1]=(byte)(c>>8);
            bytes[i+2]=(byte)c;bytes[i+3]=(byte)255;
        }
        return RawImageData.capture(bytes,720,1280,32);
    }
    @Test void actualBlueResourceContributeOnReal2026Popup() throws Exception {
        var f=frame(image());
        assertTrue(AllianceContributionFrame.popup(f));
        var action=AllianceContributionFrame.freeResourceButton(f);
        assertTrue(action.isFound());
        assertTrue(action.getPoint().getX()>400 && action.getPoint().getX()<630);
    }
    @Test void unrelatedBlueButtonCanNeverBeTreatedAsAllianceDonation() throws Exception {
        var image=image();
        for(int y=155;y<245;y++)for(int x=150;x<560;x++)image.setRGB(x,y,0xff000000);
        assertFalse(AllianceContributionFrame.popup(frame(image)));
        assertFalse(AllianceContributionFrame.freeResourceButton(frame(image)).isFound());
    }
}
