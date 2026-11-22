package dev.frostguard.tasks.security;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;

/**
 * Per-profile and per-task explicit opt-in for diamond/gem spending.
 *
 * This is an additional condition, not a substitute for verifying that an
 * on-screen paid button is safe, its price is known and any local spending
 * limit is satisfied. An unrecognized task or missing preference fails closed.
 * Legacy "use gems" preferences never override this opt-in.
 */
public final class TaskGemSpendPolicy {
    private TaskGemSpendPolicy() {}

    public static ConfigurationKeyEnum keyFor(TpDailyTaskEnum task) {
        if (task == null) return null;
        return switch (task) {
            case CITY_UPGRADE_FURNACE -> ConfigurationKeyEnum.CITY_UPGRADE_ALLOW_GEM_SPENDING_BOOL;
            case ARENA -> ConfigurationKeyEnum.ARENA_ALLOW_GEM_SPENDING_BOOL;
            case GATHER_BOOST -> ConfigurationKeyEnum.GATHER_SPEED_ALLOW_GEM_SPENDING_BOOL;
            case EVENT_TUNDRA_TRUCK -> ConfigurationKeyEnum.TUNDRA_TRUCK_ALLOW_GEM_SPENDING_BOOL;
            case NOMADIC_MERCHANT -> ConfigurationKeyEnum.NOMADIC_MERCHANT_ALLOW_GEM_SPENDING_BOOL;
            case ALLIANCE_MOBILIZATION -> ConfigurationKeyEnum.ALLIANCE_MOBILIZATION_ALLOW_GEM_SPENDING_BOOL;
            default -> null;
        };
    }

    public static boolean allowed(AccountDescriptor account, TpDailyTaskEnum task) {
        ConfigurationKeyEnum key = keyFor(task);
        return account != null && key != null
                && Boolean.TRUE.equals(account.getConfig(key, Boolean.class));
    }

    public static boolean permitted(boolean mainSwitch, boolean actionSwitch) {
        return mainSwitch && actionSwitch;
    }
}
