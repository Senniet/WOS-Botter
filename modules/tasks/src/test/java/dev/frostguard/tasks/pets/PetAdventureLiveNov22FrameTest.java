package dev.frostguard.tasks.pets;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PetAdventureLiveNov22FrameTest {
    private static final PointData A=new PointData(0,0),B=new PointData(720,1280);
    @BeforeAll static void cv() throws Exception {OpenCvPatternLocator.loadNativeLibrary();}
    private byte[] image(String name)throws Exception {
        try(var stream=Objects.requireNonNull(getClass().getResourceAsStream(
                "/pets/live-20261122/"+name))) {return stream.readAllBytes();}
    }
    @Test void recognizesLiveInAdventureConfirmationWithinOneSecond()throws Exception {
        for(var file:List.of("post-start-0p6.png","post-start-1p6.png")){
            var hit=OpenCvPatternLocator.locatePattern(image(file),
                    TemplatesEnum.PETS_CHEST_IN_ADVENTURE,A,B,90);
            System.out.println(file+" inAdventure="+hit);
            assertTrue(hit.isFound(),"Actual 22-Nov 2026 Start overlay must confirm on "+file);
        }
    }
    @Test void freshLiveOverlayMatchesTheNewConfirmationTemplate() throws Exception {
        for(String name:List.of("post-start-0p6.png","post-start-1p6.png")) {
            var hit=OpenCvPatternLocator.locatePattern(image(name),
                TemplatesEnum.PETS_CHEST_IN_ADVENTURE_NOV_2026,
                new PointData(240,272),new PointData(480,370),89);
            System.out.println("new overlay "+name+"="+hit);
            assertTrue(hit.isFound(),name);
        }
    }

    @Test void verifiesTheActualMapButNotAnAdventureDialog() throws Exception {
        assertTrue(PetAdventureMapFrame.isMap(raw("map-after-start.png")));
        assertFalse(PetAdventureMapFrame.isMap(raw("post-start-0p6.png")));
    }

    private dev.frostguard.api.domain.RawImageData raw(String name) throws Exception {
        java.awt.image.BufferedImage im;
        try(var stream=Objects.requireNonNull(getClass().getResourceAsStream(
            "/pets/live-20261122/"+name))) {
            im=javax.imageio.ImageIO.read(stream);
        }
        byte[] bytes=new byte[im.getWidth()*im.getHeight()*4];
        for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){
            int a=im.getRGB(x,y),i=(y*im.getWidth()+x)*4;
            bytes[i]=(byte)(a>>16);bytes[i+1]=(byte)(a>>8);
            bytes[i+2]=(byte)a;bytes[i+3]=(byte)255;
        }
        return dev.frostguard.api.domain.RawImageData.capture(
            bytes,im.getWidth(),im.getHeight(),32);
    }

    @Test void currentSelectPetStartButtonIsRecognizedWithoutFalseAdventure()throws Exception {
        byte[] screen=image("select-pet-start.png");
        var start=OpenCvPatternLocator.locatePattern(screen,
                TemplatesEnum.PETS_CHEST_START,A,B,90);
        var overlay=OpenCvPatternLocator.locatePattern(screen,
                TemplatesEnum.PETS_CHEST_IN_ADVENTURE,A,B,90);
        System.out.println("Live Select Pet start="+start+" inAdventure="+overlay);
        assertTrue(start.isFound(),"Start must match current 22 Nov live Pet Select panel");
        assertFalse(overlay.isFound(),"Pet selection before tapping Start must not look already running");
    }

    @Test void recognizesTimerOnTheOriginalStartedChestAfterClose()throws Exception {
        var timers=OpenCvPatternLocator.locateAllPatterns(image("map-after-start.png"),
                TemplatesEnum.PETS_CHEST_ADVENTURE_TIMER,A,B,80,5)
                .stream().filter(ImageSearchResultData::isFound).toList();
        System.out.println("Live timers="+timers);
        assertTrue(PetAdventureDecisions.startedChestHasTimer(new PointData(222,308),timers),
                "The original pending 222,308 must be confirmable by its running pin");
    }
}
