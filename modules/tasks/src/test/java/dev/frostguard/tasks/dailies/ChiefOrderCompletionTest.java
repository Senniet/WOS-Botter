package dev.frostguard.tasks.dailies;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class ChiefOrderCompletionTest {
    @Test void previouslyClaimedCooldownReschedulesToItsActualExpiry() {
        var now = LocalDateTime.of(2026, 11, 17, 20, 0);
        assertEquals(now.plusHours(3).plusMinutes(49),
                ChiefOrderRoutine.nextAvailableCheck(now, Duration.ofHours(3).plusMinutes(48)));
        assertEquals(now.plusMinutes(30), ChiefOrderRoutine.nextAvailableCheck(now, null));
    }

    @Test void onlyRushJobNeedsARewardsScreen() {
        assertTrue(ChiefOrderRoutine.expectsRewards(ChiefOrderRoutine.ChiefOrderType.RUSH_JOB));
        assertFalse(ChiefOrderRoutine.expectsRewards(ChiefOrderRoutine.ChiefOrderType.URGENT_MOBILIZATION));
        assertFalse(ChiefOrderRoutine.expectsRewards(ChiefOrderRoutine.ChiefOrderType.PRODUCTIVITY_DAY));
    }

    @Test void successfulCooldownRequiresVisibleAndDismissedReward() {
        assertTrue(ChiefOrderRoutine.completedWithVerifiedRewards(
                ChiefOrderRewardFrame.Exit.DISMISSED));
        assertFalse(ChiefOrderRoutine.completedWithVerifiedRewards(
                ChiefOrderRewardFrame.Exit.NOT_PRESENT));
        assertFalse(ChiefOrderRoutine.completedWithVerifiedRewards(
                ChiefOrderRewardFrame.Exit.UNVERIFIED));
    }
}
