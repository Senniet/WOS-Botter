package dev.frostguard.tasks.economy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

class StorehouseStaminaCapacityFrameTest {
    private static final PointData TOP_LEFT = new PointData(40, 300);
    private static final PointData BOTTOM_RIGHT = new PointData(680, 430);
    private static final double THRESHOLD = 88;

    @BeforeAll
    static void loadLibrary() throws IOException {
        try {
            OpenCvPatternLocator.loadNativeLibrary();
        } catch (UnsatisfiedLinkError ignored) {
            // Shared native OpenCV load.
        }
    }

    @Test
    void identifiesCapacityNoticeAfterARealClaimTap() throws IOException {
        var hit = OpenCvPatternLocator.locatePattern(encoded("stamina-capacity-full-toast-20261115-redacted.png"),
                TemplatesEnum.STOREHOUSE_STAMINA_CAPACITY_MAXED, TOP_LEFT, BOTTOM_RIGHT, THRESHOLD);
        System.out.println("CAPACITY_POSITIVE score=" + hit.getMatchScore());
        assertTrue(hit.isFound());
    }

    @Test
    void doesNotConfuseOrdinaryRewardDialogWithFullCapacity() throws IOException {
        var hit = OpenCvPatternLocator.locatePattern(encoded("stamina-live-20261113-redacted.png"),
                TemplatesEnum.STOREHOUSE_STAMINA_CAPACITY_MAXED, TOP_LEFT, BOTTOM_RIGHT, THRESHOLD);
        System.out.println("CAPACITY_NEGATIVE score=" + hit.getMatchScore());
        assertFalse(hit.isFound());
    }

    @Test
    void recognizesTheActualRewardCloseIconWhileCapacityToastIsVisible() throws IOException {
        var hit = OpenCvPatternLocator.locatePattern(encoded("stamina-capacity-full-toast-20261115-redacted.png"),
                TemplatesEnum.STOREHOUSE_STAMINA_REWARD_CLOSE,
                new PointData(560, 135), new PointData(665, 235), 85);
        assertTrue(hit.isFound());
    }

    private byte[] encoded(String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/storehouse/" + name)) {
            return Objects.requireNonNull(input, "Missing test frame: " + name).readAllBytes();
        }
    }
}
