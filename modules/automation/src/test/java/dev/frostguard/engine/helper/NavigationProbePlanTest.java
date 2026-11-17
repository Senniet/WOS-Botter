package dev.frostguard.engine.helper;

import dev.frostguard.engine.emulator.EmulatorType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NavigationProbePlanTest {
 @Test void skipRedundantReconnectOnVerifiedServeEmuRootOnly() {
  assertFalse(NavigationProbePlan.requiresFullReconnect(EmulatorType.SERVE_EMU,true,false));
  assertFalse(NavigationProbePlan.requiresFullReconnect(EmulatorType.SERVE_EMU,false,true));
  assertTrue(NavigationProbePlan.requiresFullReconnect(EmulatorType.SERVE_EMU,false,false));
 }
 @Test void otherBackendsKeepOriginalSafetyGuard() {
  for(var backend:EmulatorType.values()) {
   if(backend==EmulatorType.SERVE_EMU)continue;
   assertTrue(NavigationProbePlan.requiresFullReconnect(backend,true,false),backend.name());
   assertTrue(NavigationProbePlan.requiresFullReconnect(backend,false,false),backend.name());
  }
  assertTrue(NavigationProbePlan.requiresFullReconnect(null,false,false));
 }
}
