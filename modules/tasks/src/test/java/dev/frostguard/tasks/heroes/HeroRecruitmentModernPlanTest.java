package dev.frostguard.tasks.heroes;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroRecruitmentModernPlanTest {
    @Test void advancedThenEpicThenPointsRegardlessOfAvailability() {
        List<String> actions=new ArrayList<>();
        assertTrue(HeroRecruitmentRoutine.runModernStages(true,true,true,
                ()->{actions.add("advanced");return true;},
                ()->{actions.add("epic");return true;},
                ()->{actions.add("points");return true;}));
        assertEquals(List.of("advanced","epic","points"),actions);
    }

    @Test void noAdvancedEnabledStillExecutesEpicAndPoints() {
        List<String> actions=new ArrayList<>();
        assertTrue(HeroRecruitmentRoutine.runModernStages(false,true,true,
                ()->{actions.add("advanced");return true;},
                ()->{actions.add("epic");return true;},
                ()->{actions.add("points");return true;}));
        assertEquals(List.of("epic","points"),actions);
    }

    @Test void uncertainRecruitStopsBeforeNextActionsOrPointsChest() {
        List<String> actions=new ArrayList<>();
        assertFalse(HeroRecruitmentRoutine.runModernStages(true,true,true,
                ()->{actions.add("advanced-uncertain");return false;},
                ()->{actions.add("epic");return true;},
                ()->{actions.add("points");return true;}));
        assertEquals(List.of("advanced-uncertain"),actions);
    }

    @Test void unclearEpicAlsoPreventsLaterChestTap() {
        List<String> actions=new ArrayList<>();
        assertFalse(HeroRecruitmentRoutine.runModernStages(true,true,true,
                ()->{actions.add("advanced");return true;},
                ()->{actions.add("epic-uncertain");return false;},
                ()->{actions.add("points");return true;}));
        assertEquals(List.of("advanced","epic-uncertain"),actions);
    }
}
