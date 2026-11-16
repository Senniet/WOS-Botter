package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.engine.diagnostics.TaskTemplateCoach;

/**
 * Exact 2026 quick-exploration state recognition. A confirmed quick-modal
 * click is the START of quick exploration, not evidence of claimed rewards.
 * These two states are independent of normal Squad Settings and Fight.
 */
final class QuickExplorationFrame {
    private QuickExplorationFrame() {}
    enum State { IN_PROGRESS, COMPLETED, OTHER }
    static State classify(RawImageData f) { return classify(f,null); }
    static State classify(RawImageData f,String device) {
        if (!viewport(f)) return State.OTHER;
        if (matched(f,TemplatesEnum.SERVE_EMU_QUICK_EXPLORATION_COMPLETED_2026,
                new PointData(125,250),new PointData(610,385),88,device)
                && matched(f,TemplatesEnum.SERVE_EMU_QUICK_EXPLORATION_EXIT_2026,
                new PointData(135,1145),new PointData(585,1265),86,device))return State.COMPLETED;
        if (matched(f,TemplatesEnum.SERVE_EMU_QUICK_EXPLORATION_PROGRESS_2026,
                new PointData(85,125),new PointData(635,260),88,device))return State.IN_PROGRESS;
        return State.OTHER;
    }
    static ImageSearchResultData skipButton(RawImageData f) { return skipButton(f,null); }
    static ImageSearchResultData skipButton(RawImageData f,String device) {
        if (classify(f,device)!=State.IN_PROGRESS)return ImageSearchResultData.miss();
        var left=new PointData(180,1100);var right=new PointData(535,1250);
        var tpl=TemplatesEnum.SERVE_EMU_QUICK_EXPLORATION_SKIP_2026;
        var result=OpenCvPatternLocator.locatePattern(f,tpl.getTemplate(),left,right,90);
        if(device!=null)TaskTemplateCoach.observe(device,tpl,"color",90,result,f,left,right);
        return result;
    }
    static PointData exitTarget(RawImageData f) { return exitTarget(f,null); }
    static PointData exitTarget(RawImageData f,String device) {
        return classify(f,device)==State.COMPLETED ? new PointData(355,1155):null;
    }
    private static boolean matched(RawImageData f,TemplatesEnum t,PointData a,PointData b,
                                   int threshold,String device){
        var result=OpenCvPatternLocator.locatePattern(f,t.getTemplate(),a,b,threshold);
        if(device!=null)TaskTemplateCoach.observe(device,t,"color",threshold,result,f,a,b);
        return result.isFound();
    }
    private static boolean viewport(RawImageData f){
        return f!=null&&f.isValid()&&f.getWidth()==720&&f.getHeight()==1280;
    }
}
