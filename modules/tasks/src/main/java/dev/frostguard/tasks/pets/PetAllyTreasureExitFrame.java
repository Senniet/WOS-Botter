package dev.frostguard.tasks.pets;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * Exact November-2026 Ally Treasure panel X in the 720x1280 layout.
 * Requires both a matching title and a matching top-right X; never blindly
 * presses Back on an unknown screen.
 */
final class PetAllyTreasureExitFrame {
    private PetAllyTreasureExitFrame(){}

    static ImageSearchResultData verifiedClose(RawImageData frame) {
        if (frame == null || !frame.isValid()
                || frame.getWidth() != 720 || frame.getHeight() != 1280)
            return ImageSearchResultData.miss();

        ImageSearchResultData title = OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_PET_ALLY_TREASURE_TITLE_NOV_2026.getTemplate(),
                new PointData(190, 92), new PointData(535, 212), 90);
        if (!title.isFound()) return ImageSearchResultData.miss();
        ImageSearchResultData close = OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_PET_ALLY_TREASURE_CLOSE_NOV_2026.getTemplate(),
                new PointData(600, 112), new PointData(705, 213), 90);
        if (!close.isFound() || close.getPoint() == null
                || close.getPoint().getX() < 625 || close.getPoint().getX() > 690
                || close.getPoint().getY() < 122 || close.getPoint().getY() > 194)
            return ImageSearchResultData.miss();
        return close;
    }
}
