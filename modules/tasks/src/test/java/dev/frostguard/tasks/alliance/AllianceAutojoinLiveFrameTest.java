package dev.frostguard.tasks.alliance;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies the observed active Auto-Join and War frames from emulator-5554. */
class AllianceAutojoinLiveFrameTest {
    @BeforeAll static void nativeCv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void activeDialogShowsRestartNotAccidentalStart() throws Exception {
        byte[] screenshot=read("/autojoin/active-masked.jpg");
        assertTrue(find(screenshot,TemplatesEnum.AUTOJOIN_DIALOG_TITLE_2026,
                260,110,488,185,85));
        assertTrue(find(screenshot,TemplatesEnum.AUTOJOIN_RESTART_2026,
                325,1035,686,1155,85));
        assertTrue(find(screenshot,TemplatesEnum.AUTOJOIN_DIALOG_CLOSE_2026,
                620,115,710,190,85));
        assertFalse(find(screenshot,TemplatesEnum.AUTOJOIN_WAR_TITLE_2026,
                80,5,260,80,85));
    }

    @Test void warScreenHasSeparateTitleAndNoAutoJoinModal() throws Exception {
        byte[] screenshot=read("/autojoin/war-masked.jpg");
        assertTrue(find(screenshot,TemplatesEnum.AUTOJOIN_WAR_TITLE_2026,80,5,260,80,85));
        assertFalse(find(screenshot,TemplatesEnum.AUTOJOIN_DIALOG_TITLE_2026,
                260,110,488,185,85));
    }

    private static boolean find(byte[] screenshot, TemplatesEnum template,
             int l,int t,int r,int b,double threshold) {
        return OpenCvPatternLocator.locatePattern(screenshot,template.getTemplate(),
                new PointData(l,t),new PointData(r,b),threshold).isFound();
    }

    private static byte[] read(String resource) throws Exception {
        try(var stream=Objects.requireNonNull(
                AllianceAutojoinLiveFrameTest.class.getResourceAsStream(resource))) {
            return stream.readAllBytes();
        }
    }
}
