package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * Positive 720x1280 Cave of Monsters state detection from actual 19-Nov-2026
 * Android 16 gameplay. Never interpret a generic blue button as a Challenge.
 */
public final class DailyLabyrinthFrame {
    private DailyLabyrinthFrame(){}
    private static boolean valid(RawImageData frame){
        return frame!=null && frame.isValid() && frame.getWidth()==720 && frame.getHeight()==1280;
    }
    private static ImageSearchResultData find(RawImageData f,TemplatesEnum template,
                                               int x1,int y1,int x2,int y2,int confidence){
        return valid(f)?OpenCvPatternLocator.locatePattern(f,template.getTemplate(),
                new PointData(x1,y1),new PointData(x2,y2),confidence):ImageSearchResultData.miss();
    }
    public static boolean overviewHeader(RawImageData f){
        return find(f,TemplatesEnum.LABYRINTH_CURRENT_OVERVIEW_TITLE,55,0,405,104,91).isFound();
    }
    public static boolean overview(RawImageData f){
        // The cave banner contains an animated countdown below the title.
        // Match *only* the stable Cave of Monsters text, not the timer.
        return overviewHeader(f)
            && find(f,TemplatesEnum.LABYRINTH_CURRENT_CAVE_PORTAL,32,417,465,555,85).isFound();
    }
    private static boolean caveHeader(RawImageData f){
        return find(f,TemplatesEnum.LABYRINTH_CURRENT_CAVE_TITLE,45,0,414,99,90).isFound();
    }
    public static boolean cave(RawImageData f){
        return caveHeader(f)
            && find(f,TemplatesEnum.LABYRINTH_CURRENT_REMAINING_ATTEMPTS,170,1070,520,1198,88).isFound();
    }
    public static ImageSearchResultData challenge(RawImageData f){
        if(!cave(f))return ImageSearchResultData.miss();
        return find(f,TemplatesEnum.LABYRINTH_CURRENT_CHALLENGE,203,1154,530,1279,88);
    }
    public static boolean formation(RawImageData f){
        return caveHeader(f)
            && find(f,TemplatesEnum.LABYRINTH_CURRENT_FORMATION_INFANTRY,98,307,490,468,87).isFound();
    }
    public static ImageSearchResultData deploy(RawImageData f){
        if(!formation(f))return ImageSearchResultData.miss();
        return find(f,TemplatesEnum.LABYRINTH_CURRENT_FORMATION_DEPLOY,390,1130,715,1279,90);
    }
    public static boolean victory(RawImageData f){
        return find(f,TemplatesEnum.LABYRINTH_CURRENT_VICTORY,180,265,560,492,90).isFound()
            && find(f,TemplatesEnum.LABYRINTH_CURRENT_RESULT_NEXT,385,778,700,1004,89).isFound();
    }
    /** Positive defeat state: BOTH the silver Defeat heading and its blue
     * Retry CTA must be present. Retry is evidence ONLY, never a tap target.
     * The dialog dismisses via a neutral area at (354,810), proven on ADB.
     */
    public static boolean defeat(RawImageData f){
        return find(f,TemplatesEnum.LABYRINTH_CURRENT_DEFEAT,190,260,545,475,90).isFound()
            && find(f,TemplatesEnum.LABYRINTH_CURRENT_DEFEAT_RETRY,380,905,690,1090,89).isFound();
    }
    public static ImageSearchResultData nextStage(RawImageData f){
        if(!victory(f))return ImageSearchResultData.miss();
        return find(f,TemplatesEnum.LABYRINTH_CURRENT_RESULT_NEXT,385,778,700,1004,89);
    }
    public static boolean home(RawImageData f){
        return find(f,TemplatesEnum.SERVE_EMU_CITY_ANCHOR,575,1138,716,1279,90).isFound();
    }
}
