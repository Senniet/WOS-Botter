package dev.frostguard.engine.diagnostics;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.runtime.WorkspacePaths;
import dev.frostguard.engine.service.ConfigService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Asynchronous, private HTTPS LAN diagnostics, never an external telemetry service. */
public final class LanDiagnosticReporter {
    private static final Logger LOG = LoggerFactory.getLogger(LanDiagnosticReporter.class);
    public static final String ENDPOINT = "https://10.0.0.100:18761/api/diagnostics";
    // Pinned SHA-256 of the certificate stored only on the user's private webserver.
    private static final String CERT_SHA256 =
            "e427136dc48a04dd6afbdc6728cc06ff353eb69237b1efcb3e90adcd386536c7";
    private static final int MAX_SCREENSHOT = 2_000_000;
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(24), job -> {
                Thread t = new Thread(job, "SurvivalOps-LanDiagnostics");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.DiscardPolicy());

    private LanDiagnosticReporter() {}

    /** Snapshot has already been written and may contain game account details.
     * Screenshot sharing is a separate explicit opt-in.
     */
    public static void snapshot(String activity, String kind, String relativePath) {
        if (!enabled()) return;
        Path root = WorkspacePaths.current().root();
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root.resolve("logs/snapshot")) || !file.getFileName().toString().endsWith(".png"))
            return;
        submit(activity, kind, file);
    }

    public static void failure(String task, String exceptionType) {
        if (!enabled()) return;
        submit(task, "task-error-" + exceptionType, null);
    }

    public static void testConnection(java.util.function.Consumer<String> completed) {
        String token = secret();
        EXECUTOR.execute(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://10.0.0.100:18761/health"))
                        .header("Authorization", "Bearer " + token)
                        .timeout(Duration.ofSeconds(5)).GET().build();
                var result = client().send(request, HttpResponse.BodyHandlers.discarding());
                completed.accept(result.statusCode() == 200 ? "LAN-verbinding werkt" : "HTTP " + result.statusCode());
            } catch (Exception error) {
                completed.accept("Verbinding mislukt: " + error.getClass().getSimpleName());
            }
        });
    }

    private static void submit(String activity, String kind, Path file) {
        try {
            Map<String, String> cfg = ConfigService.obtain().loadGlobalSettings();
            if (!Boolean.parseBoolean(cfg.getOrDefault(
                    ConfigurationKeyEnum.LAN_DIAGNOSTICS_ENABLED_BOOL.name(), "false"))) return;
            String token = cfg.getOrDefault(ConfigurationKeyEnum.LAN_DIAGNOSTICS_TOKEN_STRING.name(), "");
            if (token.length() < 32) return;
            boolean includeImage = Boolean.parseBoolean(cfg.getOrDefault(
                    ConfigurationKeyEnum.LAN_DIAGNOSTICS_SCREENSHOTS_BOOL.name(), "false"));
            EXECUTOR.execute(() -> transmit(activity, kind, includeImage ? file : null, token));
        } catch (RuntimeException ignored) {
            // Diagnostics must not change the automation outcome.
        }
    }

    private static void transmit(String activity, String kind, Path image, String token) {
        try {
            JSONObject event = new JSONObject()
                    .put("schema", 1)
                    .put("timestamp", Instant.now().toString())
                    .put("activity", safeToken(activity))
                    .put("kind", safeToken(kind))
                    .put("app", "SurvivalOps")
                    .put("platform", System.getProperty("os.name", "unknown"));
            if (image != null && Files.isRegularFile(image) && Files.size(image) <= MAX_SCREENSHOT) {
                event.put("image_png_base64", Base64.getEncoder().encodeToString(Files.readAllBytes(image)));
                event.put("image_name", image.getFileName().toString());
            }
            String logTail = readLogTail();
            if (!logTail.isEmpty()) event.put("log_tail", logTail);
            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(event.toString(), StandardCharsets.UTF_8))
                    .build();
            var response = client().send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 201)
                LOG.warn("LAN diagnostic upload returned HTTP {}", response.statusCode());
        } catch (Exception error) {
            LOG.warn("LAN diagnostic upload unavailable: {}", error.getClass().getSimpleName());
        }
    }

    private static String readLogTail() {
        // App logs can include player names, paths and chat. Only share recent
        // error/warning lines; do not send account database or configuration.
        Path log = WorkspacePaths.current().logs().resolve("frostguard.log");
        try {
            if (!Files.isRegularFile(log)) return "";
            long length = Files.size(log);
            try (var input = Files.newInputStream(log)) {
                input.skipNBytes(Math.max(0, length - 24000));
                String raw = new String(input.readNBytes(24000), StandardCharsets.UTF_8);
                StringBuilder lines = new StringBuilder();
                for (String line : raw.split("\\R")) {
                    if (!(line.contains("WARN") || line.contains("ERROR"))) continue;
                    String clean = line.replaceAll("(?i)(bearer\\s+|token[=:]\\s*)[A-Za-z0-9._-]+", "$1[redacted]")
                            .replaceAll("(?:[A-Za-z]:\\\\|/home/)[^\\s]+", "[local-path]")
                            .replaceAll("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b", "[ip]");
                    lines.append(clean, 0, Math.min(350, clean.length())).append('\n');
                }
                return lines.length() > 9000 ? lines.substring(lines.length() - 9000) : lines.toString();
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String safeToken(String input) {
        String value = input == null ? "" : input.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9-]+", "-");
        return value.length() > 70 ? value.substring(0, 70) : value;
    }

    static boolean workspaceReady(Path database) {
        return database != null && Files.isRegularFile(database);
    }

    private static boolean enabled() {
        try {
            // Never bootstrap persistence merely to report a snapshot.
            // On fresh installs and test runners the workspace DB may not exist.
            if (!workspaceReady(WorkspacePaths.current().database())) return false;
            Map<String, String> cfg = ConfigService.obtain().loadGlobalSettings();
            return cfg != null && Boolean.parseBoolean(cfg.getOrDefault(
                    ConfigurationKeyEnum.LAN_DIAGNOSTICS_ENABLED_BOOL.name(), "false"));
        } catch (RuntimeException ignored) { return false; }
    }

    private static String secret() {
        try {
            if (!workspaceReady(WorkspacePaths.current().database())) return "";
            Map<String, String> cfg = ConfigService.obtain().loadGlobalSettings();
            return cfg == null ? "" : cfg.getOrDefault(
                    ConfigurationKeyEnum.LAN_DIAGNOSTICS_TOKEN_STRING.name(), "");
        } catch (RuntimeException ignored) { return ""; }
    }

    private static HttpClient client() throws Exception {
        X509TrustManager pinned = new X509TrustManager() {
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            @Override public void checkClientTrusted(X509Certificate[] certs, String auth) {
                throw new SecurityException("Client certificates not used");
            }
            @Override public void checkServerTrusted(X509Certificate[] certs, String auth)
                    throws java.security.cert.CertificateException {
                if (certs == null || certs.length == 0)
                    throw new java.security.cert.CertificateException("No server certificate");
                try {
                    String fingerprint = HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(certs[0].getEncoded()));
                    if (!CERT_SHA256.equals(fingerprint))
                        throw new java.security.cert.CertificateException("LAN server certificate mismatch");
                } catch (java.security.NoSuchAlgorithmException e) {
                    throw new java.security.cert.CertificateException(e);
                }
            }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{pinned}, new SecureRandom());
        return HttpClient.newBuilder().sslContext(context).connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
}
