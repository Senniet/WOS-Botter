package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import dev.frostguard.vision.convert.ImageConverter;

/** Direct, current 720x1280 Daily Missions Claim All detector.
 * Read an already captured screen once; a pending action is verified by the
 * caller on a second fresh frame. Missing is normal when rewards are claimed.
 */
final class DailyClaimAllFrame {
    private static final GreenLabelActionDetector.Region CLAIM_ALL=
            new GreenLabelActionDetector.Region(230,985,495,1070);
    private DailyClaimAllFrame(){}

    static ImageSearchResultData ready(RawImageData frame) {
        if (!DailyMissionsCurrentFrame.title(frame)) return ImageSearchResultData.miss();
        return GreenLabelActionDetector.detect(ImageConverter.toBufferedImage(frame),CLAIM_ALL);
    }
}
