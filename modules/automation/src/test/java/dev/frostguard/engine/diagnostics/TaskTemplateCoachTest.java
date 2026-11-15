package dev.frostguard.engine.diagnostics;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.RawImageData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class TaskTemplateCoachTest {
    @TempDir Path root;

    @Test void onlyChosenTaskAndDeviceProduceEvidenceAndStopAfterItEnds() throws Exception {
        String old = System.getProperty("frostguard.workspace");
        System.setProperty("frostguard.workspace", root.toString());
        try {
            TaskTemplateCoach.arm(2026L, "emu0", TpDailyTaskEnum.INTEL, false);
            TaskTemplateCoach.taskStarted(2026L, "emu0", TpDailyTaskEnum.MAIL_REWARDS);
            assertEquals("ARMED", TaskTemplateCoach.snapshot().phase());
            TaskTemplateCoach.taskStarted(2026L, "emu0", TpDailyTaskEnum.INTEL);
            assertEquals("RUNNING", TaskTemplateCoach.snapshot().phase());
            RawImageData frame = RawImageData.capture(new byte[64 * 64 * 4], 64, 64, 32);
            Thread unrelatedTask = new Thread(() -> TaskTemplateCoach.observe("emu0",
                    TemplatesEnum.GAME_HOME_FURNACE, "color", 90,
                    ImageSearchResultData.miss(), frame));
            unrelatedTask.start();
            unrelatedTask.join();
            assertEquals(0, TaskTemplateCoach.snapshot().checks(),
                    "Calls from a different routine thread must not enter the diagnostic session");
            TaskTemplateCoach.observe("emu1", TemplatesEnum.GAME_HOME_FURNACE, "color", 90,
                    ImageSearchResultData.miss(), frame);
            assertEquals(0, TaskTemplateCoach.snapshot().checks());
            TaskTemplateCoach.observe("emu0", TemplatesEnum.GAME_HOME_FURNACE, "color", 90,
                    ImageSearchResultData.hit(30, 40, 98), frame);
            TaskTemplateCoach.observe("emu0", TemplatesEnum.GAME_HOME_FURNACE, "color", 90,
                    ImageSearchResultData.miss(), frame);
            TaskTemplateCoach.observe("emu0", TemplatesEnum.GAME_HOME_FURNACE, "color", 90,
                    ImageSearchResultData.miss(), frame);
            assertEquals(3, TaskTemplateCoach.snapshot().checks());
            assertEquals(1, TaskTemplateCoach.snapshot().hits());
            TaskTemplateCoach.taskFinished(2026L, "emu0", TpDailyTaskEnum.MAIL_REWARDS, "SUCCEEDED");
            assertEquals("RUNNING", TaskTemplateCoach.snapshot().phase());
            TaskTemplateCoach.taskFinished(2026L, "emu0", TpDailyTaskEnum.INTEL, "SUCCEEDED");
            for (int i = 0; i < 80 && !TaskTemplateCoach.snapshot().phase().equals("COMPLETED"); i++) {
                Thread.sleep(50);
            }
            var status = TaskTemplateCoach.snapshot();
            assertEquals("COMPLETED", status.phase());
            assertEquals(3, status.checks());
            assertTrue(status.captures() >= 2);
            assertTrue(Files.isRegularFile(Path.of(status.directory(), "summary.txt")));
            String events = Files.readString(Path.of(status.directory(), "recognition-events.csv"));
            assertTrue(events.contains("GAME_HOME_FURNACE"));
            assertEquals(4, events.lines().count()); // CSV header and exactly three observations
            assertFalse(status.note().contains("could not"));
            TaskTemplateCoach.observe("emu0", TemplatesEnum.GAME_HOME_FURNACE, "color", 90,
                    ImageSearchResultData.miss(), frame);
            assertEquals(3, TaskTemplateCoach.snapshot().checks());
        } finally {
            TaskTemplateCoach.stop();
            if (old == null) System.clearProperty("frostguard.workspace");
            else System.setProperty("frostguard.workspace", old);
        }
    }

    @Test void confirmedTemplateRepairCanAlwaysBeRolledBack() throws Exception {
        String old = System.getProperty("frostguard.workspace");
        System.setProperty("frostguard.workspace", root.toString());
        try {
            Path candidate = root.resolve("candidate.png");
            ImageIO.write(new BufferedImage(24, 30, BufferedImage.TYPE_INT_RGB), "png", candidate.toFile());
            String resource = "/templates/home/example.png";
            assertThrows(IllegalArgumentException.class,
                    () -> VerifiedTemplateOverrides.pathFor("/templates/../config/secrets.png"));
            assertFalse(VerifiedTemplateOverrides.isActive(resource));
            Path installed = VerifiedTemplateOverrides.activate(resource, candidate, 100, 140, 720, 1280);
            assertTrue(Files.isRegularFile(installed));
            assertTrue(VerifiedTemplateOverrides.isActive(resource));
            assertThrows(java.io.IOException.class,
                    () -> VerifiedTemplateOverrides.activate(resource, candidate, 100, 140, 720, 1280));
            assertTrue(VerifiedTemplateOverrides.rollback(resource));
            assertFalse(VerifiedTemplateOverrides.isActive(resource));
        } finally {
            if (old == null) System.clearProperty("frostguard.workspace");
            else System.setProperty("frostguard.workspace", old);
        }
    }

    @Test void neverAutomaticallyRepairAClaimedOrSuccessfullyCompletedButton() {
        assertFalse(TaskTemplateCoach.mayAutomaticallyApply("SUCCEEDED", false, false));
        assertFalse(TaskTemplateCoach.mayAutomaticallyApply("FAILED", true, false));
        assertFalse(TaskTemplateCoach.mayAutomaticallyApply("FAILED", false, true));
        assertFalse(TaskTemplateCoach.mayAutomaticallyApply("DEFERRED", false, false));
        assertFalse(TaskTemplateCoach.mayAutomaticallyApply("UNKNOWN", false, false));
        assertTrue(TaskTemplateCoach.mayAutomaticallyApply("FAILED", false, false));
    }

    @Test void diagnosesWrongSearchRegionInsteadOfReplacingValidTemplate() throws Exception {
        String old = System.getProperty("frostguard.workspace");
        System.setProperty("frostguard.workspace", root.toString());
        try {
            dev.frostguard.vision.match.OpenCvPatternLocator.loadNativeLibrary();
            BufferedImage screenshot = new BufferedImage(720, 1280, BufferedImage.TYPE_INT_RGB);
            BufferedImage badge;
            try (var stream = getClass().getResourceAsStream(
                    TemplatesEnum.SIDEBAR_CLAIM_ACTION.getTemplate())) {
                assertNotNull(stream);
                badge = ImageIO.read(stream);
            }
            var graphics = screenshot.createGraphics();
            graphics.drawImage(badge, 382, 535, null);
            graphics.dispose();
            RawImageData frame = TaskTemplateCoach.asRaw(screenshot);
            long profileId = 30303L;
            TaskTemplateCoach.arm(profileId, "emu0", TpDailyTaskEnum.TRAINING_TROOPS, false);
            TaskTemplateCoach.taskStarted(profileId, "emu0", TpDailyTaskEnum.TRAINING_TROOPS);
            for (int i = 0; i < 3; i++) {
                TaskTemplateCoach.observe("emu0", TemplatesEnum.SIDEBAR_CLAIM_ACTION,
                        "color", 87, ImageSearchResultData.miss(), frame,
                        new dev.frostguard.api.domain.PointData(350, 578),
                        new dev.frostguard.api.domain.PointData(445, 640));
            }
            TaskTemplateCoach.taskFinished(profileId, "emu0", TpDailyTaskEnum.TRAINING_TROOPS, "FAILED");
            for (int i = 0; i < 160 && !TaskTemplateCoach.snapshot().phase().equals("COMPLETED"); i++) {
                Thread.sleep(50);
            }
            var status = TaskTemplateCoach.snapshot();
            assertEquals("COMPLETED", status.phase(), status.note());
            String report = Files.readString(Path.of(status.directory(), "summary.txt"));
            assertTrue(report.contains("REGION_MISMATCH_REVIEW SIDEBAR_CLAIM_ACTION"), report);
            assertTrue(report.contains("likely wrong ROI, not a broken PNG"));
            assertFalse(VerifiedTemplateOverrides.isActive(TemplatesEnum.SIDEBAR_CLAIM_ACTION.getTemplate()));
            String records = Files.readString(Path.of(status.directory(), "recognition-events.csv"));
            assertTrue(records.contains(",350,578,445,640"), records);
        } finally {
            TaskTemplateCoach.stop();
            if (old == null) System.clearProperty("frostguard.workspace");
            else System.setProperty("frostguard.workspace", old);
        }
    }

    @Test void screenContextValidationDistinguishesDifferentPages() {
        BufferedImage a = new BufferedImage(240, 360, BufferedImage.TYPE_INT_RGB);
        BufferedImage b = new BufferedImage(240, 360, BufferedImage.TYPE_INT_RGB);
        assertTrue(TaskTemplateCoach.sameScreenOutsideTarget(a, b, 120, 160, 30, 30));
        for (int y = 0; y < 360; y++) for (int x = 0; x < 240; x++) b.setRGB(x,y,0xffffff);
        assertFalse(TaskTemplateCoach.sameScreenOutsideTarget(a, b, 120, 160, 30, 30));
    }
}
