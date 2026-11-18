package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.ImageSearchResultData;

/** Detects the real Upgrade target only with a matched Details icon and tutorial hand. */
final class CityUpgradeGuidanceDetector {
    // Two actually observed 720x1280 selected-building layouts:
    // 2026-11-15: Details (235,854), hand (413,814), Upgrade (360,875)
    // 2026-11-18: Details (300,880), hand (491,791), Upgrade (416,880).
    // The old fixed areas missed both current anchors, creating a 12x retry loop.
    private static final AreaData DETAILS_AREA = AreaData.of(160, 775, 375, 953);
    private static final AreaData HAND_AREA = AreaData.of(340, 695, 575, 905);
    private static final AreaData OLD_UPGRADE_AREA = AreaData.of(310, 819, 425, 935);
    private static final AreaData NEW_UPGRADE_AREA = AreaData.of(366, 827, 480, 938);
    private static final int DETAILS_THRESHOLD = 93;
    private static final int HAND_THRESHOLD = 97;

    private CityUpgradeGuidanceDetector() {}

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
        var d = details.getPoint();
        var h = hand.getPoint();
        // Select the validated artwork variant by two *independently*
        // recognized anchors, never by screen-wide color or guessed OCR.
        final boolean modern = d.getX() >= 270;
        final int offsetX = modern ? -75 : -53;
        final int offsetY = modern ? 89 : 61;
        final int expectedDetailsX = modern ? 300 : 235;
        final int expectedDetailsY = modern ? 880 : 854;
        final int expectedHandX = modern ? 491 : 413;
        final int expectedHandY = modern ? 791 : 814;
        final AreaData area = modern ? NEW_UPGRADE_AREA : OLD_UPGRADE_AREA;
        int targetX = h.getX() + offsetX;
        int targetY = h.getY() + offsetY;
        int separation = targetX - d.getX();
        if (Math.abs(d.getX() - expectedDetailsX) > 24
                || Math.abs(d.getY() - expectedDetailsY) > 25
                || Math.abs(h.getX() - expectedHandX) > 30
                || Math.abs(h.getY() - expectedHandY) > 30
                || separation < 100 || separation > 165
                || Math.abs(targetY - d.getY()) > 35
                || targetX < area.topLeft().getX() || targetX > area.bottomRight().getX()
                || targetY < area.topLeft().getY() || targetY > area.bottomRight().getY()) {
            return new Evidence(details, hand, ImageSearchResultData.miss());
        }
        ImageSearchResultData upgrade = ImageSearchResultData.hit(targetX, targetY,
                Math.min(details.getMatchScore(), hand.getMatchScore()), 12, 12);
        return new Evidence(details, hand, upgrade);
    }
}
