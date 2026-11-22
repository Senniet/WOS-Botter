package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * Safe Fight detector for the modern Intel Hero's Journey.
 *
 * Only the precise green Fight button on the verified Squad Settings screen
 * may be clicked. An Explore button disappearing is not proof of failure:
 * the Squad Settings transition can already have finished while generic
 * action acknowledgement still reads another matching UI element.
 */
final class IntelJourneyFightStageFrame {
    private static final PointData TITLE_START = new PointData(0, 0);
    private static final PointData TITLE_END = new PointData(350, 155);
    private static final PointData FIGHT_START = new PointData(365, 1145);
    private static final PointData FIGHT_END = new PointData(705, 1275);

    private IntelJourneyFightStageFrame() {}

    static ImageSearchResultData verifiedFight(RawImageData frame) {
        if (frame == null || !frame.isValid()
                || frame.getWidth() != 720 || frame.getHeight() != 1280)
            return ImageSearchResultData.miss();
        var title = OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_SQUAD_SETTINGS_TITLE.getTemplate(),
                TITLE_START, TITLE_END, 90);
        if (!title.isFound()) return ImageSearchResultData.miss();
        var button = OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.INTEL_JOURNEY_FIGHT_LIVE.getTemplate(),
                FIGHT_START, FIGHT_END, 90);
        if (!button.isFound() || button.getPoint() == null
                || button.getPoint().getX() < 445 || button.getPoint().getX() > 630
                || button.getPoint().getY() < 1170 || button.getPoint().getY() > 1240)
            return ImageSearchResultData.miss();
        return button;
    }
}
