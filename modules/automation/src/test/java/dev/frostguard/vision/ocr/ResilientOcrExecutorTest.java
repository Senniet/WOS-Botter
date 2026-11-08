package dev.frostguard.vision.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;

import dev.frostguard.api.domain.PointData;

class ResilientOcrExecutorTest {

    @Test
    void rejectsRecognitionWhenCallingThreadIsAlreadyInterrupted() {
        ResilientOcrExecutor<String> executor = new ResilientOcrExecutor<>((config, topLeft, bottomRight) -> "42");
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> executor.attemptRecognition(
                    new PointData(0, 0),
                    new PointData(10, 10),
                    1,
                    0,
                    null,
                    text -> true,
                    text -> text));
        } finally {
            Thread.interrupted();
        }
    }
    @Test
    void detailedResultDistinguishesRejectedTextFromOcrFailure() {
        ResilientOcrExecutor<String> rejected = new ResilientOcrExecutor<>((config, topLeft, bottomRight) -> "??");
        var rejectedResult = rejected.attemptRecognitionDetailed(new PointData(0, 0), new PointData(10, 10),
                2, 0, null, text -> text.matches("\\d+"), text -> text);
        assertEquals(ResilientOcrExecutor.Status.REJECTED, rejectedResult.status());
        assertEquals("??", rejectedResult.rawText());
        assertEquals(2, rejectedResult.attempts());

        ResilientOcrExecutor<String> failed = new ResilientOcrExecutor<>((config, topLeft, bottomRight) -> {
            throw new OcrException("native engine unavailable");
        });
        var failedResult = failed.attemptRecognitionDetailed(new PointData(0, 0), new PointData(10, 10),
                1, 0, null, text -> true, text -> text);
        assertEquals(ResilientOcrExecutor.Status.FAILED, failedResult.status());
        assertNull(failedResult.value());
        assertEquals("OcrException: native engine unavailable", failedResult.failure());
    }

    @Test
    void doesNotSwallowCallerPredicateBugs() {
        ResilientOcrExecutor<String> executor = new ResilientOcrExecutor<>((config, topLeft, bottomRight) -> "42");
        assertThrows(IllegalStateException.class, () -> executor.attemptRecognitionDetailed(
                new PointData(0, 0), new PointData(10, 10), 1, 0, null,
                text -> { throw new IllegalStateException("predicate bug"); }, text -> text));
    }

    @Test
    void rejectsInvalidRetryConfiguration() {
        ResilientOcrExecutor<String> executor = new ResilientOcrExecutor<>((config, topLeft, bottomRight) -> "42");
        assertThrows(IllegalArgumentException.class, () -> executor.attemptRecognitionDetailed(
                new PointData(0, 0), new PointData(10, 10), 0, 0, null, text -> true, text -> text));
    }

}
