package dev.frostguard.engine.emulator;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BundledAdbLocatorTest {
    @TempDir Path temp;

    @Test void resolvesJpackageAdbWithUnrelatedWorkingDirectory() throws Exception {
        Path bin = temp.resolve("SurvivalOps/app/lib/adb/adb.exe");
        Files.createDirectories(bin.getParent());
        Files.createFile(bin);
        String actual = BundledAdbLocator.find(temp.resolve("elsewhere").toString(),
                temp.resolve("SurvivalOps/runtime").toString(),
                temp.resolve("SurvivalOps/SurvivalOps.exe").toString(), null);
        assertEquals(bin.toAbsolutePath().toString(), actual);
    }

    @Test void resolvesDevelopmentCheckout() throws Exception {
        Path adb = temp.resolve("tools/adb/adb.exe");
        Files.createDirectories(adb.getParent());
        Files.createFile(adb);
        assertEquals(adb.toAbsolutePath().toString(),
                BundledAdbLocator.find(temp.toString(), "", "", ""));
    }

    @Test void resolvesConfiguredLinuxAdbWithoutChangingBundledWindowsRules() throws Exception {
        Path executable = temp.resolve("platform-tools/adb");
        Files.createDirectories(executable.getParent());
        Files.createFile(executable);
        org.junit.jupiter.api.Assumptions.assumeTrue(executable.toFile().setExecutable(true));
        String previous = System.getProperty("frostguard.adb.path");
        try {
            System.setProperty("frostguard.adb.path", executable.toString());
            assertEquals(executable.toAbsolutePath().toString(),
                    BundledAdbLocator.find("", "", "", ""));
            System.setProperty("frostguard.adb.path", temp.resolve("nonexistent").toString());
            assertThrows(IllegalStateException.class,
                    () -> BundledAdbLocator.find("", "", "", ""));
        } finally {
            if (previous == null) System.clearProperty("frostguard.adb.path");
            else System.setProperty("frostguard.adb.path", previous);
        }
    }

    @Test void failsClearlyWhenNotBundled() {
        assertThrows(IllegalStateException.class, () -> BundledAdbLocator.find(
                temp.toString(), "", "", ""));
    }
}
