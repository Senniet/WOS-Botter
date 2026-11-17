package dev.frostguard.tasks.heroes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpertDailyClaimPolicyTest {
    @Test void onlyConfirmedDisappearanceOnTwoFramesCountsAsAClaim() {
        assertTrue(ExpertDailyClaimPolicy.disappearedOnTwoFrames(true, false, false));
        assertFalse(ExpertDailyClaimPolicy.disappearedOnTwoFrames(false, false, false));
        assertFalse(ExpertDailyClaimPolicy.disappearedOnTwoFrames(true, true, false));
        assertFalse(ExpertDailyClaimPolicy.disappearedOnTwoFrames(true, false, true));
    }

    @Test void dailyResetRequiresClaimAndVerifiedHome() {
        assertTrue(ExpertDailyClaimPolicy.readyForDailyReset(true, true));
        assertFalse(ExpertDailyClaimPolicy.readyForDailyReset(true, false));
        assertFalse(ExpertDailyClaimPolicy.readyForDailyReset(false, true));
    }
}
