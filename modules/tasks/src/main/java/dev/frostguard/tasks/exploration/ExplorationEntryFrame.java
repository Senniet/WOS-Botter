package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * After opening Exploration the loading/cart animation can show a temporary
 * header but NOT a usable Explore CTA. Recognize the current title and
 * the bottom Explore button *on the same fresh capture* before proceeding.
 */
final class ExplorationEntryFrame {
    private ExplorationEntryFrame(){}
    static boolean ready(RawImageData screen) {
        if(screen==null || !screen.isValid() || screen.getWidth()!=720 || screen.getHeight()!=1280)
            return false;
        return ExplorationPageFrame.title(screen)
                && OpenCvPatternLocator.locatePattern(screen,
                     TemplatesEnum.EXPLORATION_BUTTON.getTemplate(),
                     new PointData(160,1100),new PointData(540,1230),86).isFound();
    }
}
