package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class IntelRescueJourneyModernFrameTest {
    @BeforeAll static void setup() throws Exception {
        try { OpenCvPatternLocator.loadNativeLibrary(); }
        catch (UnsatisfiedLinkError ignored) { }
    }
    private byte[] frame(String path) throws Exception {
        try (var input=Objects.requireNonNull(getClass().getResourceAsStream(path))) {
            return input.readAllBytes();
        }
    }
    private boolean found(byte[] image, String template) throws Exception {
        return OpenCvPatternLocator.locatePattern(image,template,
                new PointData(220,565),new PointData(510,690),89).isFound();
    }
    @Test void rescueAndExploreAreDifferentAndPresentOnOnlyTheMatchingMission() throws Exception {
        byte[] rescue=frame("/intel/rescue-modern-masked.png");
        byte[] journey=frame("/intel/journey-modern-masked.png");
        assertTrue(found(rescue,"/templates/intel/survivorRescueModern.png"));
        assertFalse(found(rescue,"/templates/intel/journeyExploreModern.png"));
        assertTrue(found(journey,"/templates/intel/journeyExploreModern.png"));
        assertFalse(found(journey,"/templates/intel/survivorRescueModern.png"));
    }
    @Test void neitherButtonAppearsOnFormationScreen() throws Exception {
        byte[] other=frame("/intel/deploy-modern-masked.png");
        assertFalse(found(other,"/templates/intel/journeyExploreModern.png"));
        assertFalse(found(other,"/templates/intel/survivorRescueModern.png"));
    }
}
