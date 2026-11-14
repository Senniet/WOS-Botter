package dev.frostguard.tasks.economy;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import java.io.ByteArrayInputStream;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorehouseStaminaLive20261114Test {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void earlierTooltipsDoNotExposeGreenClaimInTheExpectedRegion() throws Exception {
        for(String name:new String[]{"stamina-tooltip-obscures-claim.png",
                "stamina-tooltip-obscures-claim-repeat.png"}) {
            byte[] png=Objects.requireNonNull(getClass().getResourceAsStream(
                    "/storehouse/"+name)).readAllBytes();
            var result=GreenLabelActionDetector.detect(ImageIO.read(new ByteArrayInputStream(png)),
                    new GreenLabelActionDetector.Region(200,900,520,1020));
            assertFalse(result.isFound(),name);
        }
    }

    @Test void diagnosticShowsActualTitleAndClaimEvenWhenRoutineRejectedIt() throws Exception {
        byte[] src=Objects.requireNonNull(getClass().getResourceAsStream(
                "/storehouse/live-20261114/stamina-green-claim-redacted.png")).readAllBytes();
        assertTrue(OpenCvPatternLocator.locatePattern(src,
                TemplatesEnum.STOREHOUSE_STAMINA_REWARD_TITLE.getTemplate(),
                new PointData(150,740),new PointData(560,885),88).isFound(),
                "Actual A Warm Welcome title was visible");
        assertTrue(OpenCvPatternLocator.locatePattern(src,
                TemplatesEnum.STOREHOUSE_STAMINA_CLAIM_TEXT.getTemplate(),
                new PointData(200,900),new PointData(520,1020),88).isFound(),
                "Actual Claim label was visible");
        var green=GreenLabelActionDetector.detect(ImageIO.read(new ByteArrayInputStream(src)),
                new GreenLabelActionDetector.Region(200,900,520,1020));
        assertTrue(green.isFound(),"Actual green Claim geometry was visible");
    }
}
