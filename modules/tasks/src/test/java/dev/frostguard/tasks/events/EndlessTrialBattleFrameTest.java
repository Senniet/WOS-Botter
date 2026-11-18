package dev.frostguard.tasks.events;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EndlessTrialBattleFrameTest {
    @BeforeAll static void loadNative() throws Exception {OpenCvPatternLocator.loadNativeLibrary();}

    static RawImageData frame(String name) throws Exception {
        BufferedImage img=ImageIO.read(EndlessTrialBattleFrameTest.class.getResourceAsStream("/endlesstrial/"+name));
        assertEquals(720,img.getWidth()); assertEquals(1280,img.getHeight());
        byte[] rgba=new byte[720*1280*4];int k=0;
        for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
            int rgb=img.getRGB(x,y);rgba[k++]=(byte)(rgb>>16);rgba[k++]=(byte)(rgb>>8);
            rgba[k++]=(byte)rgb;rgba[k++]=(byte)255;
        }
        return RawImageData.capture(rgba,720,1280,32);
    }

    @Test void realLiveWindhowlerFlowOnlyAuthorizesActionsOnCorrectScreens() throws Exception {
        var card=frame("boss-world.jpg");
        var formation=frame("formation.jpg");
        var result=frame("damage-result.jpg");
        assertTrue(EndlessTrialBattleFrame.isWorldBossCard(card));
        assertTrue(EndlessTrialBattleFrame.canAttackWorldBoss(card));
        assertFalse(EndlessTrialBattleFrame.canDeploy(card));
        assertFalse(EndlessTrialBattleFrame.isDamageResult(card));
        assertTrue(EndlessTrialBattleFrame.isWindhowlerFormation(formation));
        assertTrue(EndlessTrialBattleFrame.canDeploy(formation));
        assertFalse(EndlessTrialBattleFrame.canAttackWorldBoss(formation));
        assertTrue(EndlessTrialBattleFrame.isDamageResult(result));
        assertFalse(EndlessTrialBattleFrame.canAttackWorldBoss(result));
        assertFalse(EndlessTrialBattleFrame.canDeploy(result));
    }

    @Test void worldRootIsPositiveOnlyAfterClosingResult() throws Exception {
        var world=frame("world-after-confirm.jpg");
        assertTrue(EndlessTrialBattleFrame.isWorldRoot(world));
        assertFalse(EndlessTrialBattleFrame.isWorldRoot(frame("formation.jpg")));
        assertFalse(EndlessTrialBattleFrame.isWorldRoot(frame("damage-result.jpg")));
    }

    @Test void sidebarAndEventPanelAreNeverCombatActions() throws Exception {
        var other=EndlessTrialLiveFrameTest.frame("events-strip.jpg");
        assertFalse(EndlessTrialBattleFrame.canAttackWorldBoss(other));
        assertFalse(EndlessTrialBattleFrame.canDeploy(other));
        assertFalse(EndlessTrialBattleFrame.isDamageResult(other));
    }
}
