package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.vision.convert.ImageConverter;

/** Optional 160-point chest: require progress bar past 160 AND unopened box. */
final class DailyMilestone160Frame {
    private DailyMilestone160Frame(){}
    static ImageSearchResultData ready(RawImageData f){
        if(!DailyMissionsCurrentFrame.title(f))return ImageSearchResultData.miss();
        // Current screen's progress bar crosses the 160-point marker around
        // x=372. At 145 points the pixels there are blue; at 175 yellow.
        var image=ImageConverter.toBufferedImage(f);
        int yellow=0;
        for(int y=338;y<=346;y++)for(int x=366;x<=382;x++){
            int rgb=image.getRGB(x,y),r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
            if(r>175 && g>145 && b<150 && r>b*1.5)yellow++;
        }
        if(yellow<85)return ImageSearchResultData.miss();
        return OpenCvPatternLocator.locatePattern(f,
                TemplatesEnum.SERVE_EMU_DAILY_160_CLOSED_2026.getTemplate(),
                new PointData(315,378),new PointData(445,495),89);
    }
}
