package dev.frostguard.tasks.events;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class FishingGoFishCurrentScreenFrameTest {
    @BeforeAll static void loadCv() throws Exception {
        try { OpenCvPatternLocator.loadNativeLibrary(); } catch (UnsatisfiedLinkError ignored) { }
    }
    @Test void current2026FishingTitleAndGoFishAreDetectedWithIndependentAnchors() throws Exception {
        byte[] image;
        try (var file=Objects.requireNonNull(getClass().getResourceAsStream(
                "/fishing/go-fish-current-screen-masked.png"))) { image=file.readAllBytes(); }
        var title=OpenCvPatternLocator.locatePattern(image,"/templates/fishing/title.png",
                new PointData(85,0),new PointData(500,80),90);
        var goFish=OpenCvPatternLocator.locatePattern(image,"/templates/fishing/goFish.png",
                new PointData(270,1040),new PointData(465,1130),
                FishingRetryNavigator.thresholdFor(dev.frostguard.api.configs.TemplatesEnum.FISHING_GO_FISH));
        assertTrue(title.isFound(),"Current Fishing Tournament title must be identified: "+title);
        assertTrue(goFish.isFound(),"Current Go Fish button must be identified: "+goFish);
    }
    @Test void unrelatedFishingButtonsKeepNinetyPercentThreshold() {
        assertEquals(90,FishingRetryNavigator.thresholdFor(dev.frostguard.api.configs.TemplatesEnum.FISHING_NORMAL_CAST));
    }
}
