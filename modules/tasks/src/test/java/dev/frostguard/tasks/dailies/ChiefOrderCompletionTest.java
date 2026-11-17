package dev.frostguard.tasks.dailies;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChiefOrderCompletionTest {
    @Test void successfulCooldownRequiresVisibleAndDismissedReward() {
        assertTrue(ChiefOrderRoutine.completedWithVerifiedRewards(
                ChiefOrderRewardFrame.Exit.DISMISSED));
        assertFalse(ChiefOrderRoutine.completedWithVerifiedRewards(
                ChiefOrderRewardFrame.Exit.NOT_PRESENT));
        assertFalse(ChiefOrderRoutine.completedWithVerifiedRewards(
                ChiefOrderRewardFrame.Exit.UNVERIFIED));
    }
}
