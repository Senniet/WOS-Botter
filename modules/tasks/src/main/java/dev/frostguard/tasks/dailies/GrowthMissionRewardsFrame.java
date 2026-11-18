package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/** Strict positive evidence for Growth/Chapter Missions in 720x1280. */
public final class GrowthMissionRewardsFrame {
    private GrowthMissionRewardsFrame(){}
    static boolean valid(RawImageData f){
        return f!=null&&f.isValid()&&f.getWidth()==720&&f.getHeight()==1280;
    }
    private static ImageSearchResultData match(RawImageData f,TemplatesEnum key,
                                               int x1,int y1,int x2,int y2,int conf){
        return valid(f)?OpenCvPatternLocator.locatePattern(f,key.getTemplate(),
                new PointData(x1,y1),new PointData(x2,y2),conf):ImageSearchResultData.miss();
    }
    public static boolean growth(RawImageData f){
        return match(f,TemplatesEnum.GROWTH_MISSIONS_TITLE_2026,145,87,580,177,90).isFound();
    }
    public static boolean chapter(RawImageData f){
        return match(f,TemplatesEnum.GROWTH_MISSIONS_CHAPTER_TITLE_2026,145,87,580,177,90).isFound();
    }
    public static boolean daily(RawImageData f){
        return match(f,TemplatesEnum.DAILY_MISSION_SCREEN_TITLE,110,75,610,225,90).isFound();
    }
    public static boolean missionScreen(RawImageData f){return growth(f)||chapter(f)||daily(f);}
    public static ImageSearchResultData growthClaim(RawImageData f){
        if(!growth(f))return ImageSearchResultData.miss();
        // Only the green 'Claim' word inside a row, never a blue 'Go'.
        return match(f,TemplatesEnum.GROWTH_MISSIONS_CLAIM_2026,508,235,692,1095,89);
    }
    public static ImageSearchResultData chapterRewardClaim(RawImageData f){
        if(!chapter(f))return ImageSearchResultData.miss();
        // Chapter has a SINGLE aggregate bottom reward CTA. Never the blue
        // 'Go' button in an incomplete chapter mission row.
        return match(f,TemplatesEnum.GROWTH_MISSIONS_CLAIM_2026,509,917,697,1118,89);
    }
    public static boolean city(RawImageData f){
        return match(f,TemplatesEnum.SERVE_EMU_CITY_ANCHOR,585,1135,719,1279,90).isFound();
    }
}
