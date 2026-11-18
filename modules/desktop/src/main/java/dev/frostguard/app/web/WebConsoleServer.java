package dev.frostguard.app.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.FlowStepKind;
import dev.frostguard.api.domain.AutomationStep;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.DailyTaskStatusData;
import dev.frostguard.api.domain.TaskStateData;
import dev.frostguard.engine.schedule.TaskQueue;
import dev.frostguard.engine.service.ConfigService;
import dev.frostguard.engine.service.CustomTaskService;
import dev.frostguard.engine.service.TaskBuilderService;
import dev.frostguard.engine.service.StatisticsService;
import dev.frostguard.api.runtime.WorkspacePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import dev.frostguard.engine.service.ProfileService;
import dev.frostguard.engine.service.ScheduleService;
import dev.frostguard.engine.service.TaskManagementService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Local-only API for the web UI. A separate HTTPS/authentication gateway owns
 * all browser access; this process must never listen on a public interface.
 * The exact desktop automation engine and registered routines are reused.
 */
public final class WebConsoleServer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicReference<HttpServer> INSTANCE = new AtomicReference<>();
    private static final int MAX_BODY = 64 * 1024;

    private WebConsoleServer() {}

    public static void start() throws IOException {
        if (INSTANCE.get() != null) return;
        int port = Integer.getInteger("survivalops.web.port", 18765);
        HttpServer server = HttpServer.create(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), port), 32);
        server.createContext("/api/", WebConsoleServer::handle);
        server.setExecutor(Executors.newFixedThreadPool(8, task -> {
            Thread thread = new Thread(task, "survivalops-web-api");
            thread.setDaemon(true);
            return thread;
        }));
        if (!INSTANCE.compareAndSet(null, server)) {
            server.stop(0);
            return;
        }
        server.start();
        System.out.println("SurvivalOps web API ready on localhost:" + port);
    }

    static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
            if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) {
                write(exchange, 403, Map.of("error", "Local access only"));
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod()) &&
                    !"POST".equals(exchange.getRequestMethod())) {
                write(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                switch (path) {
                    case "/api/status" -> write(exchange, 200, status());
                    case "/api/profiles" -> write(exchange, 200, profiles());
                    case "/api/tasks" -> write(exchange, 200, tasks(
                            profile(query(exchange).get("profile"))));
                    case "/api/settings" -> write(exchange, 200, settings(
                            query(exchange).get("profile")));
                    case "/api/emulator" -> write(exchange, 200, emulator());
                    case "/api/custom-tasks" -> write(exchange, 200, customTasks());
                    case "/api/statistics" -> write(exchange, 200, statistics(profile(query(exchange).get("profile"))));
                    default -> write(exchange, 404, Map.of("error", "Not found"));
                }
            } else {
                Map<String, Object> data = read(exchange);
                switch (path) {
                    case "/api/engine" -> write(exchange, 200, controlEngine(data));
                    case "/api/task" -> write(exchange, 200, controlTask(data));
                    case "/api/profile" -> write(exchange, 200, editProfile(data));
                    case "/api/setting" -> write(exchange, 200, editSetting(data));
                    case "/api/custom-task" -> write(exchange, 200, editCustomTask(data));
                    case "/api/builder" -> write(exchange, 200, buildCustomTask(data));
                    case "/api/profile-queue" -> write(exchange, 200, controlProfileQueue(data));
                    case "/api/statistics" -> write(exchange, 200, controlStatistics(data));
                    default -> write(exchange, 404, Map.of("error", "Not found"));
                }
            }
        } catch (IllegalArgumentException | IllegalStateException badRequest) {
            write(exchange, 400, Map.of("error", String.valueOf(badRequest.getMessage())));
        } catch (Exception failure) {
            org.slf4j.LoggerFactory.getLogger(WebConsoleServer.class)
                    .error("Web API {} failed", path, failure);
            write(exchange, 500, Map.of("error", "Operation failed; inspect server logs"));
        } finally {
            exchange.close();
        }
    }

    private static Map<String, Object> statistics(AccountDescriptor p) {
        var data = StatisticsService.obtain().loadMetrics(p);
        List<Map<String, Object>> rows = new ArrayList<>();
        data.getRoutineStats().forEach((key, value) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", key);
            row.put("name", Objects.toString(value.getRoutineName(), key));
            row.put("runs", value.getExecutionCount());
            row.put("totalMs", value.getCumulativeTimeMs());
            row.put("ocrErrors", value.getOcrErrorCount());
            row.put("matchErrors", value.getMatchErrorCount());
            row.put("last", value.getLastExecutedAt());
            rows.add(row);
        });
        rows.sort((a, b) -> Integer.compare((int) b.get("runs"), (int) a.get("runs")));
        return Map.of("routines", rows, "counters", data.getNamedCounters(),
                "totalRuns", rows.stream().mapToInt(row -> (int) row.get("runs")).sum());
    }

    private static Object controlStatistics(Map<String, Object> payload) {
        String action = str(payload, "action");
        if (!action.equals("reset")) throw new IllegalArgumentException("Unknown statistics action");
        AccountDescriptor p = profile(str(payload, "profileId"));
        if (ScheduleService.obtain().getCoordinator().hasManagedQueues()) {
            throw new IllegalStateException("Stop the scheduler before clearing statistics");
        }
        StatisticsService.obtain().clearMetrics(p);
        return Map.of("ok", true);
    }

    private static Map<String, Object> status() {
        ScheduleService schedule = ScheduleService.obtain();
        boolean active = schedule.getCoordinator().hasManagedQueues();
        List<Map<String, Object>> queueRows = new ArrayList<>();
        schedule.getCoordinator().getActiveQueueStates().forEach(q -> {
            TaskQueue queue = schedule.getCoordinator().getQueue(q.getProfileId());
            queueRows.add(Map.of(
                    "profileId", q.getProfileId(),
                    "name", Objects.toString(q.getProfileName(), ""),
                    "paused", q.isPaused(),
                    "executing", queue != null && queue.hasExecutingTask(),
                    "next", queue == null || queue.getScheduledUntil() == null ? ""
                            : queue.getScheduledUntil().toString()));
        });
        return Map.of("online", true, "running", active,
                "executing", schedule.hasExecutingTasks(), "queues", queueRows,
                "time", LocalDateTime.now().toString(), "engine", "Java 21 / original SurvivalOps");
    }

    private static List<Map<String, Object>> profiles() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AccountDescriptor p : ProfileService.obtain().fetchAllAccounts()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", p.getId());
            r.put("name", p.getName());
            r.put("slot", p.getEmulatorNumber());
            r.put("enabled", Boolean.TRUE.equals(p.getEnabled()));
            r.put("priority", p.getPriority());
            TaskQueue q = ScheduleService.obtain().getCoordinator().getQueue(p.getId());
            r.put("queueActive", q != null);
            r.put("paused", q != null && q.isPaused());
            rows.add(r);
        }
        return rows;
    }

    private static List<Map<String, Object>> tasks(AccountDescriptor p) {
        var stateService = TaskManagementService.shared();
        var progress = stateService.fetchDailyProgress(p.getId());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TpDailyTaskEnum kind : TpDailyTaskEnum.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", kind.name());
            row.put("code", kind.getId());
            row.put("name", kind.getName());
            row.put("category", kind.routineCategory().name());
            row.put("toggleable", kind.getConfigKey() != null);
            row.put("activationKey", kind.getConfigKey() == null ? null : kind.getConfigKey().name());
            row.put("enabled", kind.getConfigKey() != null
                    && Boolean.TRUE.equals(p.getConfig(kind.getConfigKey(), Boolean.class)));
            TaskStateData state = stateService.lookupTaskState(p.getId(), kind.getId());
            DailyTaskStatusData persisted = progress.stream()
                    .filter(t -> t.getTaskTypeCode() == kind.getId()).findFirst().orElse(null);
            row.put("outcome", state == null ? "NOT_RUN" : state.getExecutionOutcome().name());
            row.put("detail", state == null ? "" : Objects.toString(state.getExecutionDetail(), ""));
            row.put("executing", state != null && state.isExecuting());
            row.put("queued", state != null && state.isQueued());
            // Live TaskStateData is recreated during scheduler startup and task
            // toggles. Keep the database's last successful completion visible,
            // including as proof of at least one past attempt.
            row.put("lastAttempt", asText(TaskExecutionHistoryView.lastAttempt(state, persisted)));
            row.put("lastSuccess", asText(TaskExecutionHistoryView.lastSuccess(state, persisted)));
            row.put("next", asText(TaskExecutionHistoryView.next(state, persisted)));
            rows.add(row);
        }
        return rows;
    }

    private static List<Map<String, Object>> settings(String profileId) {
        AccountDescriptor p = profileId == null || profileId.isBlank()
                ? null : profile(profileId);
        Map<String, String> globals = ConfigService.obtain().loadGlobalSettings();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ConfigurationKeyEnum key : ConfigurationKeyEnum.values()) {
            if (key.isLegacyOnly()) continue;
            boolean secret = secret(key.name());
            Object value = p == null
                    ? (globals == null ? null : globals.get(key.name()))
                    : p.getConfig(key, Object.class);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key.name());
            item.put("type", key.getType().getSimpleName());
            item.put("category", key.category().name());
            item.put("default", secret ? "" : key.getDefaultValue());
            item.put("value", secret ? "" : Objects.toString(value,
                    secret ? "" : Objects.toString(key.getDefaultValue(), "")));
            item.put("secret", secret);
            result.add(item);
        }
        return result;
    }

    private static boolean secret(String key) {
        return Arrays.stream(new String[] {"TOKEN", "PASSWORD", "SECRET", "API_KEY", "CHAT_ID",
                "AUTH", "COOKIE", "WEBHOOK"}).anyMatch(key::contains);
    }

    private static Object controlEngine(Map<String, Object> data) {
        ScheduleService service = ScheduleService.obtain();
        String command = str(data, "action");
        switch (command) {
            case "start" -> {
                var settings = ConfigService.obtain().loadGlobalSettings();
                if (settings == null || !"SERVE_EMU".equals(settings.get(
                        ConfigurationKeyEnum.CURRENT_EMULATOR_STRING.name()))) {
                    throw new IllegalStateException("Configure Serve-Emu before starting");
                }
                if (!service.getCoordinator().hasManagedQueues()) service.launchEngine();
            }
            case "stop" -> service.haltEngine();
            case "pause" -> service.suspendEngine();
            case "resume" -> service.resumeEngine();
            default -> throw new IllegalArgumentException("Invalid engine action");
        }
        return status();
    }

    private static Object controlTask(Map<String, Object> data) {
        AccountDescriptor p = profile(str(data, "profileId"));
        TpDailyTaskEnum task = TpDailyTaskEnum.valueOf(str(data, "task"));
        String action = str(data, "action");
        if ("toggle".equals(action)) {
            if (task.getConfigKey() == null) throw new IllegalArgumentException("System task cannot be toggled");
            boolean enabled = bool(data, "enabled");
            if (!ConfigService.obtain().writeAccountSetting(p.getId(),
                    task.getConfigKey(), Boolean.toString(enabled))) {
                throw new IllegalStateException("Could not save task setting");
            }
        } else if ("run".equals(action) || "schedule".equals(action)) {
            TaskQueue queue = ScheduleService.obtain().getCoordinator().getQueue(p.getId());
            if (task.getConfigKey() == null ||
                    !Boolean.TRUE.equals(p.getConfig(task.getConfigKey(), Boolean.class))) {
                throw new IllegalStateException("Enable this task first");
            }
            if ("run".equals(action)) {
                if (queue == null || !queue.isActive()) {
                    throw new IllegalStateException("Start the profile engine first");
                }
                queue.runNow(task, true);
            } else {
                LocalDateTime next;
                try { next = LocalDateTime.parse(str(data, "when")); }
                catch (Exception error) { throw new IllegalArgumentException("Expected ISO local date-time"); }
                if (next.isBefore(LocalDateTime.now().minusMinutes(2)) ||
                        next.isAfter(LocalDateTime.now().plusYears(2))) {
                    throw new IllegalArgumentException("Schedule must be between now and two years ahead");
                }
                if (queue != null && !queue.scheduleOrRescheduleQueuedTask(task, p, next)) {
                    throw new IllegalStateException("Cannot schedule a task that is executing");
                }
                ScheduleService.obtain().persistScheduleAdjustment(p, task, next, null);
            }
        } else {
            throw new IllegalArgumentException("Invalid task action");
        }
        return Map.of("ok", true);
    }

    private static Object controlProfileQueue(Map<String, Object> data) {
        AccountDescriptor profile = profile(str(data, "profileId"));
        String action = str(data, "action");
        TaskQueue queue = ScheduleService.obtain().getCoordinator().getQueue(profile.getId());
        if (queue == null) throw new IllegalStateException("Profile queue is not active");
        if (action.equals("pause")) ScheduleService.obtain().suspendAccountQueue(profile.getId());
        else if (action.equals("resume")) ScheduleService.obtain().resumeAccountQueue(profile.getId());
        else throw new IllegalArgumentException("Unknown queue action");
        return Map.of("ok", true);
    }

    private static Object editProfile(Map<String, Object> data) {
        String name = str(data, "name").trim();
        if (name.length() > 80 || name.isEmpty()) throw new IllegalArgumentException("Invalid profile name");
        String slot = Objects.toString(data.getOrDefault("slot", "0"));
        if (!"0".equals(slot)) throw new IllegalArgumentException("Only Serve-Emu slot 0 is supported");
        Long id = data.containsKey("id") && data.get("id") != null
                ? Long.valueOf(data.get("id").toString()) : null;
        AccountDescriptor profile = id == null ? new AccountDescriptor((Long) null) : profile(id.toString());
        profile.setName(name);
        profile.setEmulatorNumber("0");
        profile.setEnabled(bool(data, "enabled"));
        if (id == null) profile.setPriority(1L);
        if (profile.getReconnectionTime() == null) profile.setReconnectionTime(60L);
        boolean saved = id == null
                ? ProfileService.obtain().createAccount(profile)
                : ProfileService.obtain().persistAccount(profile);
        if (!saved) throw new IllegalStateException("Could not save profile");
        return Map.of("ok", true, "id", profile.getId());
    }

    private static Object editSetting(Map<String, Object> data) {
        String profileId = Objects.toString(data.get("profileId"), "");
        ConfigurationKeyEnum key = ConfigurationKeyEnum.valueOf(str(data, "key"));
        if (key.isLegacyOnly()) throw new IllegalArgumentException("Legacy setting cannot be edited");
        String value = str(data, "value");
        if (value.length() > 4096) throw new IllegalArgumentException("Setting too long");
        if (key.getType() == Boolean.class &&
                !value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException("Expected boolean");
        }
        if (key.getType() == Integer.class) Integer.parseInt(value);
        if (key == ConfigurationKeyEnum.CURRENT_EMULATOR_STRING &&
                !"SERVE_EMU".equals(value)) {
            throw new IllegalArgumentException("Web version supports Serve-Emu only");
        }
        if (key == ConfigurationKeyEnum.SERVE_EMU_ADB_ENDPOINTS_STRING) {
            dev.frostguard.engine.emulator.instance.ServeEmuEmulatorInstance.validateEndpoints(value);
        }
        boolean written = profileId.isBlank()
                ? ConfigService.obtain().writeGlobalSetting(key, value)
                : ConfigService.obtain().writeAccountSetting(profile(profileId).getId(), key, value);
        if (!written) throw new IllegalStateException("Setting was not saved");
        return Map.of("ok", true);
    }

    /** Safe subset of the original visual Task Builder: compose existing engine nodes,
     * generate the same Java source, and register the disabled custom routine.
     * Never execute a step as part of creating the blueprint. */
    private static Object buildCustomTask(Map<String, Object> data) throws IOException {
        if (ScheduleService.obtain().getCoordinator().hasManagedQueues()) {
            throw new IllegalStateException("Stop the scheduler before building a task");
        }
        String title = str(data, "name");
        if (!title.matches("[A-Za-z][A-Za-z0-9]{2,40}")) {
            throw new IllegalArgumentException("Name must be 3-41 ASCII letters or digits");
        }
        String className = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        if (!className.endsWith("Task")) className += "Task";
        if (Files.exists(WorkspacePaths.current().customTasks().resolve(className + ".java"))) {
            throw new IllegalStateException("A custom task with this name already exists");
        }
        Object rawNodes = data.get("steps");
        if (!(rawNodes instanceof List<?> nodes) || nodes.isEmpty() || nodes.size() > 48) {
            throw new IllegalArgumentException("A workflow needs between 1 and 48 steps");
        }
        var builder = new TaskBuilderService();
        builder.startSession(title, "0");
        String start = Objects.toString(data.get("startLocation"), "ANY");
        if (!List.of("ANY", "HOME", "WORLD").contains(start)) {
            throw new IllegalArgumentException("Invalid start location");
        }
        builder.getCurrentDefinition().setStartLocation(start);
        for (int i = 0; i < nodes.size(); i++) {
            if (!(nodes.get(i) instanceof Map<?, ?> entry)) {
                throw new IllegalArgumentException("Invalid workflow node");
            }
            Object rawType = entry.get("type");
            if (!(rawType instanceof String text)) throw new IllegalArgumentException("Step type required");
            FlowStepKind kind;
            try { kind = FlowStepKind.valueOf(text); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("Unknown workflow step"); }
            if (kind != FlowStepKind.TAP_POINT && kind != FlowStepKind.SWIPE &&
                    kind != FlowStepKind.WAIT && kind != FlowStepKind.BACK_BUTTON) {
                throw new IllegalArgumentException("Unsupported step in simple web Task Builder");
            }
            AutomationStep node = new AutomationStep(i + 1, kind);
            switch (kind) {
                case WAIT -> node.putAttribute("durationMs", Integer.toString(
                        boundedIntegerEntry(entry, "durationMs", 1000, 100, 30000)));
                case TAP_POINT -> {
                    int x = boundedIntegerEntry(entry, "x", 360, 0, 719);
                    int y = boundedIntegerEntry(entry, "y", 640, 0, 1279);
                    node.putAttribute("tlX", Integer.toString(x));
                    node.putAttribute("brX", Integer.toString(x));
                    node.putAttribute("tlY", Integer.toString(y));
                    node.putAttribute("brY", Integer.toString(y));
                }
                case SWIPE -> {
                    for (String coordinate : List.of("startX", "endX", "startY", "endY")) {
                        int max = coordinate.endsWith("X") ? 719 : 1279;
                        node.putAttribute(coordinate, Integer.toString(
                                boundedIntegerEntry(entry, coordinate, 0, 0, max)));
                    }
                }
                default -> { }
            }
            node.setNextNodeId(i == nodes.size() - 1 ? -1 : i + 2);
            builder.addNode(node);
        }
        var result = builder.saveCurrentTaskToCustomTasks(title);
        var service = CustomTaskService.getInstance();
        String loaded = service.compileAndLoad(result.javaFile().toFile());
        if (loaded == null) {
            Files.deleteIfExists(result.javaFile());
            Files.deleteIfExists(result.builderFile());
            throw new IllegalStateException("Generated routine did not compile; consult runtime log");
        }
        service.saveTasks();
        return Map.of("ok", true, "className", loaded, "steps", nodes.size(), "enabled", false);
    }

    private static int boundedIntegerEntry(Map<?, ?> data, String key,
                                           int fallback, int min, int max) {
        Object value = data.get(key);
        int numeric;
        try { numeric = value == null ? fallback : Integer.parseInt(value.toString()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid " + key); }
        if (numeric < min || numeric > max) throw new IllegalArgumentException("Out of range " + key);
        return numeric;
    }

    private static List<Map<String, Object>> customTasks() {
        List<Map<String, Object>> result = new ArrayList<>();
        var service = CustomTaskService.getInstance();
        for (CustomTaskService.SavedTaskEntry task : service.getAllSavedEntries()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("className", task.getClassName());
            item.put("name", task.getClassName());
            item.put("enabled", task.isEnabled());
            item.put("offsetMinutes", task.getOffsetMinutes());
            item.put("priority", task.getPriority());
            item.put("firstExecutionUtc", task.getFirstExecutionUtc());
            item.put("followUpDelayHours", task.getFollowUpDelayHours());
            item.put("loaded", service.isLoaded(task.getClassName()));
            result.add(item);
        }
        return result;
    }

    private static Object editCustomTask(Map<String, Object> data) {
        if (ScheduleService.obtain().getCoordinator().hasManagedQueues()) {
            throw new IllegalStateException("Stop the bot before modifying custom task scheduling");
        }
        var service = CustomTaskService.getInstance();
        String name = str(data, "className");
        if (!service.isLoaded(name)) throw new IllegalArgumentException("Unknown or unloaded custom task");
        String action = str(data, "action");
        switch (action) {
            case "enable", "disable", "save" -> {
                var previous = service.getAllSavedEntries().stream()
                        .filter(item -> name.equals(item.getClassName()))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown custom task"));
                int offset = boundedInteger(data, "offsetMinutes", previous.getOffsetMinutes(), 0, 10080);
                int priority = boundedInteger(data, "priority", previous.getPriority(), -1000, 1000);
                int followUp = boundedInteger(data, "followUpDelayHours",
                        previous.getFollowUpDelayHours() == null ? 8 : previous.getFollowUpDelayHours(),
                        1, 720);
                String next = Objects.toString(data.get("firstExecutionUtc"),
                        Objects.toString(previous.getFirstExecutionUtc(), ""));
                if (!next.isBlank()) {
                    try { java.time.Instant.parse(next); }
                    catch (Exception exception) { throw new IllegalArgumentException("Expected UTC ISO date-time"); }
                }
                if (action.equals("disable")) service.disableTask(name);
                else if (action.equals("enable")) service.enableTask(name, name, offset, priority,
                        next.isBlank() ? null : next, followUp);
                else service.saveTaskSettings(name, name, offset, priority,
                        next.isBlank() ? null : next, followUp);
            }
            case "delete" -> {
                var file = service.getSourceFile(name);
                Path workspace = WorkspacePaths.current().customTasks().toAbsolutePath().normalize();
                Path source = file == null ? null : file.toPath().toAbsolutePath().normalize();
                service.removeTask(name);
                if (source != null && workspace.equals(source.getParent())) {
                    try {
                        Files.deleteIfExists(source);
                        Files.deleteIfExists(workspace.resolve(name + ".json"));
                    } catch (IOException error) {
                        throw new IllegalStateException("Registry updated, but source removal failed");
                    }
                }
            }
            default -> throw new IllegalArgumentException("Invalid custom task action");
        }
        return Map.of("ok", true);
    }

    private static int boundedInteger(Map<String, Object> data, String key, int fallback, int min, int max) {
        Object value = data.get(key);
        int number;
        try { number = value == null ? fallback : Integer.parseInt(value.toString()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Invalid integer " + key); }
        if (number < min || number > max) throw new IllegalArgumentException("Out of range " + key);
        return number;
    }

    private static Map<String, Object> emulator() {
        var settings = ConfigService.obtain().loadGlobalSettings();
        String endpoints = settings == null ? ""
                : Objects.toString(settings.get(ConfigurationKeyEnum.SERVE_EMU_ADB_ENDPOINTS_STRING.name()), "");
        return Map.of("backend", "SERVE_EMU", "endpoints", endpoints,
                "selected", settings != null && "SERVE_EMU".equals(settings.get(
                        ConfigurationKeyEnum.CURRENT_EMULATOR_STRING.name())));
    }

    private static AccountDescriptor profile(String id) {
        long value;
        try { value = Long.parseLong(Objects.requireNonNull(id)); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid profile ID"); }
        return ProfileService.obtain().fetchAllAccounts().stream()
                .filter(p -> Objects.equals(p.getId(), value))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown profile"));
    }

    private static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return text;
    }

    private static boolean bool(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Boolean b)) throw new IllegalArgumentException("Missing boolean " + key);
        return b;
    }

    private static String asText(LocalDateTime time) {
        return time == null ? null : time.toString();
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> result = new LinkedHashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw != null) {
            for (String part : raw.split("&")) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2) result.put(
                        URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(HttpExchange ex) throws IOException {
        if (!Objects.toString(ex.getRequestHeaders().getFirst("Content-Type"), "")
                .toLowerCase().startsWith("application/json")) {
            throw new IllegalArgumentException("Expected application/json");
        }
        byte[] raw = ex.getRequestBody().readNBytes(MAX_BODY + 1);
        if (raw.length > MAX_BODY) throw new IllegalArgumentException("Request too large");
        return JSON.readValue(raw, Map.class);
    }

    private static void write(HttpExchange ex, int code, Object value) throws IOException {
        byte[] body = JSON.writeValueAsBytes(value);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(code, body.length);
        try (var stream = ex.getResponseBody()) { stream.write(body); }
    }
}
