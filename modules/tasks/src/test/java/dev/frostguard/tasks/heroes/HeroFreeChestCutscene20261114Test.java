package dev.frostguard.tasks.heroes;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroFreeChestCutscene20261114Test {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void identifiesChestAnimationFromFailedFreeRecruitClaim() throws Exception {
        assertTrue(cutscene("free-chest-redacted.png"));
    }

    @Test void cityWorldAndRewardsCannotTriggerAnotherChestTap() throws Exception {
        for(String frame:new String[]{"city-redacted.png","world-redacted.png",
                "hero-rewards-redacted.png"}) assertFalse(cutscene(frame),frame);
    }

    private boolean cutscene(String frame) throws Exception {
        byte[] bytes=Objects.requireNonNull(getClass().getResourceAsStream(
                "/heroes/chest-transition-20261114/"+frame)).readAllBytes();
        return OpenCvPatternLocator.locatePattern(bytes,
                TemplatesEnum.SERVE_EMU_HERO_FREE_CHEST_CUTSCENE_2026.getTemplate(),
                new PointData(250,620),new PointData(510,860),92).isFound();
    }
}
