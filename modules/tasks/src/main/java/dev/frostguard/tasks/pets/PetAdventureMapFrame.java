package dev.frostguard.tasks.pets;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/** Verifies the map before releasing an aged ambiguous Start lock. */
final class PetAdventureMapFrame {
    private PetAdventureMapFrame() {}

    static boolean isMap(RawImageData screen) {
        return screen != null && screen.isValid()
                && screen.getWidth()==720 && screen.getHeight()==1280
                && OpenCvPatternLocator.locatePattern(screen,
                        TemplatesEnum.PET_ADVENTURE_MAP_TITLE_NOV_2026.getTemplate(),
                        new PointData(75, 0), new PointData(368, 87), 90).isFound();
    }
}
