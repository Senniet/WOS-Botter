package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.api.domain.PointData;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

/** Actual, privacy-masked Task Coach screenshots of positive & negative states. */
class TrainingPanelFrameEvidenceTest {
    @Test void recognisesSelectedInfantrySixWithNextTierLockedAndBlueTrainButton() throws Exception {
        assertTrue(TrainingPanelFrameEvidence.currentTopUnlockedTierIsTrainable(
                capture("infantry-selected.jpg")));
    }

    @Test void recognisesSelectedLancerSixWithNextTierLockedAndBlueTrainButton() throws Exception {
        assertTrue(TrainingPanelFrameEvidence.currentTopUnlockedTierIsTrainable(
                capture("lancer-selected.jpg")));
    }

    @Test void doesNotMistakeCityAfterEdgeSwipeForTroopPanel() throws Exception {
        assertFalse(TrainingPanelFrameEvidence.currentTopUnlockedTierIsTrainable(
                capture("city-after-unsafe-swipe.jpg")));
        assertFalse(TrainingPanelFrameEvidence.currentTopUnlockedTierIsTrainable(
                capture("city-train-building.jpg")));
    }

    @Test void missingBlueTrainButtonCannotAuthoriseTap() throws Exception {
        RawImageData frame=capture("infantry-selected.jpg");
        byte[] rgba=frame.getData().clone();
        for(int y=1085;y<1160;y++)for(int x=375;x<695;x++){
            int i=(y*720+x)*4;
            rgba[i]=30;rgba[i+1]=40;rgba[i+2]=60;
        }
        assertFalse(TrainingPanelFrameEvidence.currentTopUnlockedTierIsTrainable(
                RawImageData.capture(rgba,720,1280,32)));
    }

    @Test void missingSelectedTierCannotAuthoriseTap() throws Exception {
        RawImageData frame=capture("infantry-selected.jpg");
        byte[] rgba=frame.getData().clone();
        for(int y=635;y<755;y++)for(int x=245;x<335;x++){
            int i=(y*720+x)*4;
            rgba[i]=40;rgba[i+1]=50;rgba[i+2]=65;
        }
        assertFalse(TrainingPanelFrameEvidence.currentTopUnlockedTierIsTrainable(
                RawImageData.capture(rgba,720,1280,32)));
    }

    @Test void unlockedNextTierCannotBeAssumedToBeCurrentHighest() throws Exception {
        RawImageData frame=capture("infantry-selected.jpg");
        byte[] rgba=frame.getData().clone();
        // A glowing available next card cannot count as a locked upper tier.
        for(int y=640;y<743;y++)for(int x=426;x<478;x++){
            int i=(y*720+x)*4;
            rgba[i]=(byte)220;rgba[i+1]=(byte)235;rgba[i+2]=(byte)242;
        }
        assertFalse(TrainingPanelFrameEvidence.currentTopUnlockedTierIsTrainable(
                RawImageData.capture(rgba,720,1280,32)));
    }

    @Test void carouselSwipeMustStayAwayFromAndroidBackEdgesAndWithinActualTierStrip() {
        assertTrue(TrainingRoutine.insideTroopCarouselGesture(
                new PointData(555,710),new PointData(165,710)));
        assertTrue(TrainingRoutine.insideTroopCarouselGesture(
                new PointData(545,710),new PointData(185,710)));
        assertFalse(TrainingRoutine.insideTroopCarouselGesture(
                new PointData(690,785),new PointData(73,785)),
                "Old 720px-edge swipe from the failed run must be rejected");
        assertFalse(TrainingRoutine.insideTroopCarouselGesture(
                new PointData(530,773),new PointData(490,773)),
                "Old 40px drag outside the tier strip cannot advance tiers");
        assertFalse(TrainingRoutine.insideTroopCarouselGesture(
                new PointData(120,1213),new PointData(600,1213)),
                "Footer tabs are not tier-carousel drags");
    }

    private static RawImageData capture(String file) throws Exception {
        var type=TrainingPanelFrameEvidenceTest.class;
        BufferedImage image;
        try (var src=Objects.requireNonNull(type.getResourceAsStream(
                "/training/qa-20261115/"+file))) {
            image=Objects.requireNonNull(ImageIO.read(src));
        }
        assertEquals(720,image.getWidth());assertEquals(1280,image.getHeight());
        byte[] data=new byte[720*1280*4];
        for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
            int c=image.getRGB(x,y),i=(y*720+x)*4;
            data[i]=(byte)(c>>16);data[i+1]=(byte)(c>>8);
            data[i+2]=(byte)c;data[i+3]=(byte)255;
        }
        return RawImageData.capture(data,720,1280,32);
    }
}
