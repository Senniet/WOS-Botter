package dev.frostguard.tasks.city;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResearchQueueStatusPolicyTest {

    @Test
    void onlyExplicitIdleLabelConfirmsAnAvailableQueue() {
        assertTrue(ResearchQueueStatusPolicy.isExplicitlyIdle("Idle"));
        assertTrue(ResearchQueueStatusPolicy.isExplicitlyIdle("  IDLE!  "));
        assertFalse(ResearchQueueStatusPolicy.isExplicitlyIdle(null));
        assertFalse(ResearchQueueStatusPolicy.isExplicitlyIdle(""));
        assertFalse(ResearchQueueStatusPolicy.isExplicitlyIdle("not idle"));
        assertFalse(ResearchQueueStatusPolicy.isExplicitlyIdle("idle 00:12:34"));
        assertFalse(ResearchQueueStatusPolicy.isExplicitlyIdle("building idle-ish"));
    }
}
