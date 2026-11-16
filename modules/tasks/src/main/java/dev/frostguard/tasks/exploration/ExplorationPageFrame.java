package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/** A positive, current 720x1280 Exploration title + back in one frame. */
final class ExplorationPageFrame {
    private ExplorationPageFrame(){}
    static boolean title(RawImageData f){
        return f!=null && f.isValid() && f.getWidth()==720 && f.getHeight()==1280
                && OpenCvPatternLocator.locatePattern(f,
                TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE_NOV_2026.getTemplate(),
                new PointData(65,0),new PointData(335,115),92).isFound();
    }
    static ImageSearchResultData verifiedBackCandidate(RawImageData f){
        if(!title(f))return ImageSearchResultData.miss();
        return OpenCvPatternLocator.locatePattern(f,
                TemplatesEnum.GAME_HEADER_BACK_ARROW_CURRENT.getTemplate(),
                new PointData(0,0),new PointData(110,105),90);
    }
}
