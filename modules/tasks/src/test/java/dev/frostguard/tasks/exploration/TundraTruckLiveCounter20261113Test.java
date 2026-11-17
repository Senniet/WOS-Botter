package dev.frostguard.tasks.exploration;

import dev.frostguard.vision.ocr.OcrEngine;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TundraTruckLiveCounter20261113Test {
    @Test void parsesCounterAlongsideItsLabelWithoutSpendingTrucks() {
        var parsed = TundraTruckEventRoutine.parseTruckCount("Remaining Departures: 0/4");
        assertNotNull(parsed);
        assertEquals(0,parsed.remaining());
        assertEquals(4,parsed.total());
        assertNotNull(TundraTruckEventRoutine.parseTruckCount(" 0 / 4 "));
        assertNull(TundraTruckEventRoutine.parseTruckCount("Remaining Departures: ?/4"));
        assertNull(TundraTruckEventRoutine.parseTruckCount("Remaining Departures: 5/4"));
    }

    @Test void realCounterFromRecordedEmulatorScreenshotReadsZeroOfFour() throws Exception {
        var resource=Objects.requireNonNull(getClass().getResource(
                "/tundratruck/live-20261113/departures-bottom-redacted.png"));
        // The screen crop starts at source x=425,y=1120. The old OCR window
        // ended at x=527, cutting the last glyph of 0/4 at x~544.
        String read=OcrEngine.readFromFile(new java.io.File(resource.toURI()),
                58,22,95,40,"eng");
        var parsed=TundraTruckEventRoutine.parseTruckCount(read);
        assertNotNull(parsed,"Captured truck counter OCR was: " + read);
        assertEquals(0,parsed.remaining());
        assertEquals(4,parsed.total());
    }
}
