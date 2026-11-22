package dev.frostguard.tasks.pets;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.tasks.diagnostics.TaskControlSignals;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.vision.ocr.OcrEngine;
import dev.frostguard.vision.ocr.OcrException;

/** Close only the positively identified Select Pet panel, never the unknown game UI. */
final class PetAdventureSelectPetFrame {
    private static final PointData TITLE_TOP_LEFT = new PointData(180, 175);
    private static final PointData TITLE_BOTTOM_RIGHT = new PointData(510, 245);
    private static final PointData ATTEMPT_TOP_LEFT = new PointData(100, 930);
    private static final PointData ATTEMPT_BOTTOM_RIGHT = new PointData(650, 1100);
    // Actual 2026 720x1280 emulator: Select Pet close X at (665, 219).
    private static final PointData CLOSE_X = new PointData(665, 220);
    private static final PointData TREASURE_SPOT_X = new PointData(644, 212);
    private static final PointData TREASURE_TITLE_TOP_LEFT = new PointData(235, 170);
    private static final PointData TREASURE_TITLE_BOTTOM_RIGHT = new PointData(540, 265);
    private static final OcrSettingsData TITLE_OCR = OcrSettingsData.assembler()
            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build();

    private PetAdventureSelectPetFrame() {}

    static boolean isSelectPet(RawImageData frame) {
        if (frame == null || !frame.isValid() || frame.getWidth() != 720
                || frame.getHeight() != 1280) return false;
        try {
            String title = OcrEngine.recognizeText(frame, TITLE_TOP_LEFT, TITLE_BOTTOM_RIGHT, TITLE_OCR);
            return "SELECT PET".equals(title.toUpperCase(Locale.ROOT)
                    .replaceAll("[^A-Z ]", " ").replaceAll(" +", " ").trim());
        } catch (OcrException unavailable) {
            return false;
        } catch (RuntimeException failure) {
            TaskControlSignals.rethrowControlSignal(failure);
            return false;
        }
    }

    static boolean isNoAttempts(RawImageData frame) {
        return isSelectPet(frame) && OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.PETS_CHEST_ATTEMPT.getTemplate(),
                ATTEMPT_TOP_LEFT, ATTEMPT_BOTTOM_RIGHT, 90).isFound();
    }

    static boolean isTreasureSpot(RawImageData frame) {
        return frame != null && frame.isValid() && frame.getWidth() == 720
                && frame.getHeight() == 1280
                && OpenCvPatternLocator.locatePattern(frame,
                        TemplatesEnum.PET_ADVENTURE_TREASURE_SPOT_TITLE_NOV_2026.getTemplate(),
                        TREASURE_TITLE_TOP_LEFT, TREASURE_TITLE_BOTTOM_RIGHT, 90).isFound();
    }

    enum CloseResult { NOT_PRESENT, CLOSED_TO_MAP, UNVERIFIED }

    static CloseResult closeIfVisible(Supplier<RawImageData> capture,
            Consumer<PointData> tap, Runnable settle) {
        if (!isSelectPet(capture.get())) return CloseResult.NOT_PRESENT;
        settle.run();
        if (!isSelectPet(capture.get())) return CloseResult.UNVERIFIED;
        tap.accept(CLOSE_X);
        settle.run();
        RawImageData after = capture.get();
        if (PetAdventureMapFrame.isMap(after)) return CloseResult.CLOSED_TO_MAP;
        // Select Pet is layered over Treasure Spot. Closing only the outer
        // Select Pet window leaves the underlying spot dialog obstructing
        // the next task. Verify the named spot header twice before its X.
        if (!isTreasureSpot(after)) return CloseResult.UNVERIFIED;
        settle.run();
        if (!isTreasureSpot(capture.get())) return CloseResult.UNVERIFIED;
        tap.accept(TREASURE_SPOT_X);
        settle.run();
        return PetAdventureMapFrame.isMap(capture.get())
                ? CloseResult.CLOSED_TO_MAP : CloseResult.UNVERIFIED;
    }
}
