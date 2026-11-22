package dev.frostguard.tasks.pets;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * Distinguish the player's own My Island view from World, City and alliance
 * island screens BEFORE deciding an empty Life Essence capture means complete.
 * The same title appears on real August/September 2026 720x1280 frames.
 */
final class LifeEssenceIslandFrame {
    private LifeEssenceIslandFrame() {}

    static boolean ownIsland(RawImageData frame) {
        return frame != null && frame.isValid()
                && frame.getWidth() == 720 && frame.getHeight() == 1280
                && OpenCvPatternLocator.locatePattern(frame,
                        TemplatesEnum.LIFE_ESSENCE_MY_ISLAND_TITLE_2026.getTemplate(),
                        new PointData(72, 5), new PointData(260, 72), 87).isFound();
    }
}
