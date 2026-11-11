package dev.frostguard.app.panel.emulator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsExportTest {
    @TempDir Path temp;

    @Test void exportsOnlyLogAndSnapshotImagesWithoutUploading() throws Exception {
        Path logs = Files.createDirectories(temp.resolve("logs"));
        Path snapshots = Files.createDirectories(logs.resolve("snapshot/polar"));
        Files.writeString(logs.resolve("frostguard.log"), "ERROR recognition failed");
        Files.write(snapshots.resolve("deploy.png"), new byte[]{1, 2, 3});
        Files.writeString(logs.resolve("private-token.txt"), "must not ship");
        Path zip = temp.resolve("support.zip");
        EmuConfigLayoutController.exportDiagnostics(logs, zip);
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            assertNotNull(archive.getEntry("frostguard.log"));
            assertNotNull(archive.getEntry("snapshot/polar/deploy.png"));
            assertNull(archive.getEntry("private-token.txt"));
        }
    }

    @Test void refusesToWriteArchiveInsideLogs() throws Exception {
        Path logs = Files.createDirectories(temp.resolve("logs"));
        Files.writeString(logs.resolve("frostguard.log"), "sample");
        assertThrows(java.io.IOException.class, () ->
                EmuConfigLayoutController.exportDiagnostics(logs, logs.resolve("archive.zip")));
    }
}
