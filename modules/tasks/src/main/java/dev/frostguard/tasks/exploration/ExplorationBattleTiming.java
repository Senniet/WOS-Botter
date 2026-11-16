package dev.frostguard.tasks.exploration;

/** Explicit normal battle pacing; never abandon a fight after 25 seconds. */
final class ExplorationBattleTiming {
    private ExplorationBattleTiming(){}
    // Normal battles take longer than the old 25-second result window.
    // A finished result is acknowledged immediately, otherwise wait at most
    // three minutes per battle (including animations) before marking unknown.
    static final long RESULT_WINDOW_MS=180_000L;
    static final long SESSION_WINDOW_MS=720_000L;
    static final long POLL_MS=2_500L;
    static final long LATE_QUICK_DIALOG_MS=15_000L;
    static final int MAX_NORMAL_BATTLES_PER_RUN=4;
    static boolean nextStageAllowed(int completedBattles,long elapsedSessionMs) {
        return completedBattles>0 && completedBattles<MAX_NORMAL_BATTLES_PER_RUN
                && elapsedSessionMs < SESSION_WINDOW_MS-RESULT_WINDOW_MS-10_000L;
    }
    static boolean mayInspectLateQuick(long elapsedMs){
        return elapsedMs < LATE_QUICK_DIALOG_MS;
    }
}
