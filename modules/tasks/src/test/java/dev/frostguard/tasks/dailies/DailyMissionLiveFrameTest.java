package dev.frostguard.tasks.dailies;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DailyMissionLiveFrameTest {
    private static final GreenLabelActionDetector.Region CLAIM_ALL =
            new GreenLabelActionDetector.Region(230, 985, 495, 1070);

    @BeforeAll
    static void loadOpenCv() throws Exception {
        OpenCvPatternLocator.loadNativeLibrary();
    }

    @Test
    void liveClaimAllIsPresentBeforeClaimAndAbsentAfterConfirmedReward() throws Exception {
        BufferedImage before = frame("/dailies/daily-live-masked-20261113.png");
        BufferedImage after = frame("/dailies/daily-post-masked-20261113.png");
        assertTrue(GreenLabelActionDetector.detect(before, CLAIM_ALL).isFound());
        assertFalse(GreenLabelActionDetector.detect(after, CLAIM_ALL).isFound());
    }

    @Test
    void dailyTitleRemainsVisibleAfterRewardDialogIsDismissed() throws Exception {
        for (String path : new String[] {
                "/dailies/daily-live-masked-20261113.png",
                "/dailies/daily-post-masked-20261113.png" }) {
            byte[] content = Objects.requireNonNull(getClass().getResourceAsStream(path)).readAllBytes();
            for (TemplatesEnum template : new TemplatesEnum[] {
                    TemplatesEnum.DAILY_MISSION_SCREEN_TITLE, TemplatesEnum.SERVE_EMU_DAILY_TITLE }) {
                assertTrue(OpenCvPatternLocator.locatePattern(content, template.getTemplate(),
                        new PointData(120, 80), new PointData(600, 225), 87).isFound(),
                        "Current Daily title must be detected before and after claim: " + path);
            }
        }
    }

    private static BufferedImage frame(String name) throws Exception {
        return ImageIO.read(Objects.requireNonNull(DailyMissionLiveFrameTest.class.getResource(name)));
    }
}
