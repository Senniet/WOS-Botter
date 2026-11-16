package dev.frostguard.tasks.exploration;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.RecognitionConsensus;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Completes the optional Quick Exploration route, including its separate
 * reward receipt. Never assumes a confirmed quick modal means loot was claimed.
 */
final class QuickExplorationFlow {
    private QuickExplorationFlow(){}
    enum Outcome { REWARDS_CLOSED, UNVERIFIED }
    static Outcome finish(Supplier<RawImageData> capture,
                          Consumer<PointData> tap,Runnable settle) {
        return finish(capture,tap,settle,null);
    }
    static Outcome finish(Supplier<RawImageData> capture,
                          Consumer<PointData> tap,Runnable settle,String device) {
        boolean skipped=false;
        for (int i=0;i<45;i++) {
            RawImageData frame=capture.get();
            var stage=QuickExplorationFrame.classify(frame,device);
            if (stage==QuickExplorationFrame.State.COMPLETED) {
                settle.run();
                var second=capture.get();
                var target=QuickExplorationFrame.exitTarget(second,device);
                if (target==null)continue; // must have two stable reward screens
                tap.accept(target);           // one proven blank-space exit
                settle.run();
                var after=capture.get();
                if(QuickExplorationFrame.classify(after,device)==QuickExplorationFrame.State.COMPLETED)
                    return Outcome.UNVERIFIED;
                // Only a verified Exploration page proves the reward receipt
                // was closed and the flow can hand off to chest claims.
                for (int recovery=0;recovery<3;recovery++){
                    if (ExplorationPageFrame.title(after))return Outcome.REWARDS_CLOSED;
                    settle.run();
                    after=capture.get();
                }
                return Outcome.UNVERIFIED;
            }
            if (stage==QuickExplorationFrame.State.IN_PROGRESS && !skipped) {
                var first=QuickExplorationFrame.skipButton(frame,device);
                if (first.isFound()) {
                    settle.run();
                    var second=QuickExplorationFrame.skipButton(capture.get(),device);
                    if (RecognitionConsensus.agrees(first,second,14)) {
                        tap.accept(second.getPoint());
                        skipped=true;
                    }
                }
            }
            settle.run();
        }
        return Outcome.UNVERIFIED;
    }
}
