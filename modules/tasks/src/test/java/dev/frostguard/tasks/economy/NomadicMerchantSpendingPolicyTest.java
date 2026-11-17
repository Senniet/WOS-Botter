package dev.frostguard.tasks.economy;

import dev.frostguard.engine.emulator.EmulatorType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NomadicMerchantSpendingPolicyTest {
    @Test void serveEmuDoesNotBuyVipForGemsEvenWhenOldProfileFlagIsTrue() {
        assertFalse(NomadicMerchantRoutine.vipSpendingAllowed(EmulatorType.SERVE_EMU, true));
        assertFalse(NomadicMerchantRoutine.vipSpendingAllowed(EmulatorType.SERVE_EMU, false));
        assertFalse(NomadicMerchantRoutine.vipSpendingAllowed(EmulatorType.SERVE_EMU, null));
        assertFalse(NomadicMerchantRoutine.vipSpendingAllowed(EmulatorType.BLUESTACKS, null));
        assertTrue(NomadicMerchantRoutine.vipSpendingAllowed(EmulatorType.BLUESTACKS, true));
    }
}
