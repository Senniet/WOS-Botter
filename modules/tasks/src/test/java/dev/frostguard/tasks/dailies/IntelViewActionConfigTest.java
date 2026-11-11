package dev.frostguard.tasks.dailies;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntelViewActionConfigTest {
    @Test void viewRequiresPopupRegionAndTwoFrameActionVerification() {
        var c = IntelligenceRoutine.INTEL_VIEW_ACTION_SEARCH;
        assertEquals(88, c.getThreshold());
        assertTrue(c.hasArea());
        assertEquals(205, c.getArea().topLeft().getX());
        assertEquals(850, c.getArea().topLeft().getY());
        assertEquals(535, c.getArea().bottomRight().getX());
        assertEquals(1015, c.getArea().bottomRight().getY());
    }
}
