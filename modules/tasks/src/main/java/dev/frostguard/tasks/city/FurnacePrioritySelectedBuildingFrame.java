package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/** Explicit embassy title evidence when OCR cannot read the name. */
final class FurnacePrioritySelectedBuildingFrame {
    private FurnacePrioritySelectedBuildingFrame() {}
    static boolean embassy(RawImageData frame) {
        if(frame==null || !frame.isValid()
                || frame.getWidth()!=720 || frame.getHeight()!=1280) return false;
        ImageSearchResultData title=OpenCvPatternLocator.locatePattern(
                frame,TemplatesEnum.EMBASSY_SELECTED_TITLE_NOV_2026.getTemplate(),
                new PointData(255,500),new PointData(535,595),86);
        return title.isFound();
    }
}
