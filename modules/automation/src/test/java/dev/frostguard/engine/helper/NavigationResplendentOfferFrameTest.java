package dev.frostguard.engine.helper;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavigationResplendentOfferFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void liveResplendentPurchaseOverlayHasVerifiedTitleAndCloseButton() throws Exception {
        byte[] frame = Objects.requireNonNull(getClass().getResourceAsStream(
                "/offer-live-top-20261114.jpg")).readAllBytes();
        assertTrue(OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_OFFER_RESPLENDENT_TITLE_2026.getTemplate(),
                new PointData(65, 125), new PointData(555, 235), 91).isFound(), "offer title");
        assertTrue(OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_OFFER_RESPLENDENT_CLOSE_2026.getTemplate(),
                new PointData(560, 125), new PointData(675, 245), 92).isFound(), "safe close X");
    }
}
