package dev.frostguard.tasks.heroes;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HeroRecruitmentRewardsExitLiveTest {
    @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
    @Test void actualKeyRewardHasNewSafeFooterButNotOnRecruitmentPage() throws Exception {
        byte[] frame;
        try(var s=Objects.requireNonNull(getClass().getResourceAsStream(
                "/heroes/live-20261119/rewards-exit-redacted.png"))){frame=s.readAllBytes();}
        assertTrue(OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_HERO_REWARDS_EXIT_LIVE_NOV_2026.getTemplate(),
                new PointData(190,1125),new PointData(525,1240),89).isFound());
        // The title animation varies between real frames; the Java routine
        // checks it on a fresh full frame, while this redacted fixture asserts
        // the stable safe footer and its absence on the main recruit screen.
        byte[] recruitment;
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream(
                "/heroes/live-20261119/recruitment-countdown-redacted.png"))){
            recruitment=input.readAllBytes();
        }
        assertFalse(OpenCvPatternLocator.locatePattern(recruitment,
                TemplatesEnum.SERVE_EMU_HERO_REWARDS_EXIT_LIVE_NOV_2026.getTemplate(),
                new PointData(190,1125),new PointData(525,1240),89).isFound());
    }
}
