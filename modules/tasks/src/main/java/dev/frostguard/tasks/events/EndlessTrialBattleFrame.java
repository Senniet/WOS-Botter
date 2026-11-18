package dev.frostguard.tasks.events;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * Positive, page-specific UI recognition from live 720x1280 Windhowler screens.
 * Go navigates to the world boss; Attack opens hero formation; Deploy dispatches
 * one march; the damage-result popup confirms the battle has finished.
 */
public final class EndlessTrialBattleFrame {
    private EndlessTrialBattleFrame() {}

    private static boolean valid(RawImageData frame) {
        return frame != null && frame.isValid() && frame.getWidth() == 720 && frame.getHeight() == 1280;
    }

    private static boolean matches(RawImageData frame, TemplatesEnum template,
                                   int left,int top,int right,int bottom,int threshold) {
        return valid(frame) && OpenCvPatternLocator.locatePattern(frame,template.getTemplate(),
                new PointData(left,top), new PointData(right,bottom), threshold).isFound();
    }

    public static boolean isWorldBossCard(RawImageData frame) {
        return matches(frame,TemplatesEnum.ENDLESS_TRIAL_BOSS_TITLE,160,745,590,970,90);
    }

    public static boolean canAttackWorldBoss(RawImageData frame) {
        return isWorldBossCard(frame)
                && matches(frame,TemplatesEnum.ENDLESS_TRIAL_ATTACK_BUTTON,325,970,600,1100,90);
    }

    public static boolean isWindhowlerFormation(RawImageData frame) {
        return matches(frame,TemplatesEnum.ENDLESS_TRIAL_FORMATION_TITLE,35,0,570,130,90);
    }

    public static boolean canDeploy(RawImageData frame) {
        return isWindhowlerFormation(frame)
                && matches(frame,TemplatesEnum.ENDLESS_TRIAL_DEPLOY_BUTTON,180,1110,540,1274,90);
    }

    /** World HUD City button is a positive root signal; not visible under result dialogs. */
    public static boolean isWorldRoot(RawImageData frame) {
        return matches(frame, TemplatesEnum.ENDLESS_TRIAL_WORLD_CITY_LABEL,
                582,1205,714,1280,88);
    }

    public static boolean isDamageResult(RawImageData frame) {
        return matches(frame,TemplatesEnum.ENDLESS_TRIAL_DAMAGE_RESULT,110,485,630,685,90)
                && matches(frame,TemplatesEnum.ENDLESS_TRIAL_RESULT_CONFIRM,160,800,560,1000,90);
    }
}
