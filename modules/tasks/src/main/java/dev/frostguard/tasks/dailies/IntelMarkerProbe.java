package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * Passive, single captured-frame Intel MARKER detector for Serve-Emu.
 *
 * A nonexistent Beast/Survivor/Journey marker is not an error: the game
 * commonly shows only a subset of these variants. Search all variants
 * against one immutable screenshot rather than scheduling several network
 * screenshots and recording each absent variant as a failed action.
 *
 * These results are observations, NEVER independent authorization to tap.
 * Callers must confirm a candidate on a different fresh frame.
 */
final class IntelMarkerProbe {
    private IntelMarkerProbe() {}

    static ImageSearchResultData find(RawImageData frame, TemplatesEnum marker, SearchConfig cfg) {
        if (frame == null || !frame.isValid() || marker == null || cfg == null
                || frame.getWidth() <= 0 || frame.getHeight() <= 0)
            return ImageSearchResultData.miss();
        PointData start = cfg.hasArea() ? cfg.getArea().topLeft()
                : cfg.hasCoordinates() ? cfg.getStartPoint() : new PointData(0,0);
        PointData end = cfg.hasArea() ? cfg.getArea().bottomRight()
                : cfg.hasCoordinates() ? cfg.getEndPoint()
                : new PointData(frame.getWidth(),frame.getHeight());
        return OpenCvPatternLocator.locatePatternMono(frame, marker.getTemplate(),
                start, end, cfg.getThreshold());
    }
}
