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
    @Test void currentHudRingWithFortySixStaminaIsStillDetected() throws Exception {
        var point = StaminaHudEntry.verifiedTarget(frameAt(
                "/stamina/live-hud-20261122/stamina-46-hud-only.png"));
        assertNotNull(point, "2026 current HUD ring is narrower than old bar");
        assertEquals(42, point.getX());
        assertEquals(106, point.getY());
    }

    @Test void shortGreenHudAtLowerStaminaRemainsVerified() {
        // Low-stamina bar has fewer green pixels than the old 220 threshold.
        BufferedImage image = new BufferedImage(720, 1280, BufferedImage.TYPE_INT_ARGB);
        for (int y = 101; y <= 112; y++) {
            for (int x = 26; x <= 37; x++) {
                image.setRGB(x, y, 0xff26bf16);
            }
        }
        byte[] rgba = new byte[720 * 1280 * 4];
        for (int y = 101; y <= 112; y++) {
            for (int x = 26; x <= 37; x++) {
                int i = (y * 720 + x) * 4;
                rgba[i] = 0x26;
                rgba[i+1] = (byte) 0xbf;
                rgba[i+2] = 0x16;
                rgba[i+3] = (byte) 255;
            }
        }
        assertEquals(42, StaminaHudEntry.verifiedTarget(
                RawImageData.capture(rgba, 720, 1280, 32)).getX());
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
