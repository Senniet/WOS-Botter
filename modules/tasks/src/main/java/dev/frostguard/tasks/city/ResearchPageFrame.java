package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/** Task-specific header and Back confirmation; same 720x1280 game frame. */
final class ResearchPageFrame {
    private ResearchPageFrame() {}
    static boolean correctSize(RawImageData screen) {
        return screen!=null&&screen.isValid()&&screen.getWidth()==720&&screen.getHeight()==1280;
    }
    static boolean titleVisible(RawImageData screen) {
        return correctSize(screen) && OpenCvPatternLocator.locatePattern(screen,
                TemplatesEnum.SERVE_EMU_RESEARCH_TITLE.getTemplate(),
                new PointData(75,0),new PointData(380,100),90).isFound();
    }
    static ImageSearchResultData safeBack(RawImageData screen) {
        if (!titleVisible(screen)) return ImageSearchResultData.miss();
        return OpenCvPatternLocator.locatePattern(screen,
                TemplatesEnum.GAME_HEADER_BACK_ARROW_CURRENT.getTemplate(),
                new PointData(0,0),new PointData(115,105),90);
    }
}
