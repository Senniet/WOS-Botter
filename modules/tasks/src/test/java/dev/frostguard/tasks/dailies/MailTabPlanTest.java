package dev.frostguard.tasks.dailies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import java.util.List;

class MailTabPlanTest {
    @Test void currentMailUiChecksAllFiveTabsWithoutLosingSystemReportHandling() {
        var plan=MailRewardsRoutine.tabPlan(true);
        assertEquals(List.of("Wars", "Alliance", "System", "Reports", "Starred"),
                plan.stream().map(MailRewardsRoutine.MailTab::name).toList());
        assertEquals(5, plan.size());
        // Verified against live emulator-5554 Mail tabs at y=86..152 on 2026-11-17.
        assertTrue(plan.stream().allMatch(tab -> tab.tabPosition().getY() == 120));
        assertEquals(360, plan.get(2).tabPosition().getX());
        assertTrue(plan.get(2).systemReports());
        assertFalse(plan.get(4).systemReports());
    }

    @Test void currentMailEmptyListIsNotRescannedThreeTimesPerTab() {
        assertEquals(1, MailRewardsRoutine.unreadMarkerSearchAttempts(
                dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU));
        for (var emulator : dev.frostguard.engine.emulator.EmulatorType.values()) {
            if (emulator != dev.frostguard.engine.emulator.EmulatorType.SERVE_EMU)
                assertEquals(3, MailRewardsRoutine.unreadMarkerSearchAttempts(emulator));
        }
    }

    @Test void mailCannotReportSuccessBeforeVerifiedReturnHome() {
        assertTrue(MailRewardsRoutine.mailRunVerified(true, true, true));
        assertFalse(MailRewardsRoutine.mailRunVerified(true, true, false));
        assertFalse(MailRewardsRoutine.mailRunVerified(true, false, true));
        assertFalse(MailRewardsRoutine.mailRunVerified(false, true, true));
    }

    @Test void olderThreeTabInterfaceRetainsItsOriginalOrderAndCoordinates() {
        var plan=MailRewardsRoutine.tabPlan(false);
        assertEquals(List.of("Alliance", "System", "Reports"),
                plan.stream().map(MailRewardsRoutine.MailTab::name).toList());
        assertEquals(3, plan.size());
        assertEquals(120, plan.get(1).tabPosition().getY());
        assertTrue(plan.get(1).systemReports());
    }
}
