package dev.frostguard.engine.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LanDiagnosticReporterTest {
    @TempDir Path workspace;

    @Test void doesNotBootstrapDatabaseWhenWorkspaceIsUninitialized() throws Exception {
        Path db = workspace.resolve("frostguard.db");
        assertFalse(LanDiagnosticReporter.workspaceReady(db));
        Files.createFile(db);
        assertTrue(LanDiagnosticReporter.workspaceReady(db));
    }
}
