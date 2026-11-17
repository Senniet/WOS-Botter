package dev.frostguard.tasks.economy;

import dev.frostguard.engine.emulator.EmulatorType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MysteryShopSpendingPolicyTest {
    @Test void freeRewardRoutineNeverPurchasesWithBadgesOnServeEmu() {
        assertFalse(MysteryShopRoutine.allowBadgePurchases(EmulatorType.SERVE_EMU, true));
        assertFalse(MysteryShopRoutine.allowBadgePurchases(EmulatorType.SERVE_EMU, false));
        assertFalse(MysteryShopRoutine.allowBadgePurchases(EmulatorType.BLUESTACKS, false));
        assertTrue(MysteryShopRoutine.allowBadgePurchases(EmulatorType.BLUESTACKS, true));
    }
}
