package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.TemplateSearchHelper;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntelMarkerProbeTest {
    @BeforeAll static void setup() throws Exception {
        OpenCvPatternLocator.loadNativeLibrary();
    }

    private static RawImageData frame(TemplatesEnum template, boolean includeMarker) throws Exception {
        BufferedImage image = new BufferedImage(720,1280,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        g.setColor(new java.awt.Color(24,40,56));
        g.fillRect(0,0,720,1280);
        if (includeMarker) {
            String path = "/templates/intel/"
                    + (template == TemplatesEnum.INTEL_BEAST_GRAYSCALE
                    ? "beastGrayscale.png" : "survivorGrayscale.png");
            BufferedImage sprite=ImageIO.read(Objects.requireNonNull(
                    IntelMarkerProbeTest.class.getResourceAsStream(path)));
            g.drawImage(sprite,280,570,null);
        }
        g.dispose();
        byte[] bytes = new byte[720*1280*4];
        int offset=0;
        for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
            int rgb=image.getRGB(x,y);
            bytes[offset++]=(byte)(rgb>>16);
            bytes[offset++]=(byte)(rgb>>8);
            bytes[offset++]=(byte)rgb;
            bytes[offset++]=(byte)255;
        }
        return RawImageData.capture(bytes,720,1280,32);
    }

    @Test void absentMarkerIsANormalMissWithoutFalseTap() throws Exception {
        var config=TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(3)
                .withThreshold(90).build();
        assertFalse(IntelMarkerProbe.find(frame(TemplatesEnum.INTEL_BEAST_GRAYSCALE,false),
                TemplatesEnum.INTEL_BEAST_GRAYSCALE,config).isFound());
        assertFalse(IntelMarkerProbe.find(null,TemplatesEnum.INTEL_BEAST_GRAYSCALE,config).isFound());
    }

    @Test void availableMarkerIsDetectedWithinItsAllowedSearchRegion() throws Exception {
        var screen=frame(TemplatesEnum.INTEL_BEAST_GRAYSCALE,true);
        var broad=TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(3)
                .withThreshold(90).build();
        assertTrue(IntelMarkerProbe.find(screen,TemplatesEnum.INTEL_BEAST_GRAYSCALE,broad).isFound());
        var otherRegion=TemplateSearchHelper.SearchConfig.builder().withMaxAttempts(1)
                .withThreshold(90).withArea(AreaData.of(0,0,200,200)).build();
        assertFalse(IntelMarkerProbe.find(screen,TemplatesEnum.INTEL_BEAST_GRAYSCALE,otherRegion).isFound());
    }
}
