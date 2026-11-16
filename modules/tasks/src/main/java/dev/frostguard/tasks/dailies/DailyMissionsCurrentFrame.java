package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/** Source-verified 2026 Daily Missions title and the actual top-right X. */
final class DailyMissionsCurrentFrame {
    private static final PointData TITLE_A=new PointData(120,80),TITLE_B=new PointData(600,225);
    private static final PointData CLOSE_A=new PointData(644,86),CLOSE_B=new PointData(718,165);
    private DailyMissionsCurrentFrame(){}

    static boolean title(RawImageData screen){
        return valid(screen) && OpenCvPatternLocator.locatePattern(screen,
                TemplatesEnum.DAILY_MISSION_SCREEN_TITLE.getTemplate(),TITLE_A,TITLE_B,90).isFound();
    }
    static ImageSearchResultData close(RawImageData screen){
        if(!title(screen))return ImageSearchResultData.miss();
        return OpenCvPatternLocator.locatePattern(screen,
                TemplatesEnum.SERVE_EMU_DAILY_CLOSE_NOV_2026.getTemplate(),CLOSE_A,CLOSE_B,90);
    }
    private static boolean valid(RawImageData f){
        return f!=null && f.isValid() && f.getWidth()==720 && f.getHeight()==1280;
    }
}
