package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/** One fresh ADB frame per normal Exploration result poll.
 * Color, grayscale and occasional scale matching run against the SAME pixels;
 * separate template attempts must not issue several independent remote
 * screencaps. Victory/Defeat are passive observations and authorize no taps
 * without a second fresh result frame and the contextual action check.
 */
final class ExplorationFastResultFrame {
    private static final PointData START=new PointData(0,0);
    private static final PointData END=new PointData(720,1280);
    enum State { FIGHTING, VICTORY, DEFEAT, UNKNOWN }
    private ExplorationFastResultFrame(){}

    static State classify(RawImageData f,boolean allowScaledFallback){
        if(f==null || !f.isValid() || f.getWidth()!=720 || f.getHeight()!=1280)
            return State.UNKNOWN;
        if(ExplorationBattleHudFrame.active(f))return State.FIGHTING;
        if(matches(f,TemplatesEnum.EXPLORATION_VICTORY,allowScaledFallback))return State.VICTORY;
        if(matches(f,TemplatesEnum.EXPLORATION_DEFEAT,allowScaledFallback))return State.DEFEAT;
        return State.UNKNOWN;
    }
    private static boolean matches(RawImageData frame,TemplatesEnum template,boolean scaled){
        String path=template.getTemplate();
        ImageSearchResultData match=OpenCvPatternLocator.locatePattern(frame,path,START,END,90);
        if(match.isFound())return true;
        // Real Task Coach observed 99.65%-score monochrome Victory. It
        // remains required, but shares this one screenshot with color/Defeat.
        match=OpenCvPatternLocator.locatePatternMono(frame,path,START,END,90);
        if(match.isFound())return true;
        return scaled && OpenCvPatternLocator.locatePatternMultiScale(
                frame,path,START,END,90).isFound();
    }
    static boolean sameConfirmedResult(State first,State second){
        return first==second && (first==State.VICTORY || first==State.DEFEAT);
    }
}
