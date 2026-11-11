package dev.frostguard.tasks.economy;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class StorehouseModernSelectionFrameTest {
    @BeforeAll static void setup() throws Exception {
        try { OpenCvPatternLocator.loadNativeLibrary(); }
        catch (UnsatisfiedLinkError ignored) { }
    }
    private byte[] frame(String path) throws Exception {
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream(path))) {
            return input.readAllBytes();
        }
    }
    private boolean found(byte[] image,String pattern,PointData topLeft,PointData bottomRight) throws Exception {
        return OpenCvPatternLocator.locatePattern(image,pattern,topLeft,bottomRight,90).isFound();
    }
    @Test void storehouseRequiresNameAndDetailsAsIndependentEvidence() throws Exception {
        var image=frame("/storehouse/selected-modern-masked.png");
        assertTrue(found(image,"/templates/storehouse/selectedNameModern.png",
                new PointData(280,510),new PointData(500,580)));
        assertTrue(found(image,"/templates/storehouse/selectedDetailsModern.png",
                new PointData(290,835),new PointData(480,965)));
    }
    @Test void unrelatedScreenHasNeitherAnchor() throws Exception {
        var image=frame("/intel/journey-modern-masked.png");
        assertFalse(found(image,"/templates/storehouse/selectedNameModern.png",
                new PointData(280,510),new PointData(500,580)));
        assertFalse(found(image,"/templates/storehouse/selectedDetailsModern.png",
                new PointData(290,835),new PointData(480,965)));
    }
}
