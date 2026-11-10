package dev.frostguard.engine.emulator.instance;

import dev.frostguard.engine.emulator.EmulatorInstance;
import dev.frostguard.engine.emulator.ScreenGeometry;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BlueStacks 5 instances are identified by their engine names (e.g. Nougat32, Pie64).
 * ADB ports are read from the installed BlueStacks configuration rather than guessed.
 */
public final class BlueStacksEmulatorInstance extends EmulatorInstance {
    private static final Logger LOG = LoggerFactory.getLogger(BlueStacksEmulatorInstance.class);
    private static final Pattern INSTANCE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private final ConcurrentHashMap<String, ScreenGeometry> screenGeometry = new ConcurrentHashMap<>();

    @Override protected RawImageData prepareCapturedFrame(String id, RawImageData raw) {
        ScreenGeometry geometry = ScreenGeometry.forPortraitCapture(raw);
        if (geometry == null) {
            screenGeometry.remove(id);
            LOG.warn("BlueStacks capture {}x{} is not a 9:16 portrait frame; "
                    + "coordinate normalization disabled for {}", raw.getWidth(), raw.getHeight(), id);
            return raw;
        }
        screenGeometry.put(id, geometry);
        if (geometry.needsResize()) {
            LOG.debug("BlueStacks {} screenshot normalized {}x{} -> 720x1280",
                    id, raw.getWidth(), raw.getHeight());
        }
        return geometry.normalize(raw);
    }

    @Override protected PointData mapScreenPoint(String id, PointData point) {
        ScreenGeometry geometry = screenGeometry.get(id);
        if (geometry == null) {
            // Obtain a fresh capture before the first input so coordinates cannot be guessed.
            captureScreenshot(id);
            geometry = screenGeometry.get(id);
        }
        return geometry == null ? point : geometry.toDevice(point);
    }

    public BlueStacksEmulatorInstance(String path) { super(path); }

    private static String checked(String id) {
        if (id == null || !INSTANCE_NAME.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid BlueStacks instance name: " + id);
        }
        return id;
    }

    private static String resolveInstance(String id) {
        String value = checked(id);
        if (!value.matches("[0-9]+")) return value;
        Path config = Path.of(System.getenv().getOrDefault("ProgramData", "C:\\ProgramData"),
                "BlueStacks_nxt", "bluestacks.conf");
        try {
            String content = Files.readString(config);
            Matcher matcher = Pattern.compile("(?m)^bst\\.instance\\.([A-Za-z0-9_-]+)\\.").matcher(content);
            LinkedHashSet<String> names = new LinkedHashSet<>();
            while (matcher.find()) names.add(matcher.group(1));
            List<String> ordered = new ArrayList<>(names);
            // The primary BlueStacks instance should always be index 0 when present.
            if (ordered.remove("Nougat32")) ordered.add(0, "Nougat32");
            int index = Integer.parseInt(value);
            if (index >= 0 && index < ordered.size()) return ordered.get(index);
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("Cannot resolve BlueStacks instance index " + value, e);
        }
        throw new IllegalArgumentException("No BlueStacks instance found for index " + value);
    }

    @Override protected String getDeviceSerial(String id) {
        String name = resolveInstance(id);
        Path config = Path.of(System.getenv().getOrDefault("ProgramData", "C:\\ProgramData"),
                "BlueStacks_nxt", "bluestacks.conf");
        try {
            String content = Files.readString(config);
            Pattern portPattern = Pattern.compile("(?m)^bst\\.instance\\." + Pattern.quote(name)
                    + "\\.status\\.adb_port=[^0-9\\r\\n]*([0-9]+)");
            Matcher match = portPattern.matcher(content);
            boolean found = match.find();
            if (!found) {
                // Some BlueStacks versions use a non-status adb_port key.
                match = Pattern.compile("(?m)^bst\\.instance\\." + Pattern.quote(name)
                        + "\\.adb_port=[^0-9\\r\\n]*([0-9]+)").matcher(content);
                found = match.find();
            }
            if (found) {
                int port = Integer.parseInt(match.group(1));
                if (port > 0 && port <= 65535) {
                    // BlueStacks ADB may expose the local port under Android's
                    // emulator-5554 serial rather than 127.0.0.1:5555.
                    String tcp = "127.0.0.1:" + port;
                    String emulator = port % 2 == 1 ? "emulator-" + (port - 1) : null;
                    if (emulator != null && adbDeviceOnline(emulator)) return emulator;
                    return tcp;
                }
            }
        } catch (IOException e) {
            LOG.warn("Cannot read BlueStacks ADB configuration at {}: {}", config, e.getMessage());
        }
        throw new IllegalStateException("BlueStacks ADB port not found for " + name
                + ". Enable Android Debug Bridge in BlueStacks settings and restart the instance.");
    }

    @Override public void launchEmulator(String id) {
        String name = resolveInstance(id);
        try {
            new ProcessBuilder(Path.of(consolePath, "HD-Player.exe").toString(),
                    "--instance", name).start();
            LOG.info("BlueStacks instance {} launch requested", name);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start BlueStacks " + name, e);
        }
    }

    @Override public boolean isRunning(String id) {
        // Prefer live ADB discovery: Windows process arguments are not reliable
        // for already running BlueStacks instances.
        String serial = getDeviceSerial(id);
        if (adbDeviceOnline(serial)) return true;
        String name = resolveInstance(id);
        return ProcessHandle.allProcesses().anyMatch(process -> isInstanceProcess(process, name));
    }

    private boolean adbDeviceOnline(String serial) {
        Path adb = Path.of(consolePath, "HD-Adb.exe");
        if (!Files.isRegularFile(adb)) return false;
        try {
            Process process = new ProcessBuilder(adb.toString(), "devices")
                    .redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            if (process.exitValue() != 0) return false;
            String output = new String(process.getInputStream().readAllBytes());
            for (String line : output.split("\\R")) {
                String[] columns = line.trim().split("\\s+");
                if (columns.length >= 2 && serial.equals(columns[0])
                        && "device".equals(columns[1])) return true;
            }
        } catch (IOException e) {
            LOG.debug("BlueStacks ADB device probe failed: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    @Override public void closeEmulator(String id) {
        String name = resolveInstance(id);
        // Only stop the selected instance; never terminate all BlueStacks processes.
        ProcessHandle.allProcesses().filter(process -> isInstanceProcess(process, name))
                .forEach(process -> {
                    if (!process.destroy()) LOG.warn("Could not stop BlueStacks instance {}", name);
                });
    }

    private static boolean isInstanceProcess(ProcessHandle process, String name) {
        ProcessHandle.Info info = process.info();
        String command = info.command().orElse("").replace('\\', '/').toLowerCase();
        String args = String.join(" ", info.arguments().orElse(new String[0]));
        return command.endsWith("/hd-player.exe")
                && (args.contains("--instance " + name)
                    || args.contains("--instance \"" + name + "\""));
    }
}
