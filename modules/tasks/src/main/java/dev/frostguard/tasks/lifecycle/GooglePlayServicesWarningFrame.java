package dev.frostguard.tasks.lifecycle;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * Fail-closed detection of the exact Android system modal observed in Serve-Emu:
 * "Whiteout Survival won't run without Google Play services ..."
 *
 * Requires BOTH independent anchors from a single screenshot. Never click
 * generic system OK buttons, dialogs, billing prompts or unknown screens.
 */
final class GooglePlayServicesWarningFrame {
    private static final PointData TITLE_START = new PointData(45, 545);
    private static final PointData TITLE_END = new PointData(678, 760);
    private static final PointData BUTTON_START = new PointData(255, 754);
    private static final PointData BUTTON_END = new PointData(465, 865);

    private GooglePlayServicesWarningFrame() {}

    static ImageSearchResultData verifiedOk(RawImageData frame) {
        if (frame == null || !frame.isValid() || frame.getWidth() != 720 || frame.getHeight() != 1280)
            return ImageSearchResultData.miss();
        var title = OpenCvPatternLocator.locatePattern(
                frame, TemplatesEnum.SERVE_EMU_GMS_WARNING_TITLE_NOV_2026.getTemplate(),
                TITLE_START, TITLE_END, 93);
        if (!title.isFound()) return ImageSearchResultData.miss();
        var ok = OpenCvPatternLocator.locatePattern(
                frame, TemplatesEnum.SERVE_EMU_GMS_WARNING_OK_NOV_2026.getTemplate(),
                BUTTON_START, BUTTON_END, 93);
        if (!ok.isFound() || ok.getPoint() == null
                || ok.getPoint().getX() < 300 || ok.getPoint().getX() > 435
                || ok.getPoint().getY() < 775 || ok.getPoint().getY() > 845)
            return ImageSearchResultData.miss();
        return ok;
    }
}
