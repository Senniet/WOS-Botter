package dev.frostguard.tasks.city;

import java.time.Duration;
import java.util.Locale;

/**
 * City recommendation can select Research Center while Tech Research is
 * already running. The UI then has Details / Finish / Speedup / Research,
 * NOT Upgrade. A positive needs the exact Research Center title AND a
 * full active countdown; guidance-hand artwork alone is never actionable.
 */
final class ResearchCenterBusyRead {
    private ResearchCenterBusyRead() {}

    static Duration positive(String buildingName, String countdown, boolean upgradeVisible) {
        if (upgradeVisible || buildingName == null) return null;
        String title=buildingName.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (!"researchcenter".equals(title)) return null;
        return TrainingCampBusyRead.fullClock(countdown);
    }
}
