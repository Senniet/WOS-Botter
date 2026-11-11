package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class IntelRescueJourneySafetyTest {
    @BeforeAll
    static void initializeMatcher() throws Exception {
        try { OpenCvPatternLocator.loadNativeLibrary(); }
        catch (UnsatisfiedLinkError ignored) { }
    }

    @Test
    void quitPromptCanBeRecognizedFromRealSanitizedGameScreenshot() throws Exception {
        byte[] frame;
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream(
                "/navigation/quit-game-masked.png"))) { frame = stream.readAllBytes(); }
        var hit = OpenCvPatternLocator.locatePattern(frame,
                "/templates/navigation/quitGamePrompt.png", new PointData(250,525),
                new PointData(470,645), 88);
        assertTrue(hit.isFound(), "Quit confirmation must be recognized before sending Back");
        assertEquals(362, hit.getPoint().getX());
    }

    @Test
    void quitPromptIsAbsentFromIntelDeploymentScreen() throws Exception {
        byte[] frame;
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream(
                "/intel/deploy-modern-masked.png"))) { frame = stream.readAllBytes(); }
        assertFalse(OpenCvPatternLocator.locatePattern(frame,
                "/templates/navigation/quitGamePrompt.png", new PointData(250,525),
                new PointData(470,645), 88).isFound());
    }

    @Test
    void rescueAndJourneySearchesUseConservativeTwoFrameMatching() {
        var rescue = IntelligenceRoutine.INTEL_SURVIVOR_ACTION_SEARCH;
        var journey = IntelligenceRoutine.INTEL_JOURNEY_ACTION_SEARCH;
        assertEquals(86, rescue.getThreshold());
        assertEquals(86, journey.getThreshold());
        assertEquals(3, rescue.getMaxAttempts());
        assertEquals(3, journey.getMaxAttempts());
    }
}
