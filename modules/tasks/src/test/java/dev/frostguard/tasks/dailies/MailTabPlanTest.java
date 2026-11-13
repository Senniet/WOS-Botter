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
        assertEquals(360, plan.get(2).tabPosition().getX());
        assertTrue(plan.get(2).systemReports());
        assertFalse(plan.get(4).systemReports());
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
