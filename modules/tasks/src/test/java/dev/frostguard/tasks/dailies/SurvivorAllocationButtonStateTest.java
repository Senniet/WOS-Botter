package dev.frostguard.tasks.dailies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SurvivorAllocationButtonStateTest {

    @BeforeAll
    static void initOpenCv() throws IOException {
        try {
            OpenCvPatternLocator.loadNativeLibrary();
        } catch (UnsatisfiedLinkError ignored) {
            // Shared JVM native OpenCV may already be loaded.
        }
    }

    @Test
    void blueButtonInActualNovember14StatusFrameIsEnabled() throws IOException {
        assertEquals(SurvivorAllocationButtonState.State.ENABLED,
                SurvivorAllocationButtonState.classify(image("survivor-status-blue-20261114-redacted.png")));
    }

    @Test
    void greyButtonInActualNovember15FailureFrameIsDisabled() throws IOException {
        assertEquals(SurvivorAllocationButtonState.State.DISABLED,
                SurvivorAllocationButtonState.classify(image("survivor-status-grey-20261115-redacted.png")));
    }

    @Test
    void statusTabIsSelectedInBothRealScreens() throws IOException {
        assertTrue(SurvivorAllocationButtonState.statusTabSelected(
                image("survivor-status-blue-20261114-redacted.png")));
        assertTrue(SurvivorAllocationButtonState.statusTabSelected(
                image("survivor-status-grey-20261115-redacted.png")));
        assertFalse(SurvivorAllocationButtonState.statusTabSelected(
                new BufferedImage(720, 1280, BufferedImage.TYPE_INT_RGB)));
    }

    @Test
    void verifiedHudIconIsGroundedInTheRealRedactedFrame() throws IOException {
        assertTrue(matches(frame("survivor-status-grey-20261115-redacted.png"),
                TemplatesEnum.GAME_HOME_SURVIVOR_COUNTER_ICON_2026,
                223, 0, 290, 55));
    }

    @Test
    void detailsTabColorDoesNotAuthorizeAssignment() throws IOException {
        BufferedImage details = image("survivor-status-grey-20261115-redacted.png");
        // Simulates selecting Details: the bottom tab colors swap; text and
        // gray Allocate All elsewhere are not sufficient evidence.
        details.setRGB(95, 1230, 0x406398);
        details.setRGB(525, 1230, 0xD3E6FC);
        assertFalse(SurvivorAllocationButtonState.statusTabSelected(details));
    }

    @Test
    void disabledLabelIdentityIsDetectedInTheRealGreyFrame() throws IOException {
        assertTrue(matches(frame("survivor-status-grey-20261115-redacted.png"),
                TemplatesEnum.GAME_HOME_NEW_SURVIVORS_ALLOCATE_ALL_DISABLED_2026,
                185, 1030, 535, 1180));
    }

    @Test
    void unrelatedOrWrongSizedScreensCannotProveAnAction() {
        assertEquals(SurvivorAllocationButtonState.State.UNKNOWN,
                SurvivorAllocationButtonState.classify(new BufferedImage(720, 1280, BufferedImage.TYPE_INT_RGB)));
        assertEquals(SurvivorAllocationButtonState.State.UNKNOWN,
                SurvivorAllocationButtonState.classify(new BufferedImage(360, 640, BufferedImage.TYPE_INT_RGB)));
        assertEquals(SurvivorAllocationButtonState.State.UNKNOWN,
                SurvivorAllocationButtonState.classify(null));
    }

    @Test
    void realStatusHeaderAndCloseAreVisibleIndependentlyOfButtonState() throws IOException {
        for (String fixture : new String[] {
                "survivor-status-blue-20261114-redacted.png",
                "survivor-status-grey-20261115-redacted.png"
        }) {
            byte[] bytes = frame(fixture);
            assertTrue(matches(bytes, TemplatesEnum.GAME_HOME_NEW_SURVIVORS_STATUS_TITLE_2026,
                    10, 320, 385, 430), fixture + ": status header");
            assertTrue(matches(bytes, TemplatesEnum.GAME_HOME_NEW_SURVIVORS_STATUS_CLOSE_2026,
                    640, 318, 715, 418), fixture + ": verified close");
            assertFalse(matches(bytes, TemplatesEnum.GAME_HOME_NEW_SURVIVORS_WELCOME_IN,
                    0, 310, 720, 1180), fixture + ": welcome has already been dismissed");
        }
    }

    private static boolean matches(byte[] bytes, TemplatesEnum template, int x0, int y0, int x1, int y1) {
        var found = OpenCvPatternLocator.locatePattern(
                bytes, template, new PointData(x0, y0), new PointData(x1, y1), 88);
        return found != null && found.isFound();
    }

    private static BufferedImage image(String fixture) throws IOException {
        try (InputStream in = stream(fixture)) {
            return ImageIO.read(in);
        }
    }

    private static byte[] frame(String fixture) throws IOException {
        try (InputStream in = stream(fixture)) {
            return in.readAllBytes();
        }
    }

    private static InputStream stream(String fixture) {
        return Objects.requireNonNull(
                SurvivorAllocationButtonStateTest.class.getResourceAsStream("/survivors/" + fixture),
                "Missing redacted real frame " + fixture);
    }
}
