package dev.frostguard.tasks.security;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskGemSpendPolicyTest {
    private static final Map<TpDailyTaskEnum, ConfigurationKeyEnum> GEM_CAPABLE = Map.of(
            TpDailyTaskEnum.CITY_UPGRADE_FURNACE, ConfigurationKeyEnum.CITY_UPGRADE_ALLOW_GEM_SPENDING_BOOL,
            TpDailyTaskEnum.ARENA, ConfigurationKeyEnum.ARENA_ALLOW_GEM_SPENDING_BOOL,
            TpDailyTaskEnum.GATHER_BOOST, ConfigurationKeyEnum.GATHER_SPEED_ALLOW_GEM_SPENDING_BOOL,
            TpDailyTaskEnum.EVENT_TUNDRA_TRUCK, ConfigurationKeyEnum.TUNDRA_TRUCK_ALLOW_GEM_SPENDING_BOOL,
            TpDailyTaskEnum.NOMADIC_MERCHANT, ConfigurationKeyEnum.NOMADIC_MERCHANT_ALLOW_GEM_SPENDING_BOOL,
            TpDailyTaskEnum.ALLIANCE_MOBILIZATION, ConfigurationKeyEnum.ALLIANCE_MOBILIZATION_ALLOW_GEM_SPENDING_BOOL);

    @Test void everyGemCapableTaskHasDistinctOptInDefaultingOff() {
        assertEquals(6, GEM_CAPABLE.values().stream().distinct().count());
        AccountDescriptor profile = new AccountDescriptor(120L);
        for (var entry : GEM_CAPABLE.entrySet()) {
            assertEquals(entry.getValue(), TaskGemSpendPolicy.keyFor(entry.getKey()));
            assertEquals("false", entry.getValue().initialValue());
            assertFalse(TaskGemSpendPolicy.allowed(profile, entry.getKey()),
                    "No persisted opt-in must NEVER allow a paid action for " + entry.getKey());
        }
        assertNull(TaskGemSpendPolicy.keyFor(TpDailyTaskEnum.RESEARCH));
        assertNull(TaskGemSpendPolicy.keyFor(null));
        assertFalse(TaskGemSpendPolicy.allowed(null, TpDailyTaskEnum.ARENA));
        assertFalse(TaskGemSpendPolicy.allowed(profile, TpDailyTaskEnum.RESEARCH));
    }

    @Test void enablingOneTaskDoesNotPermitAnyOtherTaskOrUnknownTask() {
        AccountDescriptor profile = new AccountDescriptor(121L);
        for (var target : GEM_CAPABLE.entrySet()) {
            for (var reset : GEM_CAPABLE.values()) profile.setConfig(reset, false);
            profile.setConfig(target.getValue(), true);
            for (var candidate : GEM_CAPABLE.keySet()) {
                assertEquals(candidate == target.getKey(),
                        TaskGemSpendPolicy.allowed(profile, candidate), candidate.name());
            }
            assertFalse(TaskGemSpendPolicy.allowed(profile, TpDailyTaskEnum.SHOP_MYSTERY));
        }
    }

    @Test void legacyActionSettingIsNotSufficientByItself() {
        assertFalse(TaskGemSpendPolicy.permitted(false, true));
        assertFalse(TaskGemSpendPolicy.permitted(true, false));
        assertFalse(TaskGemSpendPolicy.permitted(false, false));
        assertTrue(TaskGemSpendPolicy.permitted(true, true));
    }
}
