package dev.frostguard.vision.ocr;

import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.OcrSettingsData;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import java.util.function.Predicate;
import dev.frostguard.vision.ocr.OcrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a text extraction function with configurable retry semantics.
 *
 * <p>On each attempt the engine reads the given screen region; a caller-
 * supplied predicate decides whether the raw text is acceptable.  When the
 * predicate passes, the text is mapped to the desired return type via a
 * converter function.  If every attempt fails, {@code null} is returned.
 *
 * @param <R> the type the recognised text is converted into on success
 */
public class ResilientOcrExecutor<R> {

    private static final Logger log = LoggerFactory.getLogger(ResilientOcrExecutor.class);

    /**
     * Functional interface for text extraction operations.
     * Abstracts the actual OCR implementation details.
     */
    @FunctionalInterface
    public interface TextExtractor {
        /**
         * Extracts text from a rectangular region.
         *
         * @param config      engine configuration (may be null)
         * @param topLeft     upper-left corner
         * @param bottomRight lower-right corner
         * @return recognized text, never null
         * @throws IOException        if capture fails
         * @throws OcrException if recognition fails
         */
        String extractText(OcrSettingsData config, PointData topLeft, PointData bottomRight)
                throws IOException, OcrException;
    }

    private final TextExtractor textExtractor;

    /**
     * @param textExtractor the recognition back-end to delegate to
     */
    public ResilientOcrExecutor(TextExtractor textExtractor) {
        this.textExtractor = Objects.requireNonNull(textExtractor, "textExtractor required");
    }

    /**
     * Attempts recognition up to {@code maxAttempts} times with a fixed
     * inter-attempt pause.
     *
     * @param upperLeft    upper-left corner of the capture area
     * @param lowerRight   lower-right corner of the capture area
     * @param maxAttempts  total number of tries before giving up
     * @param pauseMillis  wait between consecutive attempts
     * @param config       engine parameters (may be {@code null})
     * @param acceptor     predicate that returns {@code true} when the
     *                     raw text is good enough
     * @param transformer  maps accepted text into the return type
     * @return the transformed value, or {@code null} if all attempts fail
     */
    public enum Status { ACCEPTED, REJECTED, FAILED }

    /** Explicit OCR outcome; callers can distinguish an unreadable frame from valid non-matching text. */
    public record RecognitionResult<R>(Status status, R value, String rawText,
            int attempts, String failure) {
        public boolean accepted() { return status == Status.ACCEPTED; }
    }

    public R attemptRecognition(PointData upperLeft,
                                PointData lowerRight,
                                int maxAttempts,
                                long pauseMillis,
                                OcrSettingsData config,
                                Predicate<String> acceptor,
                                Function<String, R> transformer) {
        return attemptRecognitionDetailed(upperLeft, lowerRight, maxAttempts, pauseMillis,
                config, acceptor, transformer).value();
    }

    /**
     * Performs bounded recognition while retaining the reason it did not produce
     * an accepted value. Predicate/transformer bugs are intentionally allowed to
     * propagate rather than being misreported as an OCR miss.
     */
    public RecognitionResult<R> attemptRecognitionDetailed(PointData upperLeft,
                                PointData lowerRight,
                                int maxAttempts,
                                long pauseMillis,
                                OcrSettingsData config,
                                Predicate<String> acceptor,
                                Function<String, R> transformer) {
        Objects.requireNonNull(upperLeft, "upperLeft");
        Objects.requireNonNull(lowerRight, "lowerRight");
        Objects.requireNonNull(acceptor, "acceptor");
        Objects.requireNonNull(transformer, "transformer");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be at least 1");
        if (pauseMillis < 0) throw new IllegalArgumentException("pauseMillis must not be negative");

        String lastRaw = null;
        String lastFailure = null;
        boolean receivedText = false;
        for (int trial = 0; trial < maxAttempts; trial++) {
            lastRaw = null;
            checkCancellation();
            log.debug("OCR trial {} / {}", trial + 1, maxAttempts);
            try {
                lastRaw = textExtractor.extractText(config, upperLeft, lowerRight);
                checkCancellation();
            } catch (CancellationException exception) {
                throw exception;
            } catch (IOException | OcrException | RuntimeException exception) {
                lastFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                log.warn("OCR trial {} failed: {}", trial + 1, lastFailure);
            }
            if (lastRaw != null && !lastRaw.isBlank()) {
                receivedText = true;
                if (acceptor.test(lastRaw)) {
                    R value = transformer.apply(lastRaw);
                    log.info("=== OCR Completed === accepted text='{}' after {} attempt(s)",
                            lastRaw.trim().replace("\n", " "), trial + 1);
                    return new RecognitionResult<>(Status.ACCEPTED, value, lastRaw, trial + 1, null);
                }
            }

            if (trial < maxAttempts - 1) {
                try {
                    Thread.sleep(pauseMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("OCR retry interrupted");
                }
            }
        }
        Status status = receivedText ? Status.REJECTED : Status.FAILED;
        return new RecognitionResult<>(status, null, lastRaw, maxAttempts, lastFailure);
    }

    private static void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("OCR cancelled");
        }
    }

    /**
     * Convenience overload that unpacks a {@link AreaData} into its
     * corner coordinates before delegating.
     */
    public R attemptRecognition(AreaData area,
                                int maxAttempts,
                                long pauseMillis,
                                OcrSettingsData config,
                                Predicate<String> acceptor,
                                Function<String, R> transformer) {
        return attemptRecognition(
                area.topLeft(), area.bottomRight(),
                maxAttempts, pauseMillis, config, acceptor, transformer);
    }
}
