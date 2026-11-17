package dev.frostguard.tasks.exploration;

import dev.frostguard.api.domain.RawImageData;
import java.util.function.Supplier;

/** Passive bounded screen transition check. NEVER trigger a generic nav
 * reset merely because the title animation is still transitioning. */
final class ExplorationEntryWait {
    private ExplorationEntryWait(){}
    static boolean untilReady(Supplier<RawImageData> capture,Runnable settle,int maxFrames) {
        boolean prior=false;
        for(int check=0;check<maxFrames;check++){
            boolean current=ExplorationEntryFrame.ready(capture.get());
            if(current && prior)return true;
            prior=current;
            if(check+1<maxFrames)settle.run();
        }
        return false;
    }
}
