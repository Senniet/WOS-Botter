package dev.frostguard.engine.helper;

import dev.frostguard.engine.emulator.EmulatorType;

/** Shared root-first navigation plan for every Serve-EMU task. */
final class NavigationProbePlan {
    private NavigationProbePlan(){}
    static boolean requiresFullReconnect(EmulatorType emulator,boolean city,boolean world) {
        // On a known healthy HUD the root anchor is proof the UI is ready.
        // Unknown, blurred or reconnect-blocked HUD must run the full guard.
        return emulator!=EmulatorType.SERVE_EMU || (!city && !world);
    }
}
