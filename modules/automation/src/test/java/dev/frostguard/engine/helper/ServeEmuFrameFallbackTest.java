package dev.frostguard.engine.helper;

import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.RawImageData;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ServeEmuFrameFallbackTest {
    private RawImageData frame(){return RawImageData.capture(new byte[4*4*4],4,4,32);}

    @Test void missesUseOneCaptureDespiteBothRecognitionMethods() {
        AtomicInteger captures=new AtomicInteger(),checks=new AtomicInteger(),pauses=new AtomicInteger();
        var result=ServeEmuFrameFallback.confirm(()->{captures.incrementAndGet();return frame();},
                (f,mono)->{checks.incrementAndGet();return ImageSearchResultData.miss();},
                ()->{},pauses::incrementAndGet,18);
        assertNull(result);
        assertEquals(1,captures.get());
        assertEquals(2,checks.get());
        assertEquals(0,pauses.get());
    }

    @Test void positiveScaleRequiresNewFrameAtSamePosition() {
        AtomicInteger captures=new AtomicInteger(),checks=new AtomicInteger();
        var result=ServeEmuFrameFallback.confirm(()->{captures.incrementAndGet();return frame();},
                (f,mono)->{checks.incrementAndGet();return ImageSearchResultData.hit(350,1165,95.1);},
                ()->{},()->{},18);
        assertNotNull(result);
        assertEquals(2,captures.get());
        assertEquals(2,checks.get());
    }

    @Test void grayscaleOnFirstFrameAndConfirmOnSecondOnly() {
        AtomicInteger captures=new AtomicInteger();var methods=new ArrayList<Boolean>();
        var result=ServeEmuFrameFallback.confirm(()->{captures.incrementAndGet();return frame();},
                (f,mono)->{methods.add(mono);return mono?ImageSearchResultData.hit(33,52,96)
                        :ImageSearchResultData.miss();},()->{},()->{},18);
        assertNotNull(result);
        assertEquals(2,captures.get());
        assertEquals(java.util.List.of(false,true,true),methods);
    }

    @Test void movingMatchIsNeverConfirmed() {
        AtomicInteger captures=new AtomicInteger();
        assertNull(ServeEmuFrameFallback.confirm(()->frame(),(f,mono)->
                captures.incrementAndGet()==1?ImageSearchResultData.hit(100,300,96)
                        :ImageSearchResultData.hit(300,310,96),()->{},()->{},18));
    }

    @Test void invalidCaptureCannotAuthorizeAnyMatch(){
        AtomicInteger matches=new AtomicInteger();
        assertNull(ServeEmuFrameFallback.confirm(RawImageData::new,(f,mono)->{
            matches.incrementAndGet();return ImageSearchResultData.hit(1,1,99);},()->{},()->{},18));
        assertEquals(0,matches.get());
    }
}
