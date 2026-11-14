package dev.frostguard.engine.helper;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavigationAllianceWarCurrentFrameTest {
    @BeforeAll static void init() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void alliancePageHasBothTitleAndBackArrow() throws Exception {
        assertTrue(is("alliance",TemplatesEnum.SERVE_EMU_ALLIANCE_TITLE_NOV_2026,true));
        assertTrue(is("alliance",TemplatesEnum.SERVE_EMU_ALLIANCE_BACK_NOV_2026,false));
    }

    @Test void warPageHasBothTitleAndBackArrow() throws Exception {
        assertTrue(is("war",TemplatesEnum.SERVE_EMU_WAR_TITLE_NOV_2026,true));
        assertTrue(is("war",TemplatesEnum.SERVE_EMU_WAR_BACK_NOV_2026,false));
    }

    @Test void otherScreensCannotCauseTypedAllianceOrWarRecovery() throws Exception {
        for (String page:new String[]{"city","world","intel"}) {
            assertFalse(is(page,TemplatesEnum.SERVE_EMU_ALLIANCE_TITLE_NOV_2026,true),page);
            assertFalse(is(page,TemplatesEnum.SERVE_EMU_WAR_TITLE_NOV_2026,true),page);
        }
        assertFalse(is("alliance",TemplatesEnum.SERVE_EMU_WAR_TITLE_NOV_2026,true));
        assertFalse(is("war",TemplatesEnum.SERVE_EMU_ALLIANCE_TITLE_NOV_2026,true));
    }

    private boolean is(String scene,TemplatesEnum tpl,boolean title) throws Exception {
        byte[] png=Objects.requireNonNull(getClass().getResourceAsStream(
                "/navigation/alliance-war-20261114/"+scene+"-header-redacted.png")).readAllBytes();
        return OpenCvPatternLocator.locatePattern(png,tpl.getTemplate(),
                title ? new PointData(68,5) : new PointData(0,0),
                title ? new PointData(200,85) : new PointData(90,85),92).isFound();
    }
}
