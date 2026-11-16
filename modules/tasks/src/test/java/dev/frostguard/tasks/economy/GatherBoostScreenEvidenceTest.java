package dev.frostguard.tasks.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GatherBoostScreenEvidenceTest {
 @Test void menusCannotBeAssumedFromBlindTaps() {
  assertFalse(GatherBoostScreenEvidence.isGrowthTitle("Chief Profile"));
  assertTrue(GatherBoostScreenEvidence.isGrowthTitle("Growth"));
  assertFalse(GatherBoostScreenEvidence.isGatheringSpeedScreen("Growth"));
  assertTrue(GatherBoostScreenEvidence.isGatheringSpeedScreen("Gathering Speed"));
 }
 @Test void noSuccessWithoutActiveBoostEvidence() {
  assertFalse(GatherBoostScreenEvidence.isAppliedBoost(""));
  assertFalse(GatherBoostScreenEvidence.isAppliedBoost("Use 8h Gathering Speed"));
  assertFalse(GatherBoostScreenEvidence.isAppliedBoost("Obtain 250 gems"));
  assertTrue(GatherBoostScreenEvidence.isAppliedBoost("Gathering Speed active"));
  assertTrue(GatherBoostScreenEvidence.isAppliedBoost("Gathering Speed remaining 07:15:00"));
 }
}
