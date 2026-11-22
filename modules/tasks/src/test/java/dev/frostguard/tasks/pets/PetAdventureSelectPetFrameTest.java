package dev.frostguard.tasks.pets;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

class PetAdventureSelectPetFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void realNoAttemptsSelectPetIsIdentifiedNotMistakenForMap() throws Exception {
        var noAttempts = frame("select-pet-no-attempts-masked.png");
        assertTrue(PetAdventureSelectPetFrame.isSelectPet(noAttempts));
        assertTrue(PetAdventureSelectPetFrame.isNoAttempts(noAttempts));
        assertFalse(PetAdventureMapFrame.isMap(noAttempts));
    }

    @Test void noAttemptsIsNotAVisibleTreasureSpotAndMapIsDistinct() throws Exception {
        var select=frame("select-pet-no-attempts-masked.png");
        var treasure=frame("treasure-spot-modal-masked.png");
        assertTrue(PetAdventureSelectPetFrame.isTreasureSpot(treasure));
        assertFalse(PetAdventureSelectPetFrame.isTreasureSpot(select));
        assertFalse(PetAdventureSelectPetFrame.isTreasureSpot(map()));
    }

    @Test void closesBothNestedModalsBeforeLeavingAdventureMap() throws Exception {
        var select=frame("select-pet-no-attempts-masked.png");
        var treasure=frame("treasure-spot-modal-masked.png");
        var map=map();
        var index=new AtomicInteger();
        var taps=new java.util.ArrayList<String>();
        var frames=List.of(select,select,treasure,treasure,map);
        var result=PetAdventureSelectPetFrame.closeIfVisible(
                ()->frames.get(index.getAndIncrement()),
                p->taps.add(p.getX()+","+p.getY()), ()->{});
        assertEquals(PetAdventureSelectPetFrame.CloseResult.CLOSED_TO_MAP,result);
        assertEquals(List.of("665,220","644,212"),taps);
        assertEquals(5,index.get());
    }

    @Test void neverClosesUnverifiedTreasureSpotOrMakesASecondBlindTap() throws Exception {
        var select=frame("select-pet-no-attempts-masked.png");
        var treasure=frame("treasure-spot-modal-masked.png");
        var map=map();
        var index=new AtomicInteger();
        var taps=new java.util.ArrayList<String>();
        var frames=List.of(select,select,treasure,map);
        assertEquals(PetAdventureSelectPetFrame.CloseResult.UNVERIFIED,
                PetAdventureSelectPetFrame.closeIfVisible(
                    ()->frames.get(index.getAndIncrement()),
                    p->taps.add(p.getX()+","+p.getY()), ()->{}));
        assertEquals(List.of("665,220"),taps);
    }

    @Test void closesTheActualSelectPetWithOneXOnlyThenVerifiesMap() throws Exception {
        var select = frame("select-pet-no-attempts-masked.png");
        var map = map();
        var index = new AtomicInteger();
        var taps = new java.util.ArrayList<String>();
        var result = PetAdventureSelectPetFrame.closeIfVisible(
                () -> List.of(select,select,map).get(index.getAndIncrement()),
                p -> taps.add(p.getX()+","+p.getY()), () -> {});
        assertEquals(PetAdventureSelectPetFrame.CloseResult.CLOSED_TO_MAP, result);
        assertEquals(List.of("665,220"),taps);
        assertEquals(3,index.get());
    }

    @Test void wrongPageAndUnverifiedTransitionsNeverCauseRepeatedX() throws Exception {
        var select = frame("select-pet-no-attempts-masked.png");
        var map = map();
        var taps = new AtomicInteger();
        assertEquals(PetAdventureSelectPetFrame.CloseResult.NOT_PRESENT,
                PetAdventureSelectPetFrame.closeIfVisible(()->map,p->taps.incrementAndGet(),()->{}));
        var i = new AtomicInteger();
        assertEquals(PetAdventureSelectPetFrame.CloseResult.UNVERIFIED,
                PetAdventureSelectPetFrame.closeIfVisible(
                        ()->i.getAndIncrement()==0?select:map,p->taps.incrementAndGet(),()->{}));
        assertEquals(0,taps.get());
        assertEquals(PetAdventureSelectPetFrame.CloseResult.UNVERIFIED,
                PetAdventureSelectPetFrame.closeIfVisible(()->select,p->taps.incrementAndGet(),()->{}));
        assertEquals(1,taps.get());
    }

    private static RawImageData frame(String name) throws Exception {
        try(var stream=Objects.requireNonNull(PetAdventureSelectPetFrameTest.class
                .getResourceAsStream("/pets/live-20261122/"+name))) {
            return fromImage(ImageIO.read(stream));
        }
    }
    private static RawImageData map() throws Exception {
        try(var stream=Objects.requireNonNull(PetAdventureSelectPetFrameTest.class
                .getResourceAsStream("/pets/live-20261122/map-after-start.png"))) {
            return fromImage(ImageIO.read(stream));
        }
    }
    private static RawImageData fromImage(BufferedImage img) {
        byte[] bytes = new byte[img.getWidth()*img.getHeight()*4];
        for (int y=0;y<img.getHeight();y++)for(int x=0;x<img.getWidth();x++) {
            int c=img.getRGB(x,y), i=(y*img.getWidth()+x)*4;
            bytes[i]=(byte)(c>>16);bytes[i+1]=(byte)(c>>8);
            bytes[i+2]=(byte)c;bytes[i+3]=(byte)255;
        }
        return RawImageData.capture(bytes,img.getWidth(),img.getHeight(),32);
    }
}
