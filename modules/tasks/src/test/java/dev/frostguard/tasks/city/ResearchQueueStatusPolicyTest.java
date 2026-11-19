package dev.frostguard.tasks.city;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResearchQueueStatusPolicyTest {

    @Test
    void standaloneUpgradingCannotBeParsedAsResearchTimerOrIdle() {
        assertTrue(ResearchQueueStatusPolicy.isBareUpgrading("Upgrading"));
        assertTrue(ResearchQueueStatusPolicy.isBareUpgrading(" UPGRADING! "));
        assertFalse(ResearchQueueStatusPolicy.isBareUpgrading("00:15:44"));
        assertFalse(ResearchQueueStatusPolicy.isBareUpgrading("Center Research Upgrading"));
        assertFalse(ResearchQueueStatusPolicy.isBareUpgrading("Upgrading 07:33:10"));
        assertFalse(ResearchQueueStatusPolicy.isBareUpgrading("Idle"));
        assertFalse(ResearchQueueStatusPolicy.isBareUpgrading(null));
    }

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
