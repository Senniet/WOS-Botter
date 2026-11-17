package dev.frostguard.tasks.pets;

import dev.frostguard.api.domain.RawImageData;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class PetSkillsCurrentScreenTest {
    private RawImageData live(String name) throws Exception {
        BufferedImage crop=ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/pets/"+name)));
        BufferedImage full=new BufferedImage(720,1280,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=full.createGraphics();
        try {g.drawImage(crop,23,115,null);}finally{g.dispose();}
        byte[] data=new byte[720*1280*4];int i=0;
        for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
            int c=full.getRGB(x,y);
            data[i++]=(byte)(c>>16);data[i++]=(byte)(c>>8);data[i++]=(byte)c;data[i++]=(byte)255;
        }
        return RawImageData.capture(data,720,1280,32);
    }

    @Test void recognizesCurrentTwoTilePetScreenAndActualStaminaIdentity() throws Exception {
        var locked=live("pet-arctic-20261117.jpg");
        assertTrue(PetSkillsCurrentScreen.isCurrentMenu(locked));
        assertEquals(PetSkillsCurrentScreen.Skill.STAMINA,
                PetSkillsCurrentScreen.selectedSkill(locked));
        assertEquals(PetSkillsCurrentScreen.State.LOCKED,
                PetSkillsCurrentScreen.selectedState(locked));
        assertNull(PetSkillsCurrentScreen.remainingCooldown(locked));
    }

    @Test void doesNotMistakeCurrentFirstBuilderTileForStaminaOrAvailableUse() throws Exception {
        var cooling=live("pet-builder-20261117.jpg");
        assertTrue(PetSkillsCurrentScreen.isCurrentMenu(cooling));
        assertEquals(PetSkillsCurrentScreen.Skill.OTHER,
                PetSkillsCurrentScreen.selectedSkill(cooling));
        assertEquals(PetSkillsCurrentScreen.State.COOLDOWN,
                PetSkillsCurrentScreen.selectedState(cooling));
        var remaining=PetSkillsCurrentScreen.remainingCooldown(cooling);
        assertNotNull(remaining);
        assertTrue(remaining.toHours()>=9 && remaining.toHours()<11);
    }

    @Test void realCurrentScreenScansOnlyTheTwoOccupiedTiles() throws Exception {
        var live=dev.frostguard.vision.convert.ImageConverter.toBufferedImage(
                live("pet-arctic-20261117.jpg"));
        assertTrue(PetSkillsRoutine.hasDistinctiveSkillTilePixels(live,
                dev.frostguard.api.domain.AreaData.of(85,239,195,367)));
        assertTrue(PetSkillsRoutine.hasDistinctiveSkillTilePixels(live,
                dev.frostguard.api.domain.AreaData.of(230,239,340,367)));
        assertFalse(PetSkillsRoutine.hasDistinctiveSkillTilePixels(live,
                dev.frostguard.api.domain.AreaData.of(375,239,485,367)));
        assertFalse(PetSkillsRoutine.hasDistinctiveSkillTilePixels(live,
                dev.frostguard.api.domain.AreaData.of(85,389,195,517)));
    }

    @Test void unknownStatesFailClosedAndAllTileCoordinatesStayInViewport() {
        assertEquals(PetSkillsCurrentScreen.Skill.UNKNOWN,
                PetSkillsCurrentScreen.selectedSkill(new RawImageData()));
        assertEquals(PetSkillsCurrentScreen.State.UNKNOWN,
                PetSkillsCurrentScreen.selectedState(new RawImageData()));
        assertEquals(12,PetSkillsCurrentScreen.visibleTileCenters().length);
        for(var p:PetSkillsCurrentScreen.visibleTileCenters()){
            assertTrue(p.getX()>50 && p.getX()<680);
            assertTrue(p.getY()>200 && p.getY()<740);
        }
        assertFalse(PetSkillsCurrentScreen.isCurrentMenu(null));
    }
}
