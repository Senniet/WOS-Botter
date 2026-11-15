package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;

/** Finds the Upgrade tap point only when the known tutorial hand obscures its arrow. */
final class CityUpgradeGuidanceDetector {
    private static final AreaData DETAILS_AREA = AreaData.of(165, 780, 315, 930);
    private static final AreaData HAND_AREA = AreaData.of(345, 725, 510, 880);
    private static final AreaData UPGRADE_AREA = AreaData.of(295, 820, 435, 945);
    private static final int DETAILS_THRESHOLD = 93;
    private static final int HAND_THRESHOLD = 97;
    // Derived from the November 15 live 720x1280 view: finger points left and below the hand match.
    private static final int FINGER_OFFSET_X = -53;
    private static final int FINGER_OFFSET_Y = 61;

    private CityUpgradeGuidanceDetector() {
    }

    @FunctionalInterface
    interface Matcher {
        ImageSearchResultData locate(TemplatesEnum template, AreaData area, int threshold);
    }

    record Evidence(ImageSearchResultData details, ImageSearchResultData hand,
                    ImageSearchResultData upgrade) {
        boolean actionable() { return upgrade != null && upgrade.isFound(); }
    }

    static Evidence inspect(Matcher matcher) {
        ImageSearchResultData details = matcher.locate(
                TemplatesEnum.BUILDING_BUTTON_DETAILS, DETAILS_AREA, DETAILS_THRESHOLD);
        if (details == null || !details.isFound() || details.getPoint() == null) {
            return new Evidence(details, ImageSearchResultData.miss(), ImageSearchResultData.miss());
        }
        ImageSearchResultData hand = matcher.locate(
                TemplatesEnum.SKIP_TUTORIAL_HAND, HAND_AREA, HAND_THRESHOLD);
        if (hand == null || !hand.isFound() || hand.getPoint() == null) {
            return new Evidence(details, hand, ImageSearchResultData.miss());
        }
        int fingerX = hand.getPoint().getX() + FINGER_OFFSET_X;
        int fingerY = hand.getPoint().getY() + FINGER_OFFSET_Y;
        PointData detailsPoint = details.getPoint();
        // Require the hand to point at the control immediately to the right of Details,
        // not an unrelated tutorial prompt or a premium Finish action.
        if (fingerX - detailsPoint.getX() < 100 || fingerX - detailsPoint.getX() > 165
                || Math.abs(fingerY - detailsPoint.getY()) > 55
                || fingerX < UPGRADE_AREA.topLeft().getX() || fingerX > UPGRADE_AREA.bottomRight().getX()
                || fingerY < UPGRADE_AREA.topLeft().getY() || fingerY > UPGRADE_AREA.bottomRight().getY()) {
            return new Evidence(details, hand, ImageSearchResultData.miss());
        }
        ImageSearchResultData upgrade = ImageSearchResultData.hit(fingerX, fingerY,
                Math.min(details.getMatchScore(), hand.getMatchScore()), 12, 12);
        return new Evidence(details, hand, upgrade);
    }
}
