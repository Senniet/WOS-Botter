package dev.frostguard.tasks.economy;

import java.util.Locale;

/** Conservative text gate for old blind-coordinate Growth/Boost flows. */
final class GatherBoostScreenEvidence {
    private GatherBoostScreenEvidence() {}
    static boolean isGrowthTitle(String text) {
        return normalize(text).contains("growth");
    }
    static boolean isGatheringSpeedScreen(String text) {
        return normalize(text).contains("gatheringspeed");
    }
    static boolean isAppliedBoost(String text) {
        String s=normalize(text);
        return s.contains("gatheringspeed")
                && (s.contains("active")||s.contains("remaining")||s.contains("activated"));
    }
    static boolean isObtainDialog(String text) {
        return normalize(text).contains("obtainmore");
    }
    static boolean isEightHourProduct(String text) {
        String s=normalize(text);
        return s.contains("gatheringspeedboost") && s.contains("8h");
    }
    static boolean isAllowedPurchase(String text) {
        String s=normalize(text);
        return s.contains("buyuse") && s.contains("250") && !s.contains("600");
    }
    static boolean isPurchaseConfirmation(String text) {
        String s=normalize(text);
        return s.contains("250") && (s.contains("confirm") || s.contains("purchase"));
    }
    private static String normalize(String text) {
        return text==null?"":text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
    }
}
