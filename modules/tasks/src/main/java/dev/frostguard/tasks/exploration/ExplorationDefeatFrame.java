package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * 2026 normal Exploration Defeat screen: only the real Return to City CTA
 * is actionable. Never press Retry, Enhance Hero or an arbitrary blue button.
 * Source: 720x1280 ADB frame, 2026-11-18, with player data redacted in tests.
 */
final class ExplorationDefeatFrame {
    private ExplorationDefeatFrame() {}

    static boolean visible(RawImageData frame) {
        if (frame == null || !frame.isValid() || frame.getWidth() != 720
                || frame.getHeight() != 1280) return false;
        return OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.EXPLORATION_DEFEAT.getTemplate(),
                new PointData(130,280),new PointData(625,510),90).isFound();
    }

    static ImageSearchResultData returnToCity(RawImageData frame) {
        if (!visible(frame)) return ImageSearchResultData.miss();
        return OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_EXPLORATION_DEFEAT_RETURN_CITY_2026.getTemplate(),
                new PointData(12,855),new PointData(380,1040),89);
    }
}
