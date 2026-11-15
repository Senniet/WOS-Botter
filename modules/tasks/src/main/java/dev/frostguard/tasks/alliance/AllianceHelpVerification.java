package dev.frostguard.tasks.alliance;

import dev.frostguard.api.domain.ImageSearchResultData;

/** Two fresh matching coordinates plus a verified City postcondition. */
final class AllianceHelpVerification {
    private AllianceHelpVerification() {}

    static boolean sameVisibleButton(ImageSearchResultData first,
                                     ImageSearchResultData second) {
        return first != null && second != null
                && first.isFound() && second.isFound()
                && Math.abs(first.getPoint().getX()-second.getPoint().getX()) <= 18
                && Math.abs(first.getPoint().getY()-second.getPoint().getY()) <= 18;
    }

    static boolean verifiedClaim(boolean buttonTapped, boolean cityStillVisible,
                                 boolean shortcutStillVisible) {
        return buttonTapped && cityStillVisible && !shortcutStillVisible;
    }
}
