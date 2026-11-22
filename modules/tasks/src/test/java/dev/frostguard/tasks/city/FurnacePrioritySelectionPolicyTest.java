package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FurnacePrioritySelectionPolicyTest {
    @Test void recognizesOnlyFurnaceChainPrerequisites() {
        for (var name : new String[] {"Embassy", "Infantry Camp",
                "Marksman Camp", "Lancer Camp", "Research Center"}) {
            assertTrue(FurnacePrioritySelectionPolicy.verifiedChainBuilding(name,name),
                    name + " belongs to furnace progression chain");
        }
        for (var name : new String[] {"Iron Mine", "Coal Mine", "Hunters Hut",
                "Storehouse", "Command Center", "Infirmary", "Shelter", "VIP Shop",
                "City Survivors", "War Academy", ""}) {
            assertFalse(FurnacePrioritySelectionPolicy.isChainBuilding(name), name);
        }
    }

    @Test void neverAcceptsUnverifiedOrChangingSelectedName() {
        assertFalse(FurnacePrioritySelectionPolicy.mayUseRecommendedBuilding(
                false,"Embassy","Iron Mine"));
        assertFalse(FurnacePrioritySelectionPolicy.mayUseRecommendedBuilding(
                false,"Embassy",""));
        assertFalse(FurnacePrioritySelectionPolicy.mayUseRecommendedBuilding(
                false,"Iron Mine","Iron Mine"));
        assertFalse(FurnacePrioritySelectionPolicy.mayUseRecommendedBuilding(
                false,"","Embassy"));
    }

    @Test void furnacePanelCanBeSelectedOnOwnVerifiedVisualEvidence() {
        assertTrue(FurnacePrioritySelectionPolicy.mayUseRecommendedBuilding(
                true,"",""));
        assertTrue(FurnacePrioritySelectionPolicy.mayUseRecommendedBuilding(
                false,"Embassy","Embassy"));
    }

    @Test void neverStealsGeneralQueueUnlessFocusTaskActuallyEnabled() {
        assertFalse(FurnacePrioritySelectionPolicy.shouldReserveFirstQueue(false,true));
        assertFalse(FurnacePrioritySelectionPolicy.shouldReserveFirstQueue(true,false));
        assertTrue(FurnacePrioritySelectionPolicy.shouldReserveFirstQueue(true,true));
    }
}
