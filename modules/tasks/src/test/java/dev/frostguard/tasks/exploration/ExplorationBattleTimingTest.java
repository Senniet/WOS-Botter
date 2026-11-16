package dev.frostguard.tasks.exploration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ExplorationBattleTimingTest {
 @Test void lateVictoryIsNotAbandonedAt25Seconds() {
  assertTrue(ExplorationBattleTiming.RESULT_WINDOW_MS>=120_000);
  assertTrue(ExplorationBattleTiming.RESULT_WINDOW_MS>25_000);
  assertTrue(ExplorationBattleTiming.SESSION_WINDOW_MS>ExplorationBattleTiming.RESULT_WINDOW_MS);
  assertTrue(ExplorationBattleTiming.POLL_MS>=2_000);
 }
 @Test void normalVictoryMayContinueOnlyWithBoundedSessionAndBattleCount() {
  assertTrue(ExplorationBattleTiming.nextStageAllowed(1,50_000));
  assertTrue(ExplorationBattleTiming.nextStageAllowed(3,300_000));
  assertFalse(ExplorationBattleTiming.nextStageAllowed(4,300_000));
  assertFalse(ExplorationBattleTiming.nextStageAllowed(1,715_000));
  assertFalse(ExplorationBattleTiming.nextStageAllowed(0,1_000));
 }
 @Test void onlyEarlySecondsPollForLateQuickConfirm() {
  assertTrue(ExplorationBattleTiming.mayInspectLateQuick(0));
  assertTrue(ExplorationBattleTiming.mayInspectLateQuick(14_000));
  assertFalse(ExplorationBattleTiming.mayInspectLateQuick(20_000));
  assertFalse(ExplorationBattleTiming.mayInspectLateQuick(180_000));
 }
}
