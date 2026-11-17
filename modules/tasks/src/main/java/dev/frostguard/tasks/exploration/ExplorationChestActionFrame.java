package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/** The 2026 Exploration Claim text is present even when its CTA is disabled.
 * Require the current page title and a vivid green button body before any tap.
 * One current screenshot is enough for a miss; callers independently confirm
 * positive targets on the next frame before tapping.
 */
final class ExplorationChestActionFrame {
    private static final GreenLabelActionDetector.Region ACTION =
            new GreenLabelActionDetector.Region(535,830,701,909);
    private static final PointData START = new PointData(525,804);
    private static final PointData END = new PointData(710,926);
    private ExplorationChestActionFrame() {}

    static ImageSearchResultData enabled(RawImageData frame) {
        if (!ExplorationPageFrame.title(frame)) return ImageSearchResultData.miss();
        return GreenLabelActionDetector.detect(ImageConverter.toBufferedImage(frame),ACTION);
    }

    static boolean disabled(RawImageData frame) {
        if (!ExplorationPageFrame.title(frame)) return false;
        // Do not re-run title matching via enabled(): one title result is
        // sufficient, and the active green Claim must never be mistaken
        // for the visually similar disabled Claim artwork.
        if (GreenLabelActionDetector.detect(ImageConverter.toBufferedImage(frame),ACTION).isFound())
            return false;
        return OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.EXPLORATION_CLAIM_DISABLED.getTemplate(),START,END,88).isFound();
    }
}
