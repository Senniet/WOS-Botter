package dev.frostguard.engine.emulator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;

/** Locates the ADB client packaged inside a Windows jpackage app image. */
public final class BundledAdbLocator {
    private BundledAdbLocator() { }

    public static String find(String workingDirectory, String javaHome,
            String applicationPath, String consolePath) {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        add(roots, applicationPath);
        add(roots, javaHome);
        add(roots, workingDirectory);
        add(roots, consolePath);
        for (Path root : roots) {
            // jpackage install: <install>/app/lib/adb/adb.exe
            // Maven project: <repo>/tools/adb/adb.exe
            // Gradle/dev tool layout: <cwd>/packaging/desktop/target/input/lib/adb
            Path[] candidates = {
                root.resolve("app/lib/adb/adb.exe"),
                root.resolve("lib/adb/adb.exe"),
                root.resolve("tools/adb/adb.exe"),
                root.resolve("adb/adb.exe"),
                root.resolve("packaging/desktop/target/input/lib/adb/adb.exe")
            };
            for (Path candidate : candidates) {
                if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize().toString();
            }
        }
        throw new IllegalStateException("Bundled adb.exe not found. Reinstall SurvivalOps with its Android platform tools (app/lib/adb).");
    }

    private static void add(LinkedHashSet<Path> roots, String raw) {
        if (raw == null || raw.isBlank()) return;
        try {
            Path path = Path.of(raw).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) path = path.getParent();
            for (int i = 0; i < 4 && path != null; i++, path = path.getParent()) roots.add(path);
        } catch (RuntimeException ignored) { /* skip invalid optional path */ }
    }
}
