package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.RawImageData;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import static org.junit.jupiter.api.Assertions.*;

class ChiefOrderLiveFrameTest {
    @BeforeAll static void nativeOpenCv() throws Exception {
        OpenCvPatternLocator.loadNativeLibrary();
    }
    static RawImageData frame(String name) throws Exception {
        return resourceFrame("/chieforder/live-20261117/" + name);
    }

    static RawImageData resourceFrame(String name) throws Exception {
        BufferedImage src=ImageIO.read(Objects.requireNonNull(
                ChiefOrderLiveFrameTest.class.getResourceAsStream(name)));
        assertEquals(720,src.getWidth()); assertEquals(1280,src.getHeight());
        byte[] rgba=new byte[720*1280*4];int i=0;
        for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
            int rgb=src.getRGB(x,y);
            rgba[i++]=(byte)(rgb>>16);rgba[i++]=(byte)(rgb>>8);
            rgba[i++]=(byte)rgb;rgba[i++]=(byte)255;
        }
        return RawImageData.capture(rgba,720,1280,32);
    }

    @Test void actualChiefOrderMenuAndBookAreDifferentVerifiedPages() throws Exception {
        var list=frame("order-list.jpg");
        var book=frame("productivity-cost.png");
        assertTrue(ChiefOrderLiveFrame.isChiefOrderList(list));
        assertFalse(ChiefOrderLiveFrame.isChiefOrderList(book));
        assertTrue(ChiefOrderLiveFrame.isOrderBook(book,"Productivity Day"));
        assertFalse(ChiefOrderLiveFrame.isOrderBook(list,"Productivity Day"));
    }

    @Test void liveProductivityDayCostIsFiftyThousandNotFree() throws Exception {
        var inspection=ChiefOrderLiveFrame.inspect(frame("productivity-cost.png"),
                "Productivity Day");
        assertEquals(ChiefOrderLiveFrame.Action.COSTS_POINTS,inspection.action());
        assertEquals(50_000,inspection.cost());
        assertTrue(inspection.button().contains("Enact"),inspection.toString());
    }

    @Test void actualRushJobCooldownIsAlreadyUsedNotMissingIcon() throws Exception {
        var list = ChiefOrderLiveFrame.inspectList(frame("order-list.jpg"), "Rush Job");
        assertEquals(ChiefOrderLiveFrame.ListState.ON_COOLDOWN, list.state(), list.toString());
        // The banner is reliable even when OCR misreads 07:55 as 07:93.
        // No precise timer or Enact action may be invented from the digits.
        if (list.remaining() != null) assertTrue(list.remaining().toSeconds() > 0);
    }

    @Test void availableOrdersInTheSameSavedBoardStaySelectable() throws Exception {
        var board = frame("order-list.jpg");
        var urgent = ChiefOrderLiveFrame.inspectList(board, "Urgent Mobilization");
        var productivity = ChiefOrderLiveFrame.inspectList(board, "Productivity Day");
        assertEquals(ChiefOrderLiveFrame.ListState.AVAILABLE, urgent.state(), urgent.toString());
        assertEquals(ChiefOrderLiveFrame.ListState.AVAILABLE, productivity.state(), productivity.toString());
    }

    @Test void currentLiveBoardDistinguishesActiveFromCooldownOnEachOrder() throws Exception {
        var live = frame("order-list-active-redacted.jpg");
        var rush = ChiefOrderLiveFrame.inspectList(live, "Rush Job");
        var urgent = ChiefOrderLiveFrame.inspectList(live, "Urgent Mobilization");
        var productivity = ChiefOrderLiveFrame.inspectList(live, "Productivity Day");
        assertEquals(ChiefOrderLiveFrame.ListState.ON_COOLDOWN, rush.state(), rush.toString());
        assertEquals(ChiefOrderLiveFrame.ListState.ACTIVE, urgent.state(), urgent.toString());
        assertEquals(ChiefOrderLiveFrame.ListState.ON_COOLDOWN,
                productivity.state(), productivity.toString());
    }

    @Test void cityHomeAnchorNotConfusedWithChiefOrderBoardOrBook() throws Exception {
        assertTrue(ChiefOrderLiveFrame.isCityHome(
                resourceFrame("/initialize/home-city-anchor-redacted.png")));
        assertFalse(ChiefOrderLiveFrame.isCityHome(frame("order-list.jpg")));
        assertFalse(ChiefOrderLiveFrame.isCityHome(frame("productivity-cost.png")));
    }

    @Test void orderCooldownNeedsMatchingTileIdentityAndNotAnotherOrdersBanner() {
        var cooldown = ChiefOrderLiveFrame.classifyList(
                "Rush Job", "On cooldown", "07:55:13", "Rush Job");
        assertEquals(ChiefOrderLiveFrame.ListState.ON_COOLDOWN, cooldown.state());
        assertEquals(7 * 3600 + 55 * 60 + 13, cooldown.remaining().toSeconds());
        assertEquals(ChiefOrderLiveFrame.ListState.ON_COOLDOWN,
                ChiefOrderLiveFrame.classifyList("Rush Job", "f Oncooldown im", "07:93:13", "Rush Job").state());
        assertEquals(ChiefOrderLiveFrame.ListState.ACTIVE,
                ChiefOrderLiveFrame.classifyList("Rush Job", "Active", "00:12:45", "Rush Job").state());
        assertEquals(ChiefOrderLiveFrame.ListState.UNKNOWN,
                ChiefOrderLiveFrame.classifyList("Double Time", "On cooldown", "07:55:13", "Rush Job").state());
        assertEquals(ChiefOrderLiveFrame.ListState.UNKNOWN,
                ChiefOrderLiveFrame.classifyList("Rush Job", "unreadable", "07:55:13", "Rush Job").state());
        assertEquals(ChiefOrderLiveFrame.ListState.AVAILABLE,
                ChiefOrderLiveFrame.classifyList("Rush Job", "", "", "Rush Job").state());
    }

    @Test void noFreeActionWithoutExactTitleAndExplicitFreePrice() {
        assertEquals(ChiefOrderLiveFrame.Action.FREE,ChiefOrderLiveFrame.classify(
                "Productivity Day","Enact","","Free","Productivity Day").action());
        assertEquals(ChiefOrderLiveFrame.Action.UNVERIFIED,ChiefOrderLiveFrame.classify(
                "Productivity Day","Enact","","","Productivity Day").action());
        assertEquals(ChiefOrderLiveFrame.Action.UNVERIFIED,ChiefOrderLiveFrame.classify(
                "Productivity Day","Enact","0","0","Productivity Day").action());
        assertEquals(ChiefOrderLiveFrame.Action.UNVERIFIED,ChiefOrderLiveFrame.classify(
                "Urgent Mobilization","Enact","50,000","50,000","Productivity Day").action());
        assertEquals(ChiefOrderLiveFrame.Action.UNVERIFIED,ChiefOrderLiveFrame.classify(
                "Productivity Day","Cancel","50,000","50,000","Productivity Day").action());
    }

    @Test void doubleTimeIsIndependentMiddleRightTileAndHasCorrectPointParsing() throws Exception {
        var board=frame("order-list.jpg");
        var doubleTime=ChiefOrderLiveFrame.inspectList(board,"Double Time");
        Long actualPoints=ChiefOrderLiveFrame.availableOrderPoints(frame("order-list-balance.jpg"));
        // Native Tesseract varies across Windows/Linux. Missing OCR is a
        // supported fail-closed outcome: the purchase gate blocks spending.
        if (actualPoints != null) {
            assertTrue(actualPoints>=800_000L,"Visible balance must never be underestimated: "+actualPoints);
        }
        assertEquals(ChiefOrderLiveFrame.ListState.ON_COOLDOWN,doubleTime.state(),
                doubleTime.toString());
        assertEquals(4_200_000L,ChiefOrderLiveFrame.parseOrderPoints("4.2M"));
        assertEquals(800_000L,ChiefOrderLiveFrame.parseOrderPoints("800K"));
        assertEquals(800_000L,ChiefOrderLiveFrame.parseOrderPoints("800,000"));
        assertNull(ChiefOrderLiveFrame.parseOrderPoints("not recognized"));
        assertNull(ChiefOrderLiveFrame.parseOrderPoints(""));
    }

    @Test void malformedOrMissingFramesAreNeverAuthorizeToTap() {
        assertEquals(ChiefOrderLiveFrame.Action.UNVERIFIED,
                ChiefOrderLiveFrame.inspect(null,"Productivity Day").action());
    }
}
