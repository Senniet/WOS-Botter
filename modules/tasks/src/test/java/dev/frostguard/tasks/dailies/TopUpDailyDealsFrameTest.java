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
    @Test void liveNov19TabRecognitionProbe() throws Exception {
        var f=frame("topup-tab-20261119-redacted.png");
        assertTrue(TopUpDailyDealsFrame.topUp(f));
        var result=TopUpDailyDealsFrame.match(f,dev.frostguard.api.configs.TemplatesEnum.TOP_UP_DAILY_DEALS_TAB_2026,
                10,87,716,195,0);
        System.out.println("TOPUP_NOV19_TAB_PROBE "+result);
        assertTrue(TopUpDailyDealsFrame.dailyDealsTab(f).isFound(),"Actual Daily Deals tab must be detected");
    }
    @Test void liveNov19DailyDealsPageProbe() throws Exception {
        var f=frame("daily-deals-current-20261119-redacted.png");
        assertTrue(TopUpDailyDealsFrame.topUp(f));
        assertTrue(TopUpDailyDealsFrame.dailyDeals(f));
        assertFalse(TopUpDailyDealsFrame.openedFreeChestOnCooldown(f));
        assertTrue(TopUpDailyDealsFrame.readyFreeChest(f),
                "The actual Nov 19 chest with the Free label must be claimable");
        for(int[] roi:new int[][]{{15,350,175,478},{35,410,160,478},{35,420,170,472},{40,412,130,471}}){
            var text=dev.frostguard.vision.ocr.OcrEngine.recognizeText(f,
                    new dev.frostguard.api.domain.PointData(roi[0],roi[1]),
                    new dev.frostguard.api.domain.PointData(roi[2],roi[3]),
                    dev.frostguard.api.domain.OcrSettingsData.configurator()
                            .textLayout(dev.frostguard.api.domain.OcrSettingsData.TextLayout.SINGLE_WORD)
                            .build());
            System.out.println("FREE_CHEST_OCR "+java.util.Arrays.toString(roi)+" = "+text);
        }
    }
    @Test void liveSecondCaptureReadyFreeChestWithChangingGlowIsStillRecognized()throws Exception{
        var f=frame("daily-deals-repeat-20261119-redacted.png");
        assertTrue(TopUpDailyDealsFrame.dailyDeals(f));
        System.out.println("FREE_REPEATED_NEW_TEMPLATE " + TopUpDailyDealsFrame.match(f,
            dev.frostguard.api.configs.TemplatesEnum.TOP_UP_DAILY_READY_CHEST_2026,18,338,175,485,0));
        System.out.println("FREE_REPEATED_OLD_TEMPLATE " + TopUpDailyDealsFrame.match(f,
            dev.frostguard.api.configs.TemplatesEnum.TOP_UP_DAILY_CLOSED_CHEST_2026,18,338,175,485,0));
        assertTrue(TopUpDailyDealsFrame.readyFreeChest(f),
             "Changing glow/animation must not cause the bot to miss the only Free chest");
    }
    @Test void actualHundredDiamondClaimReceiptIsRecognizedBeforeSafeExit()throws Exception{
        var receipt=frame("daily-free-claimed-receipt-20261119-redacted.png");
        assertTrue(TopUpDailyDealsFrame.claimedReceipt(receipt));
        assertFalse(TopUpDailyDealsFrame.readyFreeChest(receipt));
        assertFalse(TopUpDailyDealsFrame.dailyDeals(receipt));
        assertFalse(TopUpDailyDealsFrame.claimedReceipt(frame("daily-deals-current-20261119-redacted.png")));
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
