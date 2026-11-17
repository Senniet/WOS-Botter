package dev.frostguard.tasks.heroes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroRecruitmentKeyPolicyTest {
    @Test void validatesOCRCounterAndRejectsAmbiguousOrLargeValues() {
        assertEquals(2,HeroRecruitmentKeyPolicy.countFromOcr("2"));
        assertEquals(1,HeroRecruitmentKeyPolicy.countFromOcr("I"));
        assertEquals(0,HeroRecruitmentKeyPolicy.countFromOcr("0"));
        for (String raw : new String[]{"", "Recruit once", "x1", "2 3", "1,000", "123", "-1"})
            assertEquals(-1,HeroRecruitmentKeyPolicy.countFromOcr(raw),raw);
        assertEquals(-1,HeroRecruitmentKeyPolicy.countFromOcr(null));
    }

    @Test void spendingRequiresOptInExactKeyTemplateAndTwoStablePositiveCountReadings() {
        assertTrue(HeroRecruitmentKeyPolicy.mayRecruitWithKey(true,2,2,true));
        assertFalse(HeroRecruitmentKeyPolicy.mayRecruitWithKey(false,2,2,true));
        assertFalse(HeroRecruitmentKeyPolicy.mayRecruitWithKey(true,2,2,false));
        assertFalse(HeroRecruitmentKeyPolicy.mayRecruitWithKey(true,2,1,true));
        assertFalse(HeroRecruitmentKeyPolicy.mayRecruitWithKey(true,-1,-1,true));
        assertFalse(HeroRecruitmentKeyPolicy.mayRecruitWithKey(true,0,0,true));
    }

    @Test void verifiedSingleKeyConsumptionExcludesDoubleOrGemExpense() {
        assertTrue(HeroRecruitmentKeyPolicy.verifiedSingleKeySpent(2,1));
        assertTrue(HeroRecruitmentKeyPolicy.verifiedSingleKeySpent(1,0));
        assertFalse(HeroRecruitmentKeyPolicy.verifiedSingleKeySpent(2,2));
        assertFalse(HeroRecruitmentKeyPolicy.verifiedSingleKeySpent(3,1));
        assertFalse(HeroRecruitmentKeyPolicy.verifiedSingleKeySpent(1,-1));
    }
}
