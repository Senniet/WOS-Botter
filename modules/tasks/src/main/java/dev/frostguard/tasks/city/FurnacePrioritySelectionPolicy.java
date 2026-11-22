package dev.frostguard.tasks.city;

import java.util.Locale;
import java.util.Set;

/** Fail-closed building selection for the Furnace progression chain.
 *
 * The game cycles Furnace prerequisites through Embassy, three troop camps
 * and Research Center at ordinary levels 11-30. These are the only optional
 * chain buildings we allow, and only after identifying their selected-building
 * title twice. A red/unknown building or any unrelated Mine/Storehouse/Shelter
 * never receives an Upgrade tap.
 *
 * The exact next-level requirement cannot be inferred without reading the
 * Furnace requirement panel. This policy is a conservative CHAIN allowlist,
 * not proof that each individual camp is required for the immediate next level.
 */
final class FurnacePrioritySelectionPolicy {
    private FurnacePrioritySelectionPolicy() {}

    private static final Set<String> CHAIN = Set.of(
            "embassy", "infantrycamp", "lancercamp", "marksman"
                    + "camp", "researchcenter");

    static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z]", "");
    }

    static boolean isChainBuilding(String name) {
        return CHAIN.contains(normalize(name));
    }

    static boolean verifiedChainBuilding(String first, String second) {
        String a=normalize(first), b=normalize(second);
        return !a.isEmpty() && a.equals(b) && CHAIN.contains(a);
    }

    static boolean mayUseRecommendedBuilding(boolean furnacePanelVerified,
            String firstSelectedName, String secondSelectedName) {
        return furnacePanelVerified
                || verifiedChainBuilding(firstSelectedName, secondSelectedName);
    }

    static boolean shouldReserveFirstQueue(boolean focusTaskEnabled,
            boolean generalCityUpgrade) {
        return focusTaskEnabled && generalCityUpgrade;
    }
}
