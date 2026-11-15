package dev.frostguard.tasks.dailies;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.vision.ocr.OcrEngine;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.Objects;
import javax.imageio.ImageIO;

class TriumphClaimVerificationTest {
    @Test void cannotTreatBothMissingTemplatesAsSuccess() {
        assertFalse(TriumphClaimVerification.dailyVerified(false, false, false, false));
    }
    @Test void clickingWithoutVerifiedClaimedStateIsStillUnresolved() {
        assertFalse(TriumphClaimVerification.dailyVerified(false, true, true, false));
        assertFalse(TriumphClaimVerification.dailyVerified(false, true, false, true));
    }
    @Test void alreadyClaimedOrNewlyVerifiedClaimedAreSafe() {
        assertTrue(TriumphClaimVerification.dailyVerified(true, false, false, false));
        assertTrue(TriumphClaimVerification.dailyVerified(false, true, true, true));
    }

    @Test void realCurrentChestScreenshotReadsFiftyOfEightHundred() throws Exception {
        byte[] snapshot = Objects.requireNonNull(getClass().getResourceAsStream(
                "/triumph/personal-chest-progress-50-of-800.jpg")).readAllBytes();
        assertTrue(snapshot.length > 100, "Fixture content missing");
        assertTrue(ImageIO.getImageReadersByFormatName("JPEG").hasNext(), "JPEG reader missing");
        BufferedImage png = ImageIO.read(new java.io.ByteArrayInputStream(snapshot));
        assertNotNull(png, "Could not decode real-frame JPEG badge fixture");
        byte[] rgba = new byte[png.getWidth() * png.getHeight() * 4];
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) {
            int color = png.getRGB(x, y), i = (y * png.getWidth() + x) * 4;
            rgba[i] = (byte)(color >> 16); rgba[i+1] = (byte)(color >> 8);
            rgba[i+2] = (byte)color; rgba[i+3] = (byte)255;
        }
        RawImageData frame = RawImageData.capture(rgba, png.getWidth(), png.getHeight(), 32);
        String text = OcrEngine.recognizeText(frame,
                new PointData(0,0), new PointData(png.getWidth(),png.getHeight()),
                OcrSettingsData.configurator()
                        .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)
                        .allowedGlyphs("0123456789/").build());
        assertEquals(new TriumphClaimVerification.ChestProgress(50, 800),
                TriumphClaimVerification.parseChestProgress(text), "Real image OCR: " + text);
    }

    @Test void unavailableChestCannotBeRecordedAsClaimed() {
        var progress = TriumphClaimVerification.parseChestProgress("50/800");
        assertEquals(TriumphClaimVerification.DailyState.NOT_READY,
                TriumphClaimVerification.dailyState(false, false, false, false, progress));
        assertEquals(TriumphClaimVerification.DailyState.UNVERIFIED,
                TriumphClaimVerification.dailyState(false, true, true, false, progress));
        assertFalse(TriumphClaimVerification.dailyVerified(false, false, false, false));
    }

    @Test void unreadableOrAmbiguousCounterCannotSuppressUnverifiedOutcome() {
        for (String malformed : new String[]{"", "50", "? / 800", "900/800", "0/0", "10/20", "501/800 time"}) {
            assertNull(TriumphClaimVerification.parseChestProgress(malformed), malformed);
        }
        assertEquals(TriumphClaimVerification.DailyState.UNVERIFIED,
                TriumphClaimVerification.dailyState(false, false, false, false, null));
        assertEquals(TriumphClaimVerification.DailyState.UNVERIFIED,
                TriumphClaimVerification.dailyState(false, false, false, false,
                        TriumphClaimVerification.parseChestProgress("800/800")));
    }

    @Test void notReadySchedulesProgressReviewBeforeNextDailyReset() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 15, 20, 45);
        LocalDateTime reset = LocalDateTime.of(2026, 11, 16, 1, 0);
        assertEquals(now.plusMinutes(240),
                TriumphClaimVerification.nextProgressReview(now, reset, 240));
        assertEquals(reset.plusMinutes(5),
                TriumphClaimVerification.nextProgressReview(now, reset, 1000));
    }
}
