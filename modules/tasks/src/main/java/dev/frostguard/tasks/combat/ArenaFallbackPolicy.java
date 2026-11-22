package dev.frostguard.tasks.combat;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Last-resort daily challenge selection: confirmed green always precedes red. */
final class ArenaFallbackPolicy {
    private ArenaFallbackPolicy() {}

    static boolean selectablePower(boolean green, boolean red, boolean lastResort) {
        // UNKNOWN must never authorize an Arena battle. RED is only allowed
        // after all earlier green/refresh choices were exhausted.
        return green || (lastResort && red);
    }

    static <T> List<T> preferGreenBeforeRed(List<T> allowed,
            Predicate<T> isWeaker, Comparator<T> preference) {
        return allowed.stream()
                .sorted(Comparator.comparingInt((T candidate) ->
                        isWeaker.test(candidate) ? 0 : 1).thenComparing(preference))
                .toList();
    }
}
