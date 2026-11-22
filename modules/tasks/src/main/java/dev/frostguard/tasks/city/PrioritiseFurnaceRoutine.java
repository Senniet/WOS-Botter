package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;

/**
 * Furnace-first construction policy. Uses the SAME guarded construction
 * detector, resource validation, queue timer and post-action confirmation
 * as City Upgrade; the old freehand coordinate script is intentionally gone.
 * Only the Furnace and recognized members of its required building chain are
 * ever eligible. An unknown building is never upgraded as a fallback.
 */
public final class PrioritiseFurnaceRoutine extends UpgradeBuildingsRoutine {
    public PrioritiseFurnaceRoutine(AccountDescriptor profile, TpDailyTaskEnum type) {
        super(profile, type, true);
    }
}
