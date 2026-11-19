package dev.frostguard.tasks.economy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StorehouseStaminaRetryPolicyTest {
    @Test void refusalSkipsOneOrTwoThirtyMinuteVisitsNotChestCollections(){
        LocalDateTime now=LocalDateTime.of(2026,11,19,12,54);
        var due=StorehouseStaminaRetryPolicy.nextClaimAfterRefusal(now);
        assertFalse(StorehouseStaminaRetryPolicy.shouldSkip(now,null));
        assertTrue(StorehouseStaminaRetryPolicy.shouldSkip(now.plusMinutes(30),due));
        assertTrue(StorehouseStaminaRetryPolicy.shouldSkip(now.plusMinutes(60),due));
        assertFalse(StorehouseStaminaRetryPolicy.shouldSkip(now.plusMinutes(75),due));
        assertFalse(StorehouseStaminaRetryPolicy.shouldSkip(now.plusHours(2),due));
    }
}
