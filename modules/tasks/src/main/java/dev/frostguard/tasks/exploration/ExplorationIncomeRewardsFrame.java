package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/** The separate Rewards sheet opened by Idle Income Claim in November 2026. */
final class ExplorationIncomeRewardsFrame {
    private ExplorationIncomeRewardsFrame() {}
    static boolean visible(RawImageData f) {
        if(f==null || !f.isValid() || f.getWidth()!=720 || f.getHeight()!=1280)return false;
        return OpenCvPatternLocator.locatePattern(f,
                TemplatesEnum.SERVE_EMU_EXPLORATION_IDLE_REWARDS_2026.getTemplate(),
                new PointData(150,235),new PointData(570,420),89).isFound()
                && OpenCvPatternLocator.locatePattern(f,
                TemplatesEnum.SERVE_EMU_EXPLORATION_IDLE_REWARDS_EXIT_2026.getTemplate(),
                new PointData(120,1110),new PointData(590,1265),88).isFound();
    }
}
