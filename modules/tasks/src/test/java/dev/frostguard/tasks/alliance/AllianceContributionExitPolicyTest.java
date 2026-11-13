package dev.frostguard.tasks.alliance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AllianceContributionExitPolicyTest {
    @Test void confirmsOnlyTheAllianceContributionWindowInTwoSeparateFrames() {
        assertTrue(AllianceContributionExitPolicy.isVerifiedContributionTitle(
                "Long Live Our Alliance", "Long Live Our Alliance"));
        assertFalse(AllianceContributionExitPolicy.isVerifiedContributionTitle(
                "Long Live Our Alliance", "Alliance Tech"));
        assertFalse(AllianceContributionExitPolicy.isVerifiedContributionTitle(
                "Alliance Contribution", "Alliance Contribution"));
        assertFalse(AllianceContributionExitPolicy.isVerifiedContributionTitle(null,
                "Long Live Our Alliance"));
        assertFalse(AllianceContributionExitPolicy.isVerifiedContributionTitle(
                "Long Live Our Alliance", "Quit game?"));
    }
}
