package dev.frostguard.tasks.heroes;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroFreeRewardExitLive20261115Test {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void actualNovember15RewardExitIsRecognized() throws Exception {
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream(
                "/heroes/hero-free-rewards-live-20261115-redacted.png"))) {
            byte[] frame=in.readAllBytes();
            var result=OpenCvPatternLocator.locatePattern(frame,
                    TemplatesEnum.SERVE_EMU_HERO_REWARD_EXIT_LIVE_2026,
                    new PointData(190,1135),new PointData(530,1240),89);
            assertTrue(result.isFound(),"Actual Tap anywhere to exit must match before tapping");
            assertTrue(result.getPoint().getY()>1135);
        }
    }
}
