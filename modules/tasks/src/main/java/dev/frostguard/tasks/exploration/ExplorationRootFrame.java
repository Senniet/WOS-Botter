package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * In Serve-EMU the bottom-left Exploration shortcut is reachable directly
 * from both City and World. Never switch City→World just to reach that same
 * shortcut. A root HUD must be positively identified before any shortcut tap.
 */
final class ExplorationRootFrame {
    private static final PointData START=new PointData(590,1145);
    private static final PointData END=new PointData(715,1275);
    enum Root { CITY, WORLD, UNKNOWN }
    private ExplorationRootFrame(){}
    static Root root(RawImageData f) {
        if(f==null || !f.isValid() || f.getWidth()!=720 || f.getHeight()!=1280)
            return Root.UNKNOWN;
        if(OpenCvPatternLocator.locatePattern(f,
                TemplatesEnum.SERVE_EMU_CITY_ANCHOR.getTemplate(),START,END,90).isFound())
            return Root.CITY;
        String world=TemplatesEnum.SERVE_EMU_WORLD_ANCHOR_CURRENT.getTemplate();
        if(OpenCvPatternLocator.locatePattern(f,world,START,END,90).isFound())
            return Root.WORLD;
        // Current World HUD was genuinely matched by multi-scale and mono
        // in Task Coach. Keep those *only* after current-colour misses;
        // require the same result on a second fresh frame before tapping.
        if(OpenCvPatternLocator.locatePatternMultiScale(f,world,START,END,90).isFound())
            return Root.WORLD;
        if(OpenCvPatternLocator.locatePatternMono(f,world,START,END,90).isFound())
            return Root.WORLD;
        return Root.UNKNOWN;
    }
    static boolean stable(Root before,Root after){
        return before!=Root.UNKNOWN && before==after;
    }
}
