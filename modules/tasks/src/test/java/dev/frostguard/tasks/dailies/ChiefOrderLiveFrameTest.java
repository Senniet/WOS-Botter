package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.RawImageData;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChiefOrderLiveFrameTest {
    static RawImageData frame(String name) throws Exception {
        BufferedImage src=ImageIO.read(Objects.requireNonNull(
                ChiefOrderLiveFrameTest.class.getResourceAsStream(
                        "/chieforder/live-20261117/"+name)));
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

    @Test void malformedOrMissingFramesAreNeverAuthorizeToTap() {
        assertEquals(ChiefOrderLiveFrame.Action.UNVERIFIED,
                ChiefOrderLiveFrame.inspect(null,"Productivity Day").action());
    }
}
