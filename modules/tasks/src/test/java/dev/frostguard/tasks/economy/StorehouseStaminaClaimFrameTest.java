package dev.frostguard.tasks.economy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import dev.frostguard.api.domain.RawImageData;
import java.io.InputStream;
import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

class StorehouseStaminaClaimFrameTest {

    private static final PointData CLAIM_AREA_TOP_LEFT = new PointData(200, 900);
    private static final PointData CLAIM_AREA_BOTTOM_RIGHT = new PointData(520, 1020);
    private static final PointData TITLE_AREA_TOP_LEFT = new PointData(150, 740);
    private static final PointData TITLE_AREA_BOTTOM_RIGHT = new PointData(560, 885);
    private static final PointData TOOLTIP_TITLE_AREA_TOP_LEFT = new PointData(45, 670);
    private static final PointData TOOLTIP_TITLE_AREA_BOTTOM_RIGHT = new PointData(300, 750);
    private static final double MATCH_THRESHOLD = 88;

    @BeforeAll
    static void loadOpenCv() throws IOException {
        try {
            OpenCvPatternLocator.loadNativeLibrary();
        } catch (UnsatisfiedLinkError ignored) {
            // Another frame test may already have loaded the native library in this JVM.
        }
    }

    @Test
    void detectsStorehouseRewardTitleAndClaimInTheExpandedButtonArea() throws IOException {
        byte[] frame = encoded("stamina-reward-claim-visible.png");

        assertTrue(matches(frame, TemplatesEnum.STOREHOUSE_STAMINA_REWARD_TITLE,
                TITLE_AREA_TOP_LEFT, TITLE_AREA_BOTTOM_RIGHT));
        assertTrue(matches(frame, TemplatesEnum.STOREHOUSE_STAMINA_CLAIM_TEXT,
                CLAIM_AREA_TOP_LEFT, CLAIM_AREA_BOTTOM_RIGHT));
        assertFalse(matches(frame, TemplatesEnum.STOREHOUSE_STAMINA_TOOLTIP_TITLE,
                TOOLTIP_TITLE_AREA_TOP_LEFT, TOOLTIP_TITLE_AREA_BOTTOM_RIGHT));
    }

    @Test
    void currentWosSennie121LiveClaimDialogMatchesWithoutTooltip() throws IOException {
        byte[] frame = encoded("stamina-live-20261113-redacted.png");
        assertTrue(matches(frame, TemplatesEnum.STOREHOUSE_STAMINA_REWARD_TITLE,
                TITLE_AREA_TOP_LEFT, TITLE_AREA_BOTTOM_RIGHT));
        assertTrue(matches(frame, TemplatesEnum.STOREHOUSE_STAMINA_CLAIM_TEXT,
                CLAIM_AREA_TOP_LEFT, CLAIM_AREA_BOTTOM_RIGHT));
        assertFalse(matches(frame, TemplatesEnum.STOREHOUSE_STAMINA_TOOLTIP_TITLE,
                TOOLTIP_TITLE_AREA_TOP_LEFT, TOOLTIP_TITLE_AREA_BOTTOM_RIGHT));
    }

    @Test
    void live20261115DialogWithVisibleClaimIsRecognized() throws IOException {
        byte[] frame = encoded("storehouse-live-20261115-claim-redacted.png");
        ImageSearchResultData title = OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.STOREHOUSE_STAMINA_REWARD_TITLE,
                TITLE_AREA_TOP_LEFT, TITLE_AREA_BOTTOM_RIGHT, MATCH_THRESHOLD);
        ImageSearchResultData claim = OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.STOREHOUSE_STAMINA_CLAIM_TEXT,
                CLAIM_AREA_TOP_LEFT, CLAIM_AREA_BOTTOM_RIGHT, MATCH_THRESHOLD);
        assertTrue(title != null && title.isFound(), "Reward title must match the live frame");
        assertTrue(claim != null && claim.isFound(), "Claim must match the live frame");
        RawImageData rawCapture = rawCapture(frame);
        assertTrue(OpenCvPatternLocator.locatePattern(rawCapture,
                TemplatesEnum.STOREHOUSE_STAMINA_REWARD_TITLE.getTemplate(),
                TITLE_AREA_TOP_LEFT, TITLE_AREA_BOTTOM_RIGHT, MATCH_THRESHOLD).isFound(),
                "The real-time raw screenshot path must detect the reward title");
        assertTrue(OpenCvPatternLocator.locatePattern(rawCapture,
                TemplatesEnum.STOREHOUSE_STAMINA_CLAIM_TEXT.getTemplate(),
                CLAIM_AREA_TOP_LEFT, CLAIM_AREA_BOTTOM_RIGHT, MATCH_THRESHOLD).isFound(),
                "The real-time raw screenshot path must detect Claim");
        assertFalse(OpenCvPatternLocator.locatePattern(rawCapture,
                TemplatesEnum.STOREHOUSE_STAMINA_TOOLTIP_TITLE.getTemplate(),
                TOOLTIP_TITLE_AREA_TOP_LEFT, TOOLTIP_TITLE_AREA_BOTTOM_RIGHT, MATCH_THRESHOLD).isFound());
    }

    @Test
    void identifiesTooltipAndRejectsClaimWhileTooltipCoversTheButton() throws IOException {
        for (String name : new String[]{
                "stamina-tooltip-obscures-claim.png",
                "stamina-tooltip-obscures-claim-repeat.png"}) {
            byte[] frame = encoded(name);

            assertTrue(matches(frame, TemplatesEnum.STOREHOUSE_STAMINA_TOOLTIP_TITLE,
                    TOOLTIP_TITLE_AREA_TOP_LEFT, TOOLTIP_TITLE_AREA_BOTTOM_RIGHT), name);
            assertFalse(matches(frame, TemplatesEnum.STOREHOUSE_STAMINA_CLAIM_TEXT,
                    CLAIM_AREA_TOP_LEFT, CLAIM_AREA_BOTTOM_RIGHT), name);
            assertFalse(matches(frame, TemplatesEnum.STOREHOUSE_STAMINA_REWARD_TITLE,
                    TITLE_AREA_TOP_LEFT, TITLE_AREA_BOTTOM_RIGHT), name);
        }
    }

    private static boolean matches(byte[] frame, TemplatesEnum template, PointData topLeft, PointData bottomRight) {
        ImageSearchResultData result = OpenCvPatternLocator.locatePattern(
                frame, template, topLeft, bottomRight, MATCH_THRESHOLD);
        return result != null && result.isFound();
    }

    private static RawImageData rawCapture(byte[] encoded) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(encoded));
        byte[] rgba = new byte[image.getWidth() * image.getHeight() * 4];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int index = (y * image.getWidth() + x) * 4;
                rgba[index] = (byte) (rgb >>> 16);
                rgba[index + 1] = (byte) (rgb >>> 8);
                rgba[index + 2] = (byte) rgb;
                rgba[index + 3] = (byte) 255;
            }
        }
        return RawImageData.capture(rgba, image.getWidth(), image.getHeight(), 32);
    }

    private byte[] encoded(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/storehouse/" + name)) {
            return Objects.requireNonNull(stream, "Missing Storehouse frame: " + name).readAllBytes();
        }
    }
}
