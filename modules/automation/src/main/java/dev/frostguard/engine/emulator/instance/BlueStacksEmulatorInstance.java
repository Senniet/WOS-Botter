package dev.frostguard.engine.emulator.instance;

import dev.frostguard.engine.emulator.EmulatorInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BlueStacks 5 instances are identified by their engine names (e.g. Nougat32, Pie64).
 * ADB ports are read from the installed BlueStacks configuration rather than guessed.
 */
public final class BlueStacksEmulatorInstance extends EmulatorInstance {
    private static final Logger LOG = LoggerFactory.getLogger(BlueStacksEmulatorInstance.class);
    private static final Pattern INSTANCE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

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
                if (port > 0 && port <= 65535) return "127.0.0.1:" + port;
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
        String name = resolveInstance(id);
        return ProcessHandle.allProcesses().anyMatch(process -> isInstanceProcess(process, name));
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
