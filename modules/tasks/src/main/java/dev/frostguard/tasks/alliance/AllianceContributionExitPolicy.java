package dev.frostguard.tasks.alliance;

import java.util.Locale;

/** Recognize the Alliance contribution pop-up before touching its close X. */
final class AllianceContributionExitPolicy {
    private AllianceContributionExitPolicy() { }

    static boolean isVerifiedContributionTitle(String first, String second) {
        return isContributionTitle(first) && isContributionTitle(second);
    }

    private static boolean isContributionTitle(String value) {
        if (value == null) return false;
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "")
                .contains("longliveouralliance");
    }
}
