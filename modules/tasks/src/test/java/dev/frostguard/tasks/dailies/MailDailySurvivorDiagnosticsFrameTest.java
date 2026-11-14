package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MailDailySurvivorDiagnosticsFrameTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void openedMailReportHasBothHeaderAndBackArrow() throws Exception {
        assertTrue(matches("mail-open-report",TemplatesEnum.SERVE_EMU_MAIL_REPORT_TITLE_NOV_2026,85,7,192,76,92));
        assertTrue(matches("mail-open-report",TemplatesEnum.SERVE_EMU_MAIL_REPORT_BACK_NOV_2026,0,0,95,91,92));
        assertFalse(matches("mail-open-report",TemplatesEnum.SERVE_EMU_MAIL_TITLE,75,75,210,150,90));
    }

    @Test void survivingRewardsOverlayCanBeRecognizedWithoutDailyTitle() throws Exception {
        assertTrue(matches("daily-existing-rewards",TemplatesEnum.SERVE_EMU_REWARDS_BANNER,40,245,680,425,90)
                || matches("daily-existing-rewards",TemplatesEnum.SHARED_REWARDS_BANNER_CURRENT,40,245,680,425,90));
        assertFalse(matches("daily-existing-rewards",TemplatesEnum.SERVE_EMU_DAILY_TITLE,120,80,600,225,90));
        assertFalse(matches("daily-existing-rewards",TemplatesEnum.DAILY_MISSION_SCREEN_TITLE,120,80,600,225,87));
    }

    @Test void survivorStatusAndAllocateAllAreVisibleInRecordedFailure() throws Exception {
        assertTrue(matches("survivors-status",TemplatesEnum.SERVE_EMU_SURVIVORS_STATUS_TITLE_NOV_2026,10,320,260,420,90));
        assertTrue(matches("survivors-status",TemplatesEnum.SERVE_EMU_SURVIVORS_ALLOCATE_ALL_NOV_2026,180,1040,560,1190,92));
        assertTrue(matches("survivors-status",TemplatesEnum.SERVE_EMU_SURVIVORS_STATUS_CLOSE_NOV_2026,640,310,720,405,92));
    }

    @Test void noneOfTheNewActionsMatchCityOrWorld() throws Exception {
        for (String frame : new String[]{"city-negative", "world-negative"}) {
            assertFalse(matches(frame,TemplatesEnum.SERVE_EMU_MAIL_REPORT_TITLE_NOV_2026,85,7,192,76,92),frame);
            assertFalse(matches(frame,TemplatesEnum.SERVE_EMU_MAIL_REPORT_BACK_NOV_2026,0,0,95,91,92),frame);
            assertFalse(matches(frame,TemplatesEnum.SERVE_EMU_SURVIVORS_STATUS_TITLE_NOV_2026,10,320,260,420,90),frame);
            assertFalse(matches(frame,TemplatesEnum.SERVE_EMU_SURVIVORS_ALLOCATE_ALL_NOV_2026,180,1040,560,1190,92),frame);
            assertFalse(matches(frame,TemplatesEnum.SERVE_EMU_SURVIVORS_STATUS_CLOSE_NOV_2026,640,310,720,405,92),frame);
        }
    }

    private boolean matches(String name,TemplatesEnum template,int x1,int y1,int x2,int y2,int threshold) throws Exception {
        byte[] img=Objects.requireNonNull(getClass().getResourceAsStream(
                "/dailies/diagnostic-20261114/"+name+".png")).readAllBytes();
        return OpenCvPatternLocator.locatePattern(img,template.getTemplate(),
                new PointData(x1,y1),new PointData(x2,y2),threshold).isFound();
    }
}
