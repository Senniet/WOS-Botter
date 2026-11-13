package dev.frostguard.tasks.events;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.BlueLabelActionDetector;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class November2026EventScreenRecognitionTest {
    @BeforeAll static void loadCv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void currentPowerCastDialogLocatesNormalAndUnselectedItems() throws Exception {
        var screen = raw(image("/fishing/power-cast-november.png"));
        var normal = FishingMinigameRoutine.normalCastControl(screen, false);
        assertTrue(normal.isFound(), "Current Normal Cast art and no-special-items text should be recognized");
        assertTrue(normal.getHitX() < 350, "Must never target orange 2x Power Cast");
        assertFalse(FishingMinigameRoutine.normalCastControl(
                raw(image("/fishing/event-current.png")), false).isFound());
    }

    @Test void fishingDialogueRequiresTheVisibleContinueInstruction() throws Exception {
        assertTrue(FishingMinigameRoutine.fishingContinueOverlay(
                raw(image("/fishing/tap-anywhere-continue-november.png"))),
                "The actual post-round Fishing dialogue should be actionable");
        assertFalse(FishingMinigameRoutine.fishingContinueOverlay(
                raw(image("/fishing/event-current.png"))),
                "Ordinary Fishing overview must not be tapped blindly");
    }

    @Test void currentlyVisibleFishingResumeIsOnlyAcceptedOnTheEventScreen() throws Exception {
        BufferedImage page = image("/fishing/go-fish-november-masked.png");
        var control = FishingRetryNavigator.goFishControl(raw(page));
        assertTrue(control.isFound(), "Existing paid stage should be recoverable");
        assertTrue(control.getHitX() >= 300 && control.getHitX() <= 430);
        assertTrue(control.getHitY() >= 1050 && control.getHitY() <= 1125);
        assertFalse(FishingRetryNavigator.goFishControl(raw(image("/fishing/event-current.png"))).isFound(),
                "Regular Fishing overview must not be mistaken for a resumed stage");
    }

    @Test void revisedButtonArtStillAllowsResumeOnlyWithMatchingTitle() throws Exception {
        BufferedImage page = image("/fishing/go-fish-november-masked.png");
        var g = page.createGraphics();
        try {
            g.setColor(new Color(55, 170, 245));
            g.fillRect(290, 1060, 150, 52);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
            g.drawString("Go Fish", 305, 1096);
        } finally { g.dispose(); }
        var originalTemplate = match(raw(page), TemplatesEnum.FISHING_GO_FISH, 270, 1040, 465, 1130, 85);
        assertFalse(originalTemplate.isFound(), "Variant must exercise fallback rather than the old template");
        assertTrue(FishingRetryNavigator.goFishControl(raw(page)).isFound(),
                "White-labelled blue button plus verified title should survive an artwork change");
        // Without the event title the blue button is not proof of a paid stage.
        g = page.createGraphics();
        try { g.setColor(Color.BLACK); g.fillRect(75, 0, 430, 95); }
        finally { g.dispose(); }
        assertFalse(FishingRetryNavigator.goFishControl(raw(page)).isFound());
    }

    @Test void exploreAndIdleIncomeControlsAreFoundOnRealFrames() throws Exception {
        BufferedImage explore = image("/exploration/explore-november-masked.png");
        assertTrue(match(raw(explore), TemplatesEnum.EXPLORATION_SCREEN_TITLE,
                70, 5, 365, 90, 90).isFound());
        assertTrue(match(raw(explore), TemplatesEnum.EXPLORATION_BUTTON,
                160, 1100, 540, 1230, 86).isFound());
        assertTrue(BlueLabelActionDetector.detect(explore,
                new BlueLabelActionDetector.Region(214, 1139, 507, 1219, 292, 1156, 435, 1196)).isFound());
        BufferedImage income = image("/exploration/idle-income-november-masked.png");
        assertTrue(match(raw(income), TemplatesEnum.EXPLORATION_IDLE_INCOME_TITLE,
                140, 220, 550, 370, 88).isFound());
        assertTrue(GreenLabelActionDetector.detect(income,
                new GreenLabelActionDetector.Region(213, 875, 507, 972)).isFound());
    }

    @Test void verifiedHeaderBackArrowIsCommonAcrossEventPages() throws Exception {
        for (String path : new String[]{"/fishing/go-fish-november-masked.png",
                "/exploration/explore-november-masked.png"}) {
            assertTrue(match(raw(image(path)), TemplatesEnum.GAME_HEADER_BACK_ARROW_CURRENT,
                    0, 0, 110, 105, 91).isFound(), path);
        }
    }

    @Test void rewardsOverlayIsFoundInBothActualCapturesButNotOnExplorationPage() throws Exception {
        for (String name : new String[]{"rewards-november-a-masked", "rewards-november-b-masked"}) {
            assertTrue(match(raw(image("/exploration/" + name + ".png")),
                    TemplatesEnum.SHARED_REWARDS_BANNER_CURRENT, 50, 245, 670, 400, 92).isFound(), name);
        }
        assertFalse(match(raw(image("/exploration/explore-november-masked.png")),
                TemplatesEnum.SHARED_REWARDS_BANNER_CURRENT, 50, 245, 670, 400, 92).isFound());
    }

    private static dev.frostguard.api.domain.ImageSearchResultData match(RawImageData frame,
            TemplatesEnum tpl, int x1, int y1, int x2, int y2, int threshold) {
        return OpenCvPatternLocator.locatePattern(frame, tpl.getTemplate(),
                new PointData(x1,y1), new PointData(x2,y2), threshold);
    }

    private static BufferedImage image(String path) throws IOException {
        return ImageIO.read(Objects.requireNonNull(November2026EventScreenRecognitionTest.class.getResource(path)));
    }

    private static RawImageData raw(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        byte[] bytes = new byte[width * height * 4];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int rgb = image.getRGB(x,y), at = (y * width + x) * 4;
            bytes[at] = (byte)(rgb >>> 16);
            bytes[at+1] = (byte)(rgb >>> 8);
            bytes[at+2] = (byte)rgb;
            bytes[at+3] = (byte)255;
        }
        return RawImageData.capture(bytes, width, height, 32);
    }
}
