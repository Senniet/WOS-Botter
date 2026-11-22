package dev.frostguard.tasks.city;

import dev.frostguard.engine.emulator.EmulatorType;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CityUpgradeVipMisclickSafety20261120Test {
    @Test void serveEmuNeverUsesBlindResourceConfirmationOrVipShopTap() {
        // Exact live issue: Obtain More displays VIP Shop Go near (511,1056).
        // No automatic replenishment can be authorized on this backend.
        assertFalse(UpgradeBuildingsRoutine.mayAutomaticallyReplenish(EmulatorType.SERVE_EMU));
    }

    @Test void exactResourceLabelIsRequiredBeforeAnyClick() {
        assertTrue(UpgradeBuildingsRoutine.verifiedOwnedPackButtonText("Replenish All"));
        assertTrue(UpgradeBuildingsRoutine.verifiedOwnedPackButtonText("Replenish\nAll"));
        assertFalse(UpgradeBuildingsRoutine.verifiedOwnedPackButtonText("Buy & Use 100"));
        assertFalse(UpgradeBuildingsRoutine.verifiedOwnedPackButtonText("VIP Shop"));
        assertFalse(UpgradeBuildingsRoutine.verifiedOwnedPackButtonText(null));
    }

    @Test void resourceShortageRequiresOnlyOneAttemptAndVerifiedHomeRecovery() {
        int[] attempts = {0};
        int[] recovered = {0};
        var exception = assertThrows(CityUpgradeFlow.UnresolvedBuildingException.class,
                () -> CityUpgradeFlow.handleQueue(2, new CityUpgradeFlow.QueueUi<Boolean>() {
                    @Override public CityUpgradeFlow.Attempt<Boolean> attempt(int n) {
                        attempts[0]++;
                        return CityUpgradeFlow.Attempt.unresolved(
                                CityUpgradeFlow.FailureReason.RESOURCES_NOT_OBTAINED);
                    }
                    @Override public void retainFailure(int n, CityUpgradeFlow.FailureReason reason) {
                        assertEquals(CityUpgradeFlow.FailureReason.RESOURCES_NOT_OBTAINED, reason);
                    }
                    @Override public void recoverHome() { recovered[0]++; }
                }));
        assertEquals(CityUpgradeFlow.FailureReason.RESOURCES_NOT_OBTAINED, exception.reason());
        assertEquals(1, attempts[0]);
        assertEquals(1, recovered[0]);
    }
}
