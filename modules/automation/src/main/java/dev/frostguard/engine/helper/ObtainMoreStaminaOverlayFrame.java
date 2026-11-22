package dev.frostguard.engine.helper;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * 720x1280 "Obtain more" for Chief Stamina. This page often survives a
 * finished task and hides both Home/World anchors. Strict title+X only:
 * never authorize purchases, generic green buttons or blind Android Back.
 */
public final class ObtainMoreStaminaOverlayFrame {
    private static final PointData TITLE_START = new PointData(100, 105);
    private static final PointData TITLE_END = new PointData(650, 310);
    private static final PointData CLOSE_START = new PointData(637, 104);
    private static final PointData CLOSE_END = new PointData(700, 174);

    private ObtainMoreStaminaOverlayFrame() { }

    public static ImageSearchResultData verifiedClose(RawImageData frame) {
        if (frame == null || !frame.isValid()
                || frame.getWidth() != 720 || frame.getHeight() != 1280) {
            return ImageSearchResultData.miss();
        }
        var title = OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_STAMINA_TITLE.getTemplate(),
                TITLE_START, TITLE_END, 90);
        if (!title.isFound()) return ImageSearchResultData.miss();
        // "Obtain more" is also used for Iron, Coal and other resource
        // purchases! Require the first-row Chief Stamina label, so this
        // recovery never closes a resource purchase screen by mistake.
        var stamina=OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_CHIEF_STAMINA_LABEL_2026.getTemplate(),
                new PointData(155, 185), new PointData(385, 253), 89);
        if (!stamina.isFound()) return ImageSearchResultData.miss();
        return OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_STAMINA_OBTAIN_MORE_CLOSE_2026.getTemplate(),
                CLOSE_START, CLOSE_END, 89);
    }
}
