package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.color.GameColors;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.EnumSet;
import java.util.Set;

/**
 * Screenshot recognition for 2026 Daily sidebar. Small green 'Ready',
 * 'Can contribute 18/25', and 'Intel Gain: 9' text frequently disappears in
 * Tesseract OCR; the rendered vivid-green status plus the specific row icon
 * is reliable. No row is dispatched on green alone.
 */
final class DailyReadyStatusFrame {
    private DailyReadyStatusFrame(){}

    static Set<TpDailyTaskEnum> actionable(RawImageData frame){
        EnumSet<TpDailyTaskEnum> found=EnumSet.noneOf(TpDailyTaskEnum.class);
        if(!DailyOverviewFrame.isDailyOverview(frame))return found;
        if(ready(frame,TemplatesEnum.SIDEBAR_DAILY_PET_ADVENTURE))
            found.add(TpDailyTaskEnum.PET_ADVENTURE);
        if(ready(frame,TemplatesEnum.SIDEBAR_DAILY_ALLIANCE_CONTRIBUTION_2026))
            found.add(TpDailyTaskEnum.ALLIANCE_TECH);
        if(ready(frame,TemplatesEnum.SIDEBAR_DAILY_LIGHTHOUSE_INTEL))
            found.add(TpDailyTaskEnum.INTEL);
        // Arena's configured due-time is not overridden here.
        // Online Rewards needs actual Ready/Claim status text verified on a
        // current Daily screenshot; a timer or row title is insufficient.
        return found;
    }

    static boolean ready(RawImageData frame,TemplatesEnum icon){
        if(frame==null||!frame.isValid()||frame.getWidth()!=720||frame.getHeight()!=1280)return false;
        ImageSearchResultData anchor=OpenCvPatternLocator.locatePattern(frame,
                icon.getTemplate(),new PointData(0,270),new PointData(145,990),87);
        if(!anchor.isFound())return false;
        int y=anchor.getPoint().getY();
        int greens=0;
        var image=dev.frostguard.vision.convert.ImageConverter.toBufferedImage(frame);
        for(int row=Math.max(275,y+11);row<Math.min(970,y+44);row++)
            for(int x=145;x<425;x++)
                if(GameColors.isVividGreen(image.getRGB(x,row)))greens++;
        return greens>=85;
    }
}
