package dev.frostguard.engine.helper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import java.awt.Color;
import java.awt.image.BufferedImage;
import dev.frostguard.engine.emulator.EmulatorController;
import dev.frostguard.engine.error.HomeNotFoundException;
import dev.frostguard.engine.nav.SidebarDestination;
import dev.frostguard.engine.nav.SidebarRowLookup;
import dev.frostguard.engine.schedule.LaunchPoint;

class IntelScreenHelperTest {

    private static final AccountDescriptor PROFILE =
            new AccountDescriptor(999L, "Synthetic Intel", "review", true, 0L, 0L);

    @Test
    void anchoredGreenLabelIsDetectedOnlyInsideLighthouseStatusLine() {
        var frame = new BufferedImage(720, 1280, BufferedImage.TYPE_INT_RGB);
        for (int y = 836; y < 849; y++) {
            for (int x = 130; x < 265; x++) {
                frame.setRGB(x, y, new Color(28, 183, 46).getRGB());
            }
        }
        var row = ImageSearchResultData.hit(42, 825, 99);
        assertTrue(IntelScreenHelper.availableGreenPixels(frame,
                IntelScreenHelper.intelGainStatusArea(row)) > 150);
        var otherRow = ImageSearchResultData.hit(42, 700, 99);
        assertFalse(IntelScreenHelper.availableGreenPixels(frame,
                IntelScreenHelper.intelGainStatusArea(otherRow)) > 150);
    }

    @Test
    void sidebarTransitionFailureCannotMasqueradeAsAnAbsentIntelRow() {
        assertThrows(HomeNotFoundException.class,
                () -> IntelScreenHelper.requireSidebarReady(
                        SidebarRowLookup.sidebarUnavailable()));
    }

    @Test
    void completedScanCanStillReportThatTheIntelRowIsAbsent() {
        assertFalse(IntelScreenHelper.requireSidebarReady(
                SidebarRowLookup.fromRow(null)).isFound());
    }

    @Test
    void productionEntryRejectsAnUnavailableSidebar() {
        RecordingNavigationHelper navigation =
                new RecordingNavigationHelper(SidebarRowLookup.sidebarUnavailable());
        IntelScreenHelper helper = new IntelScreenHelper(
                null, "review", null, navigation, PROFILE);

        assertThrows(HomeNotFoundException.class, helper::enterIntelFromDailyIfAvailable);
        assertTrue(navigation.locationChecked);
        assertFalse(navigation.closeCalled);
    }

    @Test
    void activeCycleReentryUsesWildernessWithoutConsultingDaily() {
        RecordingNavigationHelper navigation =
                new RecordingNavigationHelper(SidebarRowLookup.sidebarUnavailable());
        IntelScreenHelper helper = new IntelScreenHelper(
                null, "review", null, navigation, PROFILE);

        helper.resumeIntelCycleFromWilderness();

        assertTrue(navigation.intelOpened);
        assertFalse(navigation.locationChecked);
        assertFalse(navigation.sidebarLookupCalled);
        assertFalse(navigation.closeCalled);
    }

    @Test
    void activeCycleReentryRejectsAnUnverifiedIntelTransition() {
        RecordingNavigationHelper navigation =
                new RecordingNavigationHelper(SidebarRowLookup.sidebarUnavailable());
        navigation.intelOpenResult = false;
        IntelScreenHelper helper = new IntelScreenHelper(
                null, "review", null, navigation, PROFILE);

        assertThrows(HomeNotFoundException.class, helper::resumeIntelCycleFromWilderness);
        assertTrue(navigation.intelOpened);
    }

    private static final class RecordingNavigationHelper extends NavigationHelper {
        private final SidebarRowLookup lookup;
        private boolean locationChecked;
        private boolean sidebarLookupCalled;
        private boolean closeCalled;
        private boolean intelOpened;
        private boolean intelOpenResult = true;

        private RecordingNavigationHelper(SidebarRowLookup lookup) {
            super((EmulatorController) null, "review", PROFILE);
            this.lookup = lookup;
        }

        @Override
        public void ensureCorrectScreenLocation(LaunchPoint launchPoint) {
            locationChecked = true;
        }

        @Override
        public SidebarRowLookup findSidebarDestinationRowWithStatus(
                SidebarDestination destination) {
            sidebarLookupCalled = true;
            return lookup;
        }

        @Override
        public boolean closeSidebar() {
            closeCalled = true;
            return true;
        }

        @Override
        public boolean openIntelFromWilderness() {
            intelOpened = true;
            return intelOpenResult;
        }
    }
}
