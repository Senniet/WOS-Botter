package dev.frostguard.tasks.pets;

import java.util.List;

import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;

/**
 * Pure decisions for Pet Adventure map pins and the start overlay.
 *
 * <p>An idle pin is a coloured chest without a countdown. An occupied pin
 * still matches the red/purple/blue rim crops, but it has a dark timer
 * pill under the marker. After Start, the accepted screen is the overlay
 * whose subtitle is {@code In Adventure}.</p>
 */
final class PetAdventureDecisions {

    static final double TEMPLATE_THRESHOLD = 90.0;

    /** Orange clock on the occupied-pin pill; second pin can sit a bit below 90. */
    static final double TIMER_THRESHOLD = 80.0;

    /** Horizontal slack from the chest centre to its countdown pill. */
    static final int TIMER_MAX_DX = 90;

    /** Countdown sits under the pin, not on the chest art. */
    static final int TIMER_MIN_DY = 40;

    static final int TIMER_MAX_DY = 200;

    private PetAdventureDecisions() {
    }

    /**
     * The chest icon changes to a different badge once it is running.
     * Verify the timer relative to its BEFORE-Start coordinate; don't
     * require the original red/purple/blue sprite to still match.
     */
    /** A pet adventure runs for three hours; one extra quarter hour is a
     * conservative allowance for animations, clock drift and server delay.
     * An unverified Start must not block the player indefinitely after this.
     */
    static final java.time.Duration PENDING_MAX_AGE = java.time.Duration.ofHours(3)
            .plusMinutes(15);

    static boolean pendingHasExpired(java.time.Instant pendingStart, java.time.Instant now) {
        return pendingStart != null && now != null
                && !now.isBefore(pendingStart.plus(PENDING_MAX_AGE));
    }

    static boolean startedChestHasTimer(PointData originalChest,
                                        List<ImageSearchResultData> timers) {
        return occupiedByTimer(originalChest, timers);
    }

    static boolean inAdventureOverlay(ImageSearchResultData overlay) {
        return overlay != null && overlay.isFound();
    }

    static boolean occupiedByTimer(PointData chest, List<ImageSearchResultData> timers) {
        if (chest == null || timers == null) {
            return false;
        }
        for (ImageSearchResultData timer : timers) {
            if (timer == null || !timer.isFound()) {
                continue;
            }
            int dx = Math.abs(timer.getHitX() - chest.getX());
            int dy = timer.getHitY() - chest.getY();
            if (dx <= TIMER_MAX_DX && dy >= TIMER_MIN_DY && dy <= TIMER_MAX_DY) {
                return true;
            }
        }
        return false;
    }
}
