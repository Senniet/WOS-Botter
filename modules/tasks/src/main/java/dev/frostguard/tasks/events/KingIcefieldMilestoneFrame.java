package dev.frostguard.tasks.events;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * Read-only 720×1280 event-specific recognition, recorded in the actual
 * 18 November 2026 King of Icefield Stage 3 screen. The event is not the
 * Hall of Heroes shop; it awards earned stage milestones, not purchases.
 */
public final class KingIcefieldMilestoneFrame {
    private KingIcefieldMilestoneFrame() {}
    static boolean valid(RawImageData f) {
        return f != null && f.isValid() && f.getWidth()==720 && f.getHeight()==1280;
    }
    private static ImageSearchResultData find(RawImageData f,TemplatesEnum template,
                                               int x1,int y1,int x2,int y2,int confidence){
        if(!valid(f))return ImageSearchResultData.miss();
        return OpenCvPatternLocator.locatePattern(f,template.getTemplate(),
                new PointData(x1,y1),new PointData(x2,y2),confidence);
    }
    public static boolean eventsPanel(RawImageData f) {
        return find(f,TemplatesEnum.KING_ICEFIELDS_EVENTS_HEADER_2026,55,0,300,83,90).isFound();
    }
    public static boolean kingPage(RawImageData f) {
        return eventsPanel(f)
                && find(f,TemplatesEnum.KING_ICEFIELDS_TITLE_2026,22,180,520,305,91).isFound();
    }
    /** Inactive header tab; never click this as a generic event if hidden. */
    static ImageSearchResultData kingTab(RawImageData f) {
        if(!eventsPanel(f))return ImageSearchResultData.miss();
        return find(f,TemplatesEnum.KING_ICEFIELDS_TAB_INACTIVE_2026,180,88,690,194,87);
    }
    public static ImageSearchResultData unclaimedMilestone(RawImageData f) {
        if(!kingPage(f))return ImageSearchResultData.miss();
        // Stage reward cards have their action at x>=500. No package, rank,
        // upgrade or other events button is eligible.
        return find(f,TemplatesEnum.KING_ICEFIELDS_CLAIM_2026,495,644,688,1230,89);
    }
    public static boolean claimedAt(RawImageData f,PointData claimedPosition) {
        if(!kingPage(f)||claimedPosition==null)return false;
        int x=claimedPosition.getX(),y=claimedPosition.getY();
        return find(f,TemplatesEnum.KING_ICEFIELDS_CLAIMED_2026,
                Math.max(495,x-82),Math.max(650,y-74),
                Math.min(688,x+83),Math.min(1220,y+76),88).isFound();
    }
    static boolean eventClosed(RawImageData f) {
        return valid(f) && find(f,TemplatesEnum.SERVE_EMU_CITY_ANCHOR,
                581,1138,716,1277,90).isFound();
    }
}
