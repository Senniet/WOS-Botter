package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.tasks.diagnostics.TaskControlSignals;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.vision.ocr.OcrEngine;
import dev.frostguard.vision.ocr.OcrException;
import java.util.Locale;

/** Read-only recognition of the expanded Daily sidebar from live Serve-Emu frames. */
public final class DailyOverviewFrame {
    private static final OcrSettingsData TEXT = OcrSettingsData.configurator()
            .textLayout(OcrSettingsData.TextLayout.TEXT_BLOCK).build();
    private DailyOverviewFrame() {}
    public record Summary(boolean arenaVisible, boolean petAdventureVisible,
                          boolean allianceContributionVisible, boolean lighthouseVisible, String text) {}

    public static boolean isDailyOverview(RawImageData frame) {
        if (frame == null || !frame.isValid() || frame.getWidth() != 720 || frame.getHeight() != 1280)
            return false;
        return OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SIDEBAR_DAILY_ARENA.getTemplate(),
                new PointData(0, 265), new PointData(215, 485), 86).isFound()
                || OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SIDEBAR_DAILY_PET_ADVENTURE.getTemplate(),
                new PointData(0, 340), new PointData(215, 615), 86).isFound();
    }

    /** One OCR of the real Daily sidebar serves both reporting and smart dispatch. */
    static String readVisibleText(RawImageData frame) {
        if (!isDailyOverview(frame)) return "";
        try {
            return OcrEngine.recognizeText(frame,
                    new PointData(15,277),new PointData(465,980),TEXT);
        } catch (OcrException expected) {
            return "";
        } catch (RuntimeException unexpected) {
            TaskControlSignals.rethrowControlSignal(unexpected);
            return "";
        }
    }

    public static Summary summarize(RawImageData frame) {
        if (!isDailyOverview(frame)) return new Summary(false,false,false,false,"Unverified Daily sidebar");
        String text = readVisibleText(frame);
        String normalized = text.toUpperCase(Locale.ROOT);
        boolean arena = normalized.contains("ARENA");
        boolean pet = normalized.contains("PET ADVENTURE");
        boolean alliance = normalized.contains("ALLIANCE CONTRIBUTION");
        boolean intel = normalized.contains("LIGHTHOUSE");
        return new Summary(arena, pet, alliance, intel,
                "Arena="+arena+", Pet Adventure="+pet+", Alliance Contribution="+alliance+
                        ", Lighthouse="+intel+"; visible Daily entries only; actions owned by individual routines");
    }
}
