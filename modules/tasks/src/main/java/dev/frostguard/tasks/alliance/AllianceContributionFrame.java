package dev.frostguard.tasks.alliance;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.BlueLabelActionDetector;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/** Verified current Serve-EMU Alliance Tech donation modal. The FREE resource
 * contribution is the BLUE lower-right action; the ORANGE lower-left action
 * spends gems and must never be tapped by the auto-donation routine.
 */
final class AllianceContributionFrame {
    private static final BlueLabelActionDetector.Region FREE_RESOURCE =
            new BlueLabelActionDetector.Region(370,985,650,1073,
                    420,1001,609,1042);
    private AllianceContributionFrame(){}

    static boolean popup(RawImageData frame) {
        return frame!=null && frame.isValid() && frame.getWidth()==720 && frame.getHeight()==1280
                && OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_ALLIANCE_CONTRIBUTION_TITLE_2026.getTemplate(),
                new PointData(170,157),new PointData(545,240),90).isFound();
    }

    static ImageSearchResultData freeResourceButton(RawImageData frame) {
        if (!popup(frame))return ImageSearchResultData.miss();
        return BlueLabelActionDetector.detect(ImageConverter.toBufferedImage(frame),FREE_RESOURCE);
    }
}
