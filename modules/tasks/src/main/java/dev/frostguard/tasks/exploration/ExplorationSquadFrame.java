package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * A narrow 2026 screen-context gate: Squad Settings is NOT a Quick Exploration
 * confirmation, even if troop artwork contains adjacent orange/blue patches.
 * This method does not tap any action and does not replace the separate,
 * two-frame Fight/Quick Deploy verification before any button is used.
 */
final class ExplorationSquadFrame {
    private static final PointData TITLE_START = new PointData(70, 0);
    private static final PointData TITLE_END = new PointData(460, 165);
    private static final PointData FIGHT_START = new PointData(365, 1145);
    private static final PointData FIGHT_END = new PointData(710, 1275);
    private ExplorationSquadFrame() {}

    static boolean isSquadReady(RawImageData frame) {
        if (frame == null || !frame.isValid() || frame.getWidth() != 720
                || frame.getHeight() != 1280) return false;
        return OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_SQUAD_SETTINGS_TITLE.getTemplate(),
                TITLE_START, TITLE_END, 91).isFound()
            && OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.INTEL_JOURNEY_FIGHT_LIVE.getTemplate(),
                FIGHT_START, FIGHT_END, 92).isFound();
    }
}
