package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/** Only the exact modern Upgrade Bonus panel may use its top-right X. */
final class CityUpgradeBonusExitFrame {
    private CityUpgradeBonusExitFrame(){}

    static ImageSearchResultData verifiedClose(RawImageData frame) {
        if(frame==null || !frame.isValid() || frame.getWidth()!=720 || frame.getHeight()!=1280)
            return ImageSearchResultData.miss();
        var title=OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_BUILDING_UPGRADE_BONUS_TITLE_NOV_2026.getTemplate(),
                new PointData(205,225),new PointData(510,340),90);
        if(!title.isFound())return ImageSearchResultData.miss();
        var close=OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_BUILDING_UPGRADE_BONUS_CLOSE_NOV_2026.getTemplate(),
                new PointData(610,145),new PointData(710,244),90);
        if(!close.isFound() || close.getPoint()==null
                || close.getPoint().getX()<630 || close.getPoint().getX()>700
                || close.getPoint().getY()<165 || close.getPoint().getY()>235)
            return ImageSearchResultData.miss();
        return close;
    }
}
