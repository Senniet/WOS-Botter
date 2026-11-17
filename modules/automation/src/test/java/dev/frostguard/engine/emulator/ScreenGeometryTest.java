package dev.frostguard.engine.emulator;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScreenGeometryTest {
    @Test void normalizesBlueStacks900x1600AndMapsInputBack() {
        byte[] rgba = new byte[900 * 1600 * 4];
        // Fixed-color image to verify the channel layout survives resizing.
        for (int i = 0; i < rgba.length; i += 4) {
            rgba[i] = 13; rgba[i + 1] = 45; rgba[i + 2] = 67; rgba[i + 3] = (byte)255;
        }
        RawImageData original = RawImageData.capture(rgba, 900, 1600, 32);
        ScreenGeometry geometry = ScreenGeometry.forPortraitCapture(original);
        assertNotNull(geometry);
        RawImageData adjusted = geometry.normalize(original);
        assertEquals(720, adjusted.getWidth());
        assertEquals(1280, adjusted.getHeight());
        assertTrue(adjusted.isValid());
        assertEquals(13, adjusted.getData()[0] & 255);
        assertEquals(45, adjusted.getData()[1] & 255);
        assertEquals(67, adjusted.getData()[2] & 255);
        PointData center = geometry.toDevice(new PointData(360, 640));
        assertEquals(450, center.getX());
        assertEquals(800, center.getY());
    }

    @Test void doesNotResizeReferenceOrLandscape() {
        RawImageData reference = RawImageData.capture(new byte[720 * 1280 * 4], 720, 1280, 32);
        ScreenGeometry geometry = ScreenGeometry.forPortraitCapture(reference);
        assertNotNull(geometry);
        assertSame(reference, geometry.normalize(reference));
        assertNull(ScreenGeometry.forPortraitCapture(
            RawImageData.capture(new byte[1600 * 900 * 4], 1600, 900, 32)));
    }

    @Test void clampsEdgeCoordinatesAndSupportsRgb565() {
        ScreenGeometry geometry = ScreenGeometry.forPortraitCapture(
            RawImageData.capture(new byte[900 * 1600 * 2], 900, 1600, 16));
        assertNotNull(geometry);
        PointData edge = geometry.toDevice(new PointData(720, 1280));
        assertEquals(899, edge.getX());
        assertEquals(1599, edge.getY());
    }
}
