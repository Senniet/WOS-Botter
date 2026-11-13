package dev.frostguard.tasks.dailies;

import java.util.Locale;

/** A persistent single survey invitation is mail, not an unclaimed reward. */
final class MailNonRewardSurveyPolicy {
    private MailNonRewardSurveyPolicy() { }

    static boolean canLeaveSurveyUnread(int unreadCount, String firstRead, String secondRead) {
        return unreadCount == 1 && isSurvey(firstRead) && isSurvey(secondRead);
    }

    private static boolean isSurvey(String text) {
        if (text == null) return false;
        String value = text.toLowerCase(Locale.ROOT).replaceAll("[\\[\\]{}()|]", " ");
        // Only explicit Survey mail qualifies. A generic unread reward or
        // announcement must not be incorrectly treated as nonreward mail.
        return value.matches("(?s).*\\bsurvey\\b.*");
    }
}
