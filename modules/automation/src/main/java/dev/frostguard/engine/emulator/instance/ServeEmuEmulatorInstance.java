package dev.frostguard.engine.emulator.instance;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.emulator.EmulatorInstance;
import dev.frostguard.engine.emulator.ScreenGeometry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** External ADB-over-TCP devices supplied by serve-emu on TrueNAS.
 * The emulator lifecycle remains owned by TrueNAS; the Windows client does not
 * launch, stop, restart or otherwise administer the remote VM/container.
 */
public final class ServeEmuEmulatorInstance extends EmulatorInstance {
    private static final Logger LOG = LoggerFactory.getLogger(ServeEmuEmulatorInstance.class);
    private static final Pattern ADDRESS = Pattern.compile(
            "(?:[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?|\\[[0-9a-fA-F:]+\\]):[0-9]{1,5}");
    private final List<String> endpoints;
    private final ConcurrentHashMap<String, ScreenGeometry> geometry = new ConcurrentHashMap<>();

    public ServeEmuEmulatorInstance(String endpoints) {
        // The base class finds the adb.exe bundled with SurvivalOps under tools/adb.
        super(Path.of("tools", "adb").toString());
        this.endpoints = validateEndpoints(endpoints);
    }

    /** Ordered endpoints correspond to device indices 0, 1, ... in task settings. */
    public static List<String> validateEndpoints(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Set at least one serve-emu ADB endpoint (host:port)");
        var result = new ArrayList<String>();
        for (String item : value.split(",", -1)) {
            String hostPort = item.trim();
            if (!ADDRESS.matcher(hostPort).matches())
                throw new IllegalArgumentException("Invalid serve-emu ADB endpoint: " + hostPort);
            int port = Integer.parseInt(hostPort.substring(hostPort.lastIndexOf(':') + 1));
            if (port < 1 || port > 65535)
                throw new IllegalArgumentException("Invalid serve-emu ADB port: " + port);
            if (result.contains(hostPort)) throw new IllegalArgumentException("Duplicate ADB endpoint: " + hostPort);
            result.add(hostPort);
        }
        return List.copyOf(result);
    }

    @Override protected String getDeviceSerial(String id) {
        try {
            int index = Integer.parseInt(id);
            if (index >= 0 && index < endpoints.size()) return endpoints.get(index);
        } catch (NumberFormatException ignored) { }
        if (endpoints.contains(id)) return id;
        throw new IllegalArgumentException("serve-emu device " + id + " not configured (" + endpoints.size() + " endpoints)");
    }

    @Override public void launchEmulator(String id) {
        // No TrueNAS lifecycle action. Base device discovery issues adb connect.
        LOG.info("serve-emu {} uses externally managed Android instance", getDeviceSerial(id));
    }

    @Override public void closeEmulator(String id) {
        // Never terminate a remote user-managed Android instance.
        geometry.remove(id);
        invalidateAllCaches(id);
    }

    @Override public boolean isRunning(String id) {
        try {
            String serial = getDeviceSerial(id);
            if (online(serial)) return true;
            // A remote Android instance will not appear in adb devices until
            // explicitly connected. Connect only the configured LAN endpoint.
            var result = dev.frostguard.engine.emulator.BoundedProcessRunner.run(
                    new ProcessBuilder(getAdbPath(), "connect", serial), Duration.ofSeconds(5));
            return !result.timedOut() && result.exitCode() == 0 && online(serial);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            LOG.debug("serve-emu connection unavailable: {}", e.getMessage());
            return false;
        }
    }

    private boolean online(String serial) throws java.io.IOException, InterruptedException {
        var result = dev.frostguard.engine.emulator.BoundedProcessRunner.run(
                new ProcessBuilder(getAdbPath(), "-s", serial, "get-state"), Duration.ofSeconds(3));
        return !result.timedOut() && result.exitCode() == 0 && result.output().trim().equals("device");
    }

    @Override protected RawImageData prepareCapturedFrame(String id, RawImageData raw) {
        ScreenGeometry frame = ScreenGeometry.forPortraitCapture(raw);
        if (frame == null) { geometry.remove(id); return raw; }
        geometry.put(id, frame);
        return frame.normalize(raw);
    }

    @Override protected PointData mapScreenPoint(String id, PointData point) {
        ScreenGeometry frame = geometry.get(id);
        if (frame == null) {
            captureScreenshot(id);
            frame = geometry.get(id);
        }
        return frame == null ? point : frame.toDevice(point);
    }
}
