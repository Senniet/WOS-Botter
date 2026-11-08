package dev.frostguard.vision.ocr;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OcrEngineFrameValidationTest {
    @Test
    void acceptsValidRegionAndNormalizesCornerOrder() {
        RawImageData frame = RawImageData.capture(new byte[100 * 80 * 4], 100, 80, 32);
        assertArrayEquals(new int[]{10, 20, 30, 25}, OcrEngine.computeClipRect(
                new PointData(40, 45), new PointData(10, 20), frame));
    }

    @Test
    void rejectsZeroAreaAndNegativeRegions() {
        RawImageData frame = RawImageData.capture(new byte[100 * 80 * 4], 100, 80, 32);
        assertThrows(IllegalArgumentException.class, () -> OcrEngine.computeClipRect(
                new PointData(10, 10), new PointData(10, 40), frame));
        assertThrows(IllegalArgumentException.class, () -> OcrEngine.computeClipRect(
                new PointData(-1, 0), new PointData(10, 10), frame));
    }

    @Test
    void rejectsRegionsOutsideFrame() {
        RawImageData frame = RawImageData.capture(new byte[100 * 80 * 4], 100, 80, 32);
        assertThrows(IllegalArgumentException.class, () -> OcrEngine.computeClipRect(
                new PointData(90, 10), new PointData(110, 30), frame));
    }
}
