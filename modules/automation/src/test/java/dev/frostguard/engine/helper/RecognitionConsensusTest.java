package dev.frostguard.engine.helper;

import dev.frostguard.api.domain.ImageSearchResultData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecognitionConsensusTest {
    @Test void confirmsStableTarget() {
        assertTrue(RecognitionConsensus.agrees(
                ImageSearchResultData.hit(200, 400, 94, 90, 38),
                ImageSearchResultData.hit(204, 403, 95, 92, 38), 18));
    }
    @Test void rejectsShiftingTarget() {
        assertFalse(RecognitionConsensus.agrees(
                ImageSearchResultData.hit(200, 400, 94),
                ImageSearchResultData.hit(280, 403, 95), 18));
    }
    @Test void rejectsChangingScaleAndMisses() {
        assertFalse(RecognitionConsensus.agrees(
                ImageSearchResultData.hit(200, 400, 94, 90, 38),
                ImageSearchResultData.hit(200, 400, 95, 140, 38), 18));
        assertFalse(RecognitionConsensus.agrees(ImageSearchResultData.miss(),
                ImageSearchResultData.hit(200, 400, 95), 18));
        assertFalse(RecognitionConsensus.agrees(null, null, 18));
    }
    @Test void rejectsInvalidScoresAndNegativeDistance() {
        assertFalse(RecognitionConsensus.agrees(
                ImageSearchResultData.hit(200, 400, Double.NaN),
                ImageSearchResultData.hit(200, 400, 95), 18));
        assertFalse(RecognitionConsensus.agrees(
                ImageSearchResultData.hit(200, 400, 95),
                ImageSearchResultData.hit(200, 400, 95), -1));
    }
}
