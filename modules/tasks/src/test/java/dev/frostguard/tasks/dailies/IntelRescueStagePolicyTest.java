package dev.frostguard.tasks.dailies;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IntelRescueStagePolicyTest {
    @Test void formationRequiresActualDeploymentNotMerelyButtonDisappearance(){
        assertEquals(IntelRescueStagePolicy.Decision.VERIFY_DEPLOY,
                IntelRescueStagePolicy.decide(true,true));
        assertEquals(IntelRescueStagePolicy.Decision.VERIFY_DEPLOY,
                IntelRescueStagePolicy.decide(false,true));
        assertEquals(IntelRescueStagePolicy.Decision.DIRECT_ACKNOWLEDGED,
                IntelRescueStagePolicy.decide(true,false));
        assertEquals(IntelRescueStagePolicy.Decision.DEFER_UNKNOWN,
                IntelRescueStagePolicy.decide(false,false));
    }
}
