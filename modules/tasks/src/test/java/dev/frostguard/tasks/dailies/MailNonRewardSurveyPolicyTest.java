package dev.frostguard.tasks.dailies;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MailNonRewardSurveyPolicyTest {
    @Test void onlyStableSingleSurveyInvitationFromSystemCanBeSkipped() {
        assertTrue(MailNonRewardSurveyPolicy.canLeaveSurveyUnread(1,
                "[Survey] Alliance Communication", "[Survey] Alliance Communication"));
        assertFalse(MailNonRewardSurveyPolicy.canLeaveSurveyUnread(2,
                "[Survey] Alliance Communication", "[Survey] Alliance Communication"));
        assertFalse(MailNonRewardSurveyPolicy.canLeaveSurveyUnread(1,
                "[Survey] Alliance Communication", "Survey text missing".replace("Survey", "Other")));
        assertFalse(MailNonRewardSurveyPolicy.canLeaveSurveyUnread(1,
                "Mail reward x1", "Mail reward x1"));
        assertFalse(MailNonRewardSurveyPolicy.canLeaveSurveyUnread(1, null, null));
        assertFalse(MailNonRewardSurveyPolicy.canLeaveSurveyUnread(0,
                "[Survey] Alliance Communication", "[Survey] Alliance Communication"));
    }
}
