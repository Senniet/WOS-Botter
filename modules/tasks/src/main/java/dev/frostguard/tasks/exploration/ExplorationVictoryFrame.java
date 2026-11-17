package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/** Normal battle Victory has a positive Return to City CTA, not Tap anywhere. */
final class ExplorationVictoryFrame {
    private ExplorationVictoryFrame(){}
    static ImageSearchResultData returnToCity(RawImageData f){
        if(f==null || !f.isValid() || f.getWidth()!=720 || f.getHeight()!=1280)
            return ImageSearchResultData.miss();
        if(!OpenCvPatternLocator.locatePattern(f,
                TemplatesEnum.EXPLORATION_VICTORY.getTemplate(),
                new PointData(80,190),new PointData(645,550),90).isFound())
            return ImageSearchResultData.miss();
        return OpenCvPatternLocator.locatePattern(f,
                TemplatesEnum.SERVE_EMU_EXPLORATION_VICTORY_RETURN_CITY_2026.getTemplate(),
                new PointData(0,920),new PointData(420,1085),90);
    }
}
