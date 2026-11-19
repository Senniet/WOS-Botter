package dev.frostguard.tasks.economy;

import java.time.Duration;
import java.time.LocalDateTime;

/** A refused Stamina Claim is NOT a failed chest claim. Avoid repeating the
 * same unaccepted reward click during every 30-minute Storehouse visit. */
final class StorehouseStaminaRetryPolicy {
    static final Duration REFUSAL_COOLDOWN = Duration.ofMinutes(75);
    private StorehouseStaminaRetryPolicy(){}
    static LocalDateTime nextClaimAfterRefusal(LocalDateTime now){return now.plus(REFUSAL_COOLDOWN);}
    static boolean shouldSkip(LocalDateTime now,LocalDateTime deferredUntil){
        return deferredUntil!=null && now.isBefore(deferredUntil);
    }
}
