package dev.frostguard.engine.helper;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * The Storehouse A Warm Welcome stamina dialog is a full-screen overlay.
 * Only a positive title + white X on one screenshot can authorize a potential
 * dismissal. Never infer an actionable Claim from generic green pixels.
 */
public final class WarmWelcomeOverlayFrame {
    private static final PointData TITLE_FROM = new PointData(150, 740);
    private static final PointData TITLE_TO = new PointData(560, 885);
    private static final PointData X_FROM = new PointData(560, 135);
    private static final PointData X_TO = new PointData(665, 235);

    private WarmWelcomeOverlayFrame() {}

    public static ImageSearchResultData verifiedClose(RawImageData frame) {
        if (frame == null || !frame.isValid()
                || frame.getWidth() != 720 || frame.getHeight() != 1280) {
            return ImageSearchResultData.miss();
        }
        var title = OpenCvPatternLocator.locatePattern(
                frame, TemplatesEnum.STOREHOUSE_STAMINA_REWARD_TITLE.getTemplate(),
                TITLE_FROM, TITLE_TO, 88);
        if (!title.isFound()) return ImageSearchResultData.miss();
        return OpenCvPatternLocator.locatePattern(
                frame, TemplatesEnum.STOREHOUSE_STAMINA_REWARD_CLOSE.getTemplate(),
                X_FROM, X_TO, 85);
    }
}
