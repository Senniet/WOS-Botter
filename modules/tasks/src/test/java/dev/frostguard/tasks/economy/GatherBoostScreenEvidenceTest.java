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
 @Test void onlyExplicitEightHour250GemPurchaseIsAccepted() {
  assertTrue(GatherBoostScreenEvidence.isObtainDialog("Obtain more"));
  assertTrue(GatherBoostScreenEvidence.isEightHourProduct("Gathering Speed Boost (8h)"));
  assertTrue(GatherBoostScreenEvidence.isAllowedPurchase("Buy & Use 250"));
  assertFalse(GatherBoostScreenEvidence.isAllowedPurchase("Buy & Use 600"));
  assertFalse(GatherBoostScreenEvidence.isAllowedPurchase("Use"));
  assertFalse(GatherBoostScreenEvidence.isPurchaseConfirmation("Confirm 600 gems"));
  assertTrue(GatherBoostScreenEvidence.isPurchaseConfirmation("Confirm 250 gems"));
 }
 @Test void noSuccessWithoutActiveBoostEvidence() {
  assertFalse(GatherBoostScreenEvidence.isAppliedBoost(""));
  assertFalse(GatherBoostScreenEvidence.isAppliedBoost("Use 8h Gathering Speed"));
  assertFalse(GatherBoostScreenEvidence.isAppliedBoost("Obtain 250 gems"));
  assertTrue(GatherBoostScreenEvidence.isAppliedBoost("Gathering Speed active"));
  assertTrue(GatherBoostScreenEvidence.isAppliedBoost("Gathering Speed remaining 07:15:00"));
 }
}
