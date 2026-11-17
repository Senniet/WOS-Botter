package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TrainingScopedScreenTest {
    @BeforeAll static void nativeLibrary() throws Exception {OpenCvPatternLocator.loadNativeLibrary();}

    @Test void identifiesOnlyOurThreeKnownTrainingFooterStates()throws Exception{
        assertTrue(TrainingScopedScreen.isTrainingPanel(frame("infantry-ready.png")));
        assertTrue(TrainingScopedScreen.isTrainingPanel(frame("lancer-ready.png")));
        assertTrue(TrainingScopedScreen.isTrainingPanel(frame("marksman-training.png")));
        assertFalse(TrainingScopedScreen.isTrainingPanel(frame("city-home.png")));
    }

    @Test void backArrowAndHomeAreVerifiedOnlyOnCorrectScreens()throws Exception{
        assertTrue(TrainingScopedScreen.backArrowOnTrainingPanel(frame("infantry-ready.png")).isFound());
        assertTrue(TrainingScopedScreen.backArrowOnTrainingPanel(frame("lancer-ready.png")).isFound());
        assertTrue(TrainingScopedScreen.backArrowOnTrainingPanel(frame("marksman-training.png")).isFound());
        assertFalse(TrainingScopedScreen.backArrowOnTrainingPanel(frame("city-home.png")).isFound());
        assertTrue(TrainingScopedScreen.isCityHome(frame("city-home.png")));
        assertFalse(TrainingScopedScreen.isCityHome(frame("marksman-training.png")));
    }

    @Test void oneVerifiedTrainingExitWithoutScanningOtherTaskScreens()throws Exception{
        var training=frame("marksman-training.png");var city=frame("city-home.png");
        var frames=List.of(training,training,city);
        var reads=new AtomicInteger();var taps=new ArrayList<String>();
        var result=TrainingScopedScreen.tryExit(
                ()->frames.get(reads.getAndIncrement()),
                point->taps.add(point.getX()+","+point.getY()), ()->{});
        assertEquals(TrainingScopedScreen.ExitResult.RETURNED_HOME,result);
        assertEquals(3,reads.get());assertEquals(1,taps.size());
    }

    @Test void neverTapsOnCityOrChangedUnknownTrainingFrame()throws Exception{
        var city=frame("city-home.png");var training=frame("marksman-training.png");
        var taps=new ArrayList<String>();
        assertEquals(TrainingScopedScreen.ExitResult.NOT_TRAINING,
                TrainingScopedScreen.tryExit(()->city,p->taps.add("tapped"),()->{}));
        var reads=new AtomicInteger();
        assertEquals(TrainingScopedScreen.ExitResult.UNVERIFIED,
                TrainingScopedScreen.tryExit(()->reads.getAndIncrement()==0?training:city,
                        p->taps.add("tapped"),()->{}));
        assertTrue(taps.isEmpty());
    }

    @Test void abortsIfGameDoesNotReturnHomeAfterOneTap()throws Exception{
        var training=frame("marksman-training.png");
        var taps=new AtomicInteger();
        assertEquals(TrainingScopedScreen.ExitResult.UNVERIFIED,
                TrainingScopedScreen.tryExit(()->training,p->taps.incrementAndGet(),()->{}));
        assertEquals(1,taps.get(),"Never retry Back blindly when the panel remains open");
    }

    private static RawImageData frame(String file)throws Exception{
        BufferedImage image;
        try(var source=Objects.requireNonNull(TrainingScopedScreenTest.class.getResourceAsStream(
                "/training/qa-20261115-scoped/"+file))){
            image=Objects.requireNonNull(ImageIO.read(source));
        }
        assertEquals(720,image.getWidth());assertEquals(1280,image.getHeight());
        byte[] bytes=new byte[720*1280*4];
        for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
            int color=image.getRGB(x,y),offset=(y*720+x)*4;
            bytes[offset]=(byte)(color>>16);bytes[offset+1]=(byte)(color>>8);
            bytes[offset+2]=(byte)color;bytes[offset+3]=(byte)255;
        }
        return RawImageData.capture(bytes,720,1280,32);
    }
}
