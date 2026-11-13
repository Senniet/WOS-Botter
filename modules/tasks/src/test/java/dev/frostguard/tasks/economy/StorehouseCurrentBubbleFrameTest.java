package dev.frostguard.tasks.economy;

import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorehouseCurrentBubbleFrameTest {
    @Test void recognizesCurrentLiveCrateAndStaminaBubblesAsDifferentActions() throws Exception {
        var crate=StorehouseBubbleDetector.locate(frame("storehouse-live-20261113-ready-crate-redacted.png"));
        assertTrue(crate.stream().anyMatch(candidate -> candidate.kind() == StorehouseBubbleDetector.Kind.CHEST),
                "Ready Storehouse crate bubble must be found before tapping");
        var stamina=StorehouseBubbleDetector.locate(frame("storehouse-live-20261113-ready-stamina-redacted.png"));
        assertTrue(stamina.stream().anyMatch(candidate -> candidate.kind() == StorehouseBubbleDetector.Kind.STAMINA),
                "Stamina can must be found after collecting chest");
        assertTrue(stamina.stream().noneMatch(candidate -> candidate.kind() == StorehouseBubbleDetector.Kind.CHEST),
                "Stamina can must never be misidentified as a chest");
    }

    @Test void noStaleBubbleIsFoundAfterClaim() throws Exception {
        var collected=StorehouseBubbleDetector.locate(frame("storehouse-live-20261113-after-stamina-redacted.png"));
        assertTrue(collected.isEmpty(), "Post-claim frame must not trigger another claim");
    }

    private static BufferedImage frame(String filename) throws Exception {
        return ImageIO.read(Objects.requireNonNull(
                StorehouseCurrentBubbleFrameTest.class.getResource("/storehouse/" + filename)));
    }
}
