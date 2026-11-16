package dev.frostguard.engine.helper;

import dev.frostguard.api.domain.RawImageData;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class StaminaHudEntryFrameTest {
    @Test void actualCityAndWorldStaminaBarSelectsY106NotCityBonusArrowY140() throws Exception {
        for(var scene:new String[]{"city-redacted.png","world-redacted.png"}){
            var point=StaminaHudEntry.verifiedTarget(frame(scene));
            assertNotNull(point,scene);
            assertEquals(42,point.getX());
            assertEquals(106,point.getY(),"City Bonus is y140 and must not be tapped");
        }
    }
    @Test void cityBonusOpenedByOldArrowCannotBeMistakenForStaminaHud() throws Exception {
        assertNull(StaminaHudEntry.verifiedTarget(
                frameAt("/stamina/city-bonus-not-stamina-20261116.png")));
    }
    @Test void rewardCutsceneHasNoStaminaBar() throws Exception {
        assertNull(StaminaHudEntry.verifiedTarget(frame("hero-chest-redacted.png")));
        assertNull(StaminaHudEntry.verifiedTarget(null));
    }
    private static RawImageData frame(String scene)throws Exception {
        return frameAt("/stamina/live-hud-20261114/"+scene);
    }
    private static RawImageData frameAt(String path)throws Exception {
        BufferedImage image;
        try(var stream=Objects.requireNonNull(StaminaHudEntryFrameTest.class.getResourceAsStream(
                path))){image=ImageIO.read(stream);}
        byte[] rgba=new byte[image.getWidth()*image.getHeight()*4];
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            int c=image.getRGB(x,y),i=(y*image.getWidth()+x)*4;
            rgba[i]=(byte)(c>>16);rgba[i+1]=(byte)(c>>8);rgba[i+2]=(byte)c;rgba[i+3]=(byte)255;
        }
        return RawImageData.capture(rgba,image.getWidth(),image.getHeight(),32);
    }
}
