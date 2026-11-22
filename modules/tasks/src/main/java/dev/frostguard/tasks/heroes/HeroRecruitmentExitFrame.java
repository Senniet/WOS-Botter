package dev.frostguard.tasks.heroes;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * Header-only navigation for known Hero screens. Direct back is authorized
 * only by the corresponding Hero title AND a positively matched arrow.
 * The two header-arrow variants have different artwork on Recruitment
 * (score 87.8% alternate) and on Heroes list (score 92.9% classic).
 */
final class HeroRecruitmentExitFrame {
    private static final PointData HEAD_START=new PointData(0,0);
    private static final PointData HEAD_END=new PointData(110,110);
    private HeroRecruitmentExitFrame(){}

    static ImageSearchResultData back(RawImageData frame) {
        if(frame==null || !frame.isValid() || frame.getWidth()!=720 || frame.getHeight()!=1280)
            return ImageSearchResultData.miss();
        boolean recruit=OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_HERO_MODERN_RECRUIT_TITLE.getTemplate(),
                new PointData(390,70),new PointData(715,330),90).isFound()
                || OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_HERO_RECRUIT_TITLE.getTemplate(),
                new PointData(390,130),new PointData(710,315),90).isFound();
        boolean heroes=!recruit && OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_HEROES_PAGE_TITLE.getTemplate(),
                new PointData(65,0),new PointData(435,190),90).isFound();
        if(!recruit&&!heroes)return ImageSearchResultData.miss();
        ImageSearchResultData arrow=OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.GAME_HEADER_BACK_ARROW_CURRENT.getTemplate(),
                HEAD_START,HEAD_END,90);
        if(!arrow.isFound()) {
            arrow=OpenCvPatternLocator.locatePattern(frame,
                    TemplatesEnum.SERVE_EMU_HEADER_BACK_INTEL_2026.getTemplate(),
                    HEAD_START,HEAD_END,84);
        }
        if(!arrow.isFound() || arrow.getPoint()==null
                || arrow.getPoint().getX()<12 || arrow.getPoint().getX()>80
                || arrow.getPoint().getY()<10 || arrow.getPoint().getY()>76)
            return ImageSearchResultData.miss();
        return arrow;
    }
}
