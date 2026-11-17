package dev.frostguard.engine.diagnostics;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** An archive never accidentally includes secrets or unapproved screenshots. */
class TaskCoachEvidenceBundleTest {
    @TempDir Path temp;

    @Test void includesAllReportsAndSelectedImagesWithManifest() throws Exception {
        Path folder = goodRun();
        Path captures = Files.createDirectories(folder.resolve("captures"));
        Path proposals = Files.createDirectories(folder.resolve("proposals"));
        ImageIO.write(new BufferedImage(720, 1280, BufferedImage.TYPE_INT_RGB), "png",
                captures.resolve("001-training-camp-miss.png").toFile());
        ImageIO.write(new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB), "png",
                proposals.resolve("training-camp-unverified.png").toFile());
        Files.writeString(folder.resolve("private-token.txt"), "secret");
        var archive = TaskCoachEvidenceBundle.create(folder, true);
        assertEquals(2, archive.imageFiles());
        assertEquals(0, archive.omittedImages());
        Map<String, byte[]> entries = unzip(archive.zip());
        assertTrue(entries.containsKey("summary.txt"));
        assertTrue(entries.containsKey("recognition-events.csv"));
        assertTrue(entries.containsKey("captures/001-training-camp-miss.jpg"));
        BufferedImage screenshot = ImageIO.read(new ByteArrayInputStream(
                entries.get("captures/001-training-camp-miss.jpg")));
        assertNotNull(screenshot);
        assertEquals(720, screenshot.getWidth());
        assertEquals(1280, screenshot.getHeight());
        assertTrue(entries.containsKey("proposals/training-camp-unverified.png"));
        assertFalse(entries.containsKey("private-token.txt"));
        assertEquals(2, new JSONObject(new String(entries.get("upload-manifest.json")))
                .getInt("image_files_included"));
        assertEquals(1, new JSONObject(new String(entries.get("upload-manifest.json")))
                .getInt("jpeg_captures_full_resolution"));
    }

    @Test void neverLeaksScreenshotsWhenConsentIsDisabled() throws Exception {
        Path folder = goodRun();
        Files.createDirectories(folder.resolve("captures"));
        ImageIO.write(new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB), "png",
                folder.resolve("captures/001-test-miss.png").toFile());
        var bundle = TaskCoachEvidenceBundle.create(folder, false);
        Map<String, byte[]> entries = unzip(bundle.zip());
        assertEquals(0, bundle.imageFiles());
        assertEquals(1, bundle.omittedImages());
        assertTrue(entries.containsKey("summary.txt"));
        assertFalse(entries.containsKey("captures/001-test-miss.jpg"));
        assertFalse(new JSONObject(new String(entries.get("upload-manifest.json")))
                .getBoolean("screenshots_enabled"));
    }

    @Test void allFortyFiveTrainingFramesFitWithoutDiscardingLateScreens() throws Exception {
        Path folder = goodRun();
        Path captures = Files.createDirectory(folder.resolve("captures"));
        BufferedImage image = new BufferedImage(720, 1280, BufferedImage.TYPE_INT_RGB);
        var paint = image.createGraphics();
        try {
            for (int index = 1; index <= 45; index++) {
                paint.setColor(new java.awt.Color((index * 19) % 256, (index * 31) % 256,
                        (index * 47) % 256));
                paint.fillRect(0, 0, 720, 1280);
                paint.setColor(java.awt.Color.WHITE);
                paint.drawString("Claim and task evidence phase " + index, 42, 900);
                String file = String.format("%03d-training-screen-miss.png", index);
                assertTrue(ImageIO.write(image, "png", captures.resolve(file).toFile()));
            }
        } finally { paint.dispose(); }
        var archive = TaskCoachEvidenceBundle.create(folder, true);
        assertEquals(45, archive.imageFiles());
        assertEquals(0, archive.omittedImages());
        Map<String, byte[]> entries = unzip(archive.zip());
        assertTrue(entries.containsKey("captures/045-training-screen-miss.jpg"),
                "Last capture must remain in the evidence upload");
        assertTrue(entries.containsKey("captures/001-training-screen-miss.jpg"));
        assertTrue(archive.zip().length < 30 * 1024 * 1024);
    }

    @Test void refusesIncompleteOrUnexpectedSessions() throws Exception {
        Path invalid = Files.createDirectory(temp.resolve("../broken".replace("../", "bad-")));
        Files.writeString(invalid.resolve("summary.txt"), "status");
        Files.writeString(invalid.resolve("recognition-events.csv"), "score");
        assertThrows(IOException.class, () -> TaskCoachEvidenceBundle.create(invalid, true));
        Path run = goodRun();
        Files.delete(run.resolve("recognition-events.csv"));
        assertThrows(IOException.class, () -> TaskCoachEvidenceBundle.create(run, true));
    }

    private Path goodRun() throws Exception {
        Path folder = Files.createDirectory(temp.resolve("20261115-163819-1ij1e19"));
        Files.writeString(folder.resolve("summary.txt"), "task=TRAINING_TROOPS scheduler_outcome=FAILED");
        Files.writeString(folder.resolve("recognition-events.csv"), "template,score\nTAB,20\n");
        Files.writeString(folder.resolve("run.txt"), "task=TRAINING_TROOPS\n");
        return folder;
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                files.put(entry.getName(), zip.readAllBytes());
            }
        }
        return files;
    }
}
