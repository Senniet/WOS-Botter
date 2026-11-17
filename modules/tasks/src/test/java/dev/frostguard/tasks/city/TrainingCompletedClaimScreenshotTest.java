package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/** Regression from live 720x1280 training diagnostic (private HUD masked). */
class TrainingCompletedClaimScreenshotTest {
    @BeforeAll static void loadCv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }
    @Test void completedInfantryBadgeIsFoundInActionRegionNotTextRegion() throws Exception {
        byte[] screenshot;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/serveemu/training-complete-sidebar-masked.png"))) {
            screenshot = input.readAllBytes();
        }
        String sprite = TemplatesEnum.SIDEBAR_CLAIM_ACTION.getTemplate();
        AreaData infantry = TrainingRoutine.sidebarClaimButtonArea(
                TrainingRoutine.TroopTypeShape.INFANTRY);
        ImageSearchResultData found = OpenCvPatternLocator.locatePattern(screenshot, sprite,
                infantry.topLeft(), infantry.bottomRight(), 87.0);
        assertTrue(found.isFound(), "Live screenshot has a visible green claim button; confidence="
                + found.getMatchScore());
        assertTrue(Math.abs(found.getPoint().getX() - 405) <= 15, "Unexpected X coordinate");
        assertTrue(Math.abs(found.getPoint().getY() - 558) <= 15, "Unexpected Y coordinate");
        // Previously used OCR band + 12px: it omitted the top of the 46px icon.
        ImageSearchResultData old = OpenCvPatternLocator.locatePattern(screenshot, sprite,
                new PointData(350, 578), new PointData(445, 640), 87.0);
        assertFalse(old.isFound(), "Old claim search unexpectedly finds the completed button");
        for (TrainingRoutine.TroopTypeShape other : new TrainingRoutine.TroopTypeShape[] {
                TrainingRoutine.TroopTypeShape.LANCER,
                TrainingRoutine.TroopTypeShape.MARKSMAN}) {
            AreaData region = TrainingRoutine.sidebarClaimButtonArea(other);
            ImageSearchResultData wrong = OpenCvPatternLocator.locatePattern(screenshot, sprite,
                    region.topLeft(), region.bottomRight(), 87.0);
            assertFalse(wrong.isFound(), "Claim button must not be attributed to " + other);
        }
    }
}
