package dev.frostguard.tasks.pets;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * Account-unlocked 2026 World map alliance Tree of Life assistance.
 * Does NOT depend on the account owning/unlocking Daybreak Island.
 * Every action requires positively recognized screen-specific evidence.
 */
final class AllianceTreeCareFrame {
    private AllianceTreeCareFrame() {}
    private static boolean valid(RawImageData f){return f!=null&&f.isValid()&&f.getWidth()==720&&f.getHeight()==1280;}
    private static ImageSearchResultData match(RawImageData f,TemplatesEnum sprite,
            int left,int top,int right,int bottom,int threshold){
        return valid(f)?OpenCvPatternLocator.locatePattern(f,sprite.getTemplate(),
                new PointData(left,top),new PointData(right,bottom),threshold):ImageSearchResultData.miss();
    }
    static boolean world(RawImageData f){
        return match(f,TemplatesEnum.SERVE_EMU_WORLD_ANCHOR_CURRENT,580,1135,715,1278,90).isFound();
    }
    static ImageSearchResultData worldCareBadge(RawImageData f){
        if(!world(f))return ImageSearchResultData.miss();
        // Only on-map alliance badges; exclude all floating UI / Alliance
        // Help buttons and the bottom navigation bar.
        return match(f,TemplatesEnum.ALLIANCE_TREE_WORLD_CARE_BADGE_2026,
                55,180,628,1090,89);
    }
    static boolean visitQuestion(RawImageData f){
        return match(f,TemplatesEnum.ALLIANCE_TREE_VISIT_QUESTION_2026,
                170,515,555,667,92).isFound();
    }
    static boolean island(RawImageData f){
        return match(f,TemplatesEnum.ALLIANCE_TREE_ISLAND_HUD_2026,
                609,1097,700,1200,89).isFound()&&!world(f);
    }
    static ImageSearchResultData islandCareBadge(RawImageData f){
        if(!island(f))return ImageSearchResultData.miss();
        // On different islands the UI balloon drifts several pixels and its
        // rendering changes during the tree animation. Both captures are
        // from real member islands; only accept a strong exact sprite match.
        var standard=match(f,TemplatesEnum.ALLIANCE_TREE_ISLAND_CARE_BADGE_2026,
                120,135,610,680,91);
        var animated=match(f,TemplatesEnum.ALLIANCE_TREE_ISLAND_CARE_ANIMATED_2026,
                120,135,610,680,91);
        return standard.isFound() && (!animated.isFound()
                || standard.getMatchScore()>=animated.getMatchScore())?standard:animated;
    }
    static boolean helpedToast(RawImageData f){
        return island(f)&&match(f,TemplatesEnum.ALLIANCE_TREE_CARE_SUCCESS_TOAST_2026,
                48,410,672,540,88).isFound();
    }
    static int availableCareAttempts(RawImageData f){
        if(!island(f))return -1;
        // The same numeral renders with slightly different anti-aliasing on
        // individual allies' islands (84.9% match for a valid 2/3). Compare
        // only the FIRST digit at the fixed HUD location; the denominator 3
        // is excluded. Require both adequate confidence AND a clear lead.
        TemplatesEnum[] digits={TemplatesEnum.ALLIANCE_TREE_ATTEMPTS_3_2026,
                TemplatesEnum.ALLIANCE_TREE_ATTEMPTS_2_2026,
                TemplatesEnum.ALLIANCE_TREE_ATTEMPTS_1_2026};
        int[] values={3,2,1};
        int winner=-1;
        double best=0,runnerUp=0;
        for(int i=0;i<digits.length;i++){
            double score=match(f,digits[i],618,1194,657,1241,0).getMatchScore();
            if(score>best){runnerUp=best;best=score;winner=values[i];}
            else if(score>runnerUp)runnerUp=score;
        }
        return best>=80 && best-runnerUp>=11 ? winner : -1;
    }
}
