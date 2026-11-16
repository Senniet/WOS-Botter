package dev.frostguard.engine.helper;

import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.RawImageData;
import java.util.function.Supplier;

/**
 * Reuse one remote capture for alternative recognition methods. A candidate
 * receives an independent second remote frame; misses do not. No taps here.
 */
final class ServeEmuFrameFallback {
    @FunctionalInterface interface Matcher {
        ImageSearchResultData locate(RawImageData frame, boolean monochrome);
    }
    private ServeEmuFrameFallback(){}

    static ImageSearchResultData confirm(Supplier<RawImageData> capture,
            Matcher matcher, Runnable preemption, Runnable settle, int distance) {
        preemption.run();
        RawImageData firstFrame=capture.get();
        if(firstFrame==null || !firstFrame.isValid())return null;
        ImageSearchResultData first=matcher.locate(firstFrame,false);
        boolean mono=first==null || !first.isFound();
        if(mono)first=matcher.locate(firstFrame,true);
        if(first==null || !first.isFound() || first.getPoint()==null)return null;
        settle.run();
        preemption.run();
        RawImageData secondFrame=capture.get();
        if(secondFrame==null || !secondFrame.isValid())return null;
        ImageSearchResultData second=matcher.locate(secondFrame,mono);
        return RecognitionConsensus.agrees(first,second,distance)?second:null;
    }
}
