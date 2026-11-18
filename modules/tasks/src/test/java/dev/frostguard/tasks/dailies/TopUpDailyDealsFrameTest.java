package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.tasks.exploration.ExplorationLatestFrameProbeTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Android 16 720x1280 screen; all personal/game profile pixels redacted. */
class TopUpDailyDealsFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }
    static RawImageData frame(String file) throws Exception {
        try(var in=TopUpDailyDealsFrameTest.class.getResourceAsStream("/daily-free-20261118/"+file)){
            assertNotNull(in,file);
            return ExplorationLatestFrameProbeTest.frame(ImageIO.read(in));
        }
    }
    @Test void cityCartIsOnlyValidFromRealCityHome() throws Exception {
        var city=frame("city-cart-redacted.png");
        assertTrue(TopUpDailyDealsFrame.city(city));
        assertTrue(TopUpDailyDealsFrame.cityCart(city).isFound());
        assertFalse(TopUpDailyDealsFrame.topUp(city));
        var store=frame("topup-menu-redacted.png");
        assertFalse(TopUpDailyDealsFrame.cityCart(store).isFound());
    }
    @Test void measuredTopUpHeaderAndDailyDealsTab() throws Exception {
        var f=frame("topup-menu-redacted.png");
        assertTrue(TopUpDailyDealsFrame.topUp(f));
        var tab=TopUpDailyDealsFrame.dailyDealsTab(f);
        assertTrue(tab.isFound(),"Daily Deals navigation tab must be an exact recognizable target");
        assertTrue(tab.getPoint().getY()>105&&tab.getPoint().getY()<200);
        assertFalse(TopUpDailyDealsFrame.dailyDeals(f),"A visible tab is not an open Daily Deals page");
    }
    @Test void actualClaimedFreeChestHasCooldownAndIsNeverReclicked() throws Exception {
        var f=frame("dailydeals-cooldown-redacted.png");
        assertTrue(TopUpDailyDealsFrame.topUp(f));
        assertTrue(TopUpDailyDealsFrame.dailyDeals(f));
        assertTrue(TopUpDailyDealsFrame.openedFreeChestOnCooldown(f));
        assertFalse(TopUpDailyDealsFrame.readyFreeChest(f));
        assertFalse(TopUpDailyDealsFrame.cityCart(f).isFound());
    }
}
