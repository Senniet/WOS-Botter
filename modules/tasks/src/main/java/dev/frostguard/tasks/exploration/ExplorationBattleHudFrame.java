package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * Two independent active-battle controls (Auto and x2) distinguish real
 * combat from the results page. While present, do NOT repeatedly scan large
 * Victory/Defeat templates or send another Fight/Back action.
 * If the player changes speed or control layout, returns false and the
 * original resilient Victory/Defeat matching remains enabled.
 */
final class ExplorationBattleHudFrame {
    private ExplorationBattleHudFrame(){}
    static boolean active(RawImageData f){
        if(f==null || !f.isValid() || f.getWidth()!=720 || f.getHeight()!=1280)return false;
        return OpenCvPatternLocator.locatePattern(f,
                TemplatesEnum.SERVE_EMU_EXPLORATION_BATTLE_AUTO_2026.getTemplate(),
                new PointData(600,810),new PointData(715,915),93).isFound()
                && OpenCvPatternLocator.locatePattern(f,
                TemplatesEnum.SERVE_EMU_EXPLORATION_BATTLE_SPEED_2026.getTemplate(),
                new PointData(600,895),new PointData(715,1015),93).isFound();
    }
}
