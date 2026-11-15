package dev.frostguard.engine.diagnostics;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.runtime.WorkspacePaths;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.vision.match.OpenCvPatternLocator;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

/**
 * Task-scoped, non-invasive template coach. Captures frames supplied by the *actual*
 * recognition calls (no separate ADB requests), produces repeatable evidence,
 * and stages candidate crops for review. NEVER replaces a live template.
 */
public final class TaskTemplateCoach {
    private static final AtomicReference<Session> CURRENT = new AtomicReference<>();
    private static final DateTimeFormatter ID_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);
    private static final int MAX_CAPTURES = 70;
    private static final int MAX_TEMPLATES = 120;
    private TaskTemplateCoach() {}

    public record Summary(String task, String phase, int checks, int hits, int misses,
                          int captures, int proposals, int repaired, String directory, String note) {}
    public record Proposal(String template, String reason, Path path, double similarity) {}

    private static final class Stats {
        final String name;
        final String resource;
        int calls, hits, misses, capturedMisses;
        int lastX = -1, lastY = -1;
        int templateWidth, templateHeight;
        Path positive;
        final List<Path> failures = new ArrayList<>();
        boolean proposed;
        boolean multiMatchObserved;
        boolean tappedAfterPositive;
        Stats(String name, String resource) { this.name = name; this.resource = resource; }
    }

    private static final class Session {
        final long profileId;
        final String device;
        final boolean autoApply;
        final TpDailyTaskEnum task;
        final Path dir;
        final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(12), work -> {
                    Thread thread = new Thread(work, "survivalops-task-template-coach");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.DiscardPolicy());
        final Map<String, Stats> stats = new ConcurrentHashMap<>();
        final List<Proposal> proposals = new CopyOnWriteArrayList<>();
        final ConcurrentLinkedDeque<String> observations = new ConcurrentLinkedDeque<>();
        final AtomicInteger bufferedEvents = new AtomicInteger();
        final AtomicInteger checks = new AtomicInteger(), hits = new AtomicInteger();
        final AtomicInteger captures = new AtomicInteger(), drops = new AtomicInteger();
        final AtomicInteger repaired = new AtomicInteger();
        volatile String phase = "ARMED";
        volatile String note = "Waiting for selected task to start in the existing task queue";
        volatile boolean active;
        volatile Thread ownerThread;
        volatile String outcome = "UNKNOWN";
        Session(long profileId, String device, TpDailyTaskEnum task, Path dir, boolean autoApply) {
            this.profileId = profileId; this.device = device; this.task = task; this.dir = dir;
            this.autoApply = autoApply;
        }
    }

    public static synchronized Summary arm(long profileId, String device, TpDailyTaskEnum task,
                                           boolean autoApply) {
        if (device == null || device.isBlank() || task == null || profileId < 0)
            throw new IllegalArgumentException("Choose an active account, emulator and ordinary task");
        Session previous = CURRENT.get();
        if (previous != null && (previous.active || previous.phase.equals("ARMED")
                || previous.phase.equals("FINALIZING")))
            throw new IllegalStateException("Stop or finish the previous coached task first");
        Path root = WorkspacePaths.current().cache().resolve("task-template-coach");
        try { pruneOldRuns(root); }
        catch (IOException ignored) { /* Retention errors must not block task execution */ }
        String id = ID_DATE.format(Instant.now()) + "-" + Integer.toUnsignedString(
                java.util.concurrent.ThreadLocalRandom.current().nextInt(), 36);
        Session session = new Session(profileId, device, task, root.resolve(id), autoApply);
        CURRENT.set(session);
        return snapshot();
    }

    public static synchronized void stop() {
        Session session = CURRENT.get();
        if (session == null) return;
        session.active = false;
        if (!session.phase.equals("COMPLETED")) {
            session.phase = "STOPPED";
            session.note = "Monitoring stopped; scheduled game task was NOT cancelled";
        }
        // Don't interrupt capture writes; preserve a partial report after cancellation.
        session.writer.shutdown();
        Thread partial = new Thread(() -> {
            try {
                session.writer.awaitTermination(20, TimeUnit.SECONDS);
                if (!Files.isDirectory(session.dir)) return; // stopped before task started
                List<String> report = new ArrayList<>();
                report.add("diagnostics_stopped=true; game task was not cancelled");
                report.add("task=" + session.task.name() + " checks=" + session.checks
                        + " hits=" + session.hits + " captures=" + session.captures);
                session.stats.values().stream().sorted(Comparator.comparingInt((Stats stat) -> stat.misses).reversed())
                        .forEach(stat -> report.add(stat.name + " hits=" + stat.hits + " misses=" + stat.misses));
                Files.write(session.dir.resolve("summary.txt"), report, StandardCharsets.UTF_8);
            } catch (Exception ignored) { /* preserving the live game task matters more */ }
        }, "survivalops-task-coach-stop-report");
        partial.setDaemon(true);
        partial.start();
    }

    /** The task queue calls this on the same thread as the actual run. */
    public static void taskStarted(Long profileId, String device, TpDailyTaskEnum task) {
        Session session = CURRENT.get();
        if (session == null || !session.phase.equals("ARMED") || profileId == null
                || session.profileId != profileId || !session.device.equals(device) || session.task != task) return;
        session.phase = "RUNNING";
        session.ownerThread = Thread.currentThread();
        session.active = true;
        session.note = "Watching template calls for " + task.getName();
        try {
            Files.createDirectories(session.dir.resolve("captures"));
            Files.createDirectories(session.dir.resolve("proposals"));
            Files.writeString(session.dir.resolve("run.txt"),
                    "task=" + task.name() + "\nstarted=" + Instant.now() + "\n"
                    + "mode=" + (session.autoApply ? "AUTO_APPLY_STRICTLY_VERIFIED" : "STAGE_ONLY")
                    + "\n", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            session.note = "Could not create audit directory: " + ex.getMessage();
            session.active = false;
            session.phase = "ERROR";
        }
    }

    public static void taskFinished(Long profileId, String device, TpDailyTaskEnum task, String result) {
        Session session = CURRENT.get();
        if (session == null || profileId == null || session.profileId != profileId
                || !session.device.equals(device) || session.task != task) return;
        session.active = false;
        session.outcome = result;
        if (session.phase.equals("STOPPED") || session.phase.equals("ERROR")) return;
        session.phase = "FINALIZING";
        // Final review happens only after capture queue drains; it cannot be dropped
        // by the bounded screenshot queue while the bot is running.
        session.writer.shutdown();
        Thread finalizer = new Thread(() -> {
            try {
                if (!session.writer.awaitTermination(45, TimeUnit.SECONDS)) {
                    session.note = "Capture backlog timed out; proposals could be incomplete";
                }
                List<String> report = new ArrayList<>();
                for (Stats stat : session.stats.values().stream()
                        .sorted(Comparator.comparingInt((Stats item) -> item.misses).reversed())
                        .limit(40).toList()) {
                    try { proposeIfSupported(session, stat); }
                    catch (Exception ex) { report.add("REPAIR_CHECK_ERROR " + stat.name + " "
                            + ex.getClass().getSimpleName()); }
                }
                List<String> events = new ArrayList<>();
                events.add("time_utc,template,method,threshold,score,hit,x,y");
                events.addAll(session.observations);
                Files.write(session.dir.resolve("recognition-events.csv"), events,
                        StandardCharsets.UTF_8);
                // Strict fail-safe: if a previously activated repair only misses in
                // the next coached execution, turn it off before the next task run.
                for (Stats stat : session.stats.values()) {
                    if (stat.misses >= 3 && stat.hits == 0
                            && VerifiedTemplateOverrides.isActive(stat.resource)) {
                        if (VerifiedTemplateOverrides.rollback(stat.resource)) {
                            report.add("AUTO_ROLLBACK " + stat.name
                                    + " — no confirmed hits in this run");
                        }
                    }
                }
                report.add("task=" + session.task.name() + " scheduler_outcome=" + result
                        + " (not proof of in-game completion)");
                report.add("calls=" + session.checks + " hits=" + session.hits
                        + " captures=" + session.captures + " dropped=" + session.drops);
                session.stats.values().stream().sorted(Comparator.comparingInt((Stats s) -> s.misses).reversed())
                        .forEach(stat -> report.add(stat.name + " calls=" + stat.calls + " hits="
                                + stat.hits + " misses=" + stat.misses + " evidence=" + stat.failures.size()
                                + (stat.proposed ? " PROPOSAL" : "")));
                for (Proposal p : session.proposals) {
                    report.add("REVIEW " + p.template + " " + p.reason + " " + p.path);
                }
                report.add("Verified overlays applied=" + session.repaired.get()
                        + "; base bundled assets are never overwritten. Rollback is available in Debugging.");
                report.add("Diagnostic snapshots copied to standard snapshot store="
                        + publishRepresentativeFailures(session) + " (LAN transfer only with existing diagnostics opt-in)");
                report.add("Strict gate: known prior location, same screen context, two improved frames, "
                        + "low false-positive scores on >=2 different screen groups.");
                report.add("Any incomplete evidence is REVIEW_ONLY and NOT activated.");
                report.add("APPLIED means detection candidate passed strict checks, NOT that the in-game task is solved. "
                        + "Rerun the task and verify its actual completion before marking SOLVED.");
                Files.write(session.dir.resolve("summary.txt"), report, StandardCharsets.UTF_8);
                session.note = "Task " + result + ": " + session.proposals.size()
                        + " proposed, " + session.repaired.get() + " verified repair(s) enabled, "
                        + session.drops.get() + " capture(s) dropped. Details in summary.txt";
                session.phase = "COMPLETED";
            } catch (Exception ex) {
                session.phase = "ERROR";
                session.note = "Final audit error: " + ex.getClass().getSimpleName() + " " + ex.getMessage();
            }
        }, "survivalops-task-coach-finalizer");
        finalizer.setDaemon(true);
        finalizer.start();
    }

    /** Called on the real recognition path; constant time unless a bounded capture is due. */
    public static void observe(String device, TemplatesEnum template, String method, double threshold,
                               ImageSearchResultData result, RawImageData frame) {
        Session session = CURRENT.get();
        if (session == null || !session.active || session.ownerThread != Thread.currentThread()
                || !session.device.equals(device)
                || frame == null || !frame.isValid() || template == null) return;
        session.checks.incrementAndGet();
        boolean hit = result != null && result.isFound();
        int posX = hit && result.getPoint() != null ? result.getPoint().getX() : -1;
        int posY = hit && result.getPoint() != null ? result.getPoint().getY() : -1;
        double score = result == null ? Double.NaN : result.getMatchScore();
        session.observations.addLast(String.format(Locale.ROOT,
                "%s,%s,%s,%.2f,%.2f,%s,%d,%d",
                Instant.now(), template.name(), method, threshold, score, hit, posX, posY));
        if (session.bufferedEvents.incrementAndGet() > 1200) {
            session.observations.pollFirst();
            session.bufferedEvents.decrementAndGet();
        }
        if (!hit) session.note = "Latest miss: " + template.name() + " ("
                + String.format(Locale.ROOT, "%.1f", score) + "%)";
        if (hit) session.hits.incrementAndGet();
        String resource = template.getTemplate();
        if (resource == null || !resource.startsWith("/templates/")) return;
        Stats stat = session.stats.get(template.name());
        if (stat == null) {
            if (session.stats.size() >= MAX_TEMPLATES) return;
            stat = session.stats.computeIfAbsent(template.name(), name -> new Stats(name, resource));
        }
        // Avoid expensive screenshot copies for most lookups. Capture the first good
        // location and 2-3 failures per template, and cap total evidence per run.
        synchronized (stat) {
            stat.calls++;
            if (method.startsWith("multi-" ) || method.equals("multi")) stat.multiMatchObserved = true;
            if (hit) {
                stat.hits++;
                if (result.getPoint() != null) {
                    stat.lastX = result.getPoint().getX();
                    stat.lastY = result.getPoint().getY();
                }
            } else stat.misses++;
            boolean capture = hit ? stat.positive == null && stat.hits == 1
                    : stat.misses >= 2 && stat.capturedMisses < 3;
            if (!capture || session.captures.get() >= MAX_CAPTURES) return;
            if (session.writer.getQueue().remainingCapacity() == 0) {
                session.drops.incrementAndGet(); return;
            }
            int number = session.captures.incrementAndGet();
            if (!hit) stat.capturedMisses++;
            Stats target = stat;
            int actualWidth = frame.getWidth(), actualHeight = frame.getHeight(), bpp = frame.getBpp();
            byte[] pixels = frame.getData().clone(); // asynchronous evidence must not track mutable ADB buffer
            session.writer.execute(() -> {
                try {
                    Path file = session.dir.resolve("captures").resolve(String.format(Locale.ROOT,
                            "%03d-%s-%s.png", number, target.name.toLowerCase(Locale.ROOT),
                            hit ? "hit" : "miss"));
                    BufferedImage image = ImageConverter.toBufferedImage(
                            RawImageData.capture(pixels, actualWidth, actualHeight, bpp));
                    ImageIO.write(image, "png", file.toFile());
                    synchronized (target) {
                        if (hit && target.positive == null) target.positive = file;
                        else if (!hit) target.failures.add(file);
                    }
                } catch (Exception ex) {
                    session.drops.incrementAndGet();
                }
            });
        }
    }

    /** Records actual game input so a claimed/disabled button cannot be
     * "repaired" into the old actionable button after the click. */
    public static void tapped(String device, PointData location) {
        Session session = CURRENT.get();
        if (session == null || !session.active || session.ownerThread != Thread.currentThread()
                || !session.device.equals(device) || location == null) return;
        for (Stats stat : session.stats.values()) {
            synchronized (stat) {
                if (stat.lastX >= 0 && stat.lastY >= 0 && stat.hits > 0
                        && Math.abs(location.getX() - stat.lastX) <= 55
                        && Math.abs(location.getY() - stat.lastY) <= 55) {
                    stat.tappedAfterPositive = true;
                }
            }
        }
    }

    static boolean mayAutomaticallyApply(String outcome, boolean tapped, boolean multi) {
        return !tapped && !multi && "FAILED".equals(outcome);
    }

    private static void proposeIfSupported(Session session, Stats stat) throws IOException {
        Path success;
        List<Path> failures;
        int x, y;
        synchronized (stat) {
            success = stat.positive;
            failures = List.copyOf(stat.failures);
            x = stat.lastX; y = stat.lastY;
        }
        if (failures.size() < 2) return;
        if (success == null || x < 0 || y < 0) {
            proposeUnconfirmedLocation(session, stat, failures);
            return;
        }
        try (InputStream resource = TaskTemplateCoach.class.getResourceAsStream(stat.resource)) {
            if (resource == null) return;
            byte[] oldBytes = resource.readAllBytes();
            BufferedImage original = ImageIO.read(new java.io.ByteArrayInputStream(oldBytes));
            if (original == null || original.getWidth() < 24 || original.getHeight() < 24) return;
            int width = original.getWidth(), height = original.getHeight();
            BufferedImage prior = ImageIO.read(success.toFile());
            List<BufferedImage> frames = new ArrayList<>();
            for (Path failed : failures) frames.add(ImageIO.read(failed.toFile()));
            if (prior == null || frames.stream().anyMatch(f -> f == null || x - width / 2 < 0
                    || y - height / 2 < 0 || x - width / 2 + width > f.getWidth()
                    || y - height / 2 + height > f.getHeight())) return;
            BufferedImage first = frames.get(0).getSubimage(x - width / 2, y - height / 2, width, height);
            BufferedImage second = frames.get(1).getSubimage(x - width / 2, y - height / 2, width, height);
            double difference = averageRgbDifference(first, second);
            if (difference > 18.0 || difference < 0.8 || averageRgbDifference(first, original) < 5.0) return;
            Path candidate = session.dir.resolve("proposals").resolve(stat.name.toLowerCase(Locale.ROOT) + ".png");
            if (!ImageIO.write(first, "png", candidate.toFile())) return;
            stat.proposed = true;
            String status = "REVIEW_ONLY: stable crop at a previously recognized position";
            boolean failedTask = session.outcome.equals("FAILED");
            if (session.autoApply && mayAutomaticallyApply(session.outcome,
                    stat.tappedAfterPositive, stat.multiMatchObserved)
                    && !VerifiedTemplateOverrides.isActive(stat.resource)) {
                String validation = validateSafely(stat.resource, oldBytes, candidate, prior,
                        frames, x, y, width, height);
                status = validation;
                if (validation.equals("VERIFIED")) {
                    VerifiedTemplateOverrides.activate(stat.resource, candidate,
                            x, y, prior.getWidth(), prior.getHeight());
                    session.repaired.incrementAndGet();
                    status = "APPLIED (reversible detection overlay; RETEST task outcome; original unchanged)";
                }
            }
            if (stat.tappedAfterPositive) status = "REVIEW_ONLY: button was tapped; state change may be intentional";
            else if (!failedTask) status = "REVIEW_ONLY: task did not fail; button may have changed after success";
            else if (stat.multiMatchObserved) status = "REVIEW_ONLY: multi-match templates require separate validation";
            session.proposals.add(new Proposal(stat.name, status, candidate, difference));
        }
    }

    /** Best-match candidate for templates never found during the task. This is
     * never installed automatically: a good-looking location may be unrelated UI.
     */
    private static void proposeUnconfirmedLocation(Session session, Stats stat,
                                                    List<Path> failures) throws IOException {
        if (session.proposals.size() >= 16) return;
        try (InputStream resource = TaskTemplateCoach.class.getResourceAsStream(stat.resource)) {
            if (resource == null) return;
            BufferedImage old = ImageIO.read(resource);
            if (old == null || old.getWidth() < 24 || old.getHeight() < 24) return;
            BufferedImage first = ImageIO.read(failures.get(0).toFile());
            BufferedImage second = ImageIO.read(failures.get(1).toFile());
            if (first == null || second == null || first.getWidth() != second.getWidth()
                    || first.getHeight() != second.getHeight()) return;
            ImageSearchResultData a = OpenCvPatternLocator.locatePattern(asRaw(first),
                    stat.resource, new PointData(0, 0),
                    new PointData(first.getWidth(), first.getHeight()), 0);
            ImageSearchResultData b = OpenCvPatternLocator.locatePattern(asRaw(second),
                    stat.resource, new PointData(0, 0),
                    new PointData(second.getWidth(), second.getHeight()), 0);
            if (a == null || b == null || a.getPoint() == null || b.getPoint() == null
                    || a.getMatchScore() < 60 || b.getMatchScore() < 60
                    || a.getMatchScore() >= 90 || b.getMatchScore() >= 90) return;
            int x = a.getPoint().getX(), y = a.getPoint().getY();
            if (Math.abs(x - b.getPoint().getX()) + Math.abs(y - b.getPoint().getY()) > 14
                    || x - old.getWidth() / 2 < 0 || y - old.getHeight() / 2 < 0
                    || x - old.getWidth() / 2 + old.getWidth() > first.getWidth()
                    || y - old.getHeight() / 2 + old.getHeight() > first.getHeight()) return;
            BufferedImage cropped = first.getSubimage(x - old.getWidth() / 2,
                    y - old.getHeight() / 2, old.getWidth(), old.getHeight());
            Path candidate = session.dir.resolve("proposals").resolve(stat.name.toLowerCase(Locale.ROOT) + "-unverified.png");
            if (!ImageIO.write(cropped, "png", candidate.toFile())) return;
            stat.proposed = true;
            session.proposals.add(new Proposal(stat.name,
                    String.format(Locale.ROOT,
                            "REVIEW_ONLY: unconfirmed position (%d,%d), two possible scores %.1f/%.1f; never auto-activated",
                            x, y, a.getMatchScore(), b.getMatchScore()), candidate, 0));
        }
    }

    /** Strict automatic gate: same scene structure, two strong independent frame
     * confirmations, demonstrable improvement, and two negative screen groups.
     * Absent evidence results in REVIEW_ONLY, never a silent replacement.
     */
    private static String validateSafely(String resource, byte[] old, Path candidate,
                                         BufferedImage prior, List<BufferedImage> frames,
                                         int x, int y, int width, int height) {
        try {
            if (frames.size() < 2 || frames.get(0).getWidth() != prior.getWidth()
                    || frames.get(0).getHeight() != prior.getHeight()) return "REVIEW_ONLY: resolution mismatch";
            for (int i = 0; i < 2; i++) {
                if (frames.get(i).getWidth() != prior.getWidth()
                        || frames.get(i).getHeight() != prior.getHeight()
                        || !sameScreenOutsideTarget(prior, frames.get(i), x, y, width, height)) {
                    return "REVIEW_ONLY: screen context changed";
                }
            }
            byte[] next = Files.readAllBytes(candidate);
            for (int i = 0; i < 2; i++) {
                RawImageData image = asRaw(frames.get(i));
                PointData tl = new PointData(Math.max(0, x - width / 2 - 14),
                        Math.max(0, y - height / 2 - 14));
                PointData br = new PointData(Math.min(image.getWidth(), x + width / 2 + 14),
                        Math.min(image.getHeight(), y + height / 2 + 14));
                ImageSearchResultData oldResult = OpenCvPatternLocator.matchFromRawTemplate(image, old, tl, br, 0);
                ImageSearchResultData replacement = OpenCvPatternLocator.matchFromRawTemplate(image, next, tl, br, 95);
                if (replacement == null || !replacement.isFound() || replacement.getPoint() == null
                        || replacement.getMatchScore() < 95
                        || Math.abs(replacement.getPoint().getX() - x) > 10
                        || Math.abs(replacement.getPoint().getY() - y) > 10
                        || oldResult == null || oldResult.getMatchScore() >= 88
                        || replacement.getMatchScore() - oldResult.getMatchScore() < 7)
                    return "REVIEW_ONLY: weak or ambiguous improvement";
            }
            Path libraryRoot = WorkspacePaths.current().cache().resolve("template-screen-library");
            String group = expectedGroup(libraryRoot, resource);
            if (group == null) return "REVIEW_ONLY: template requires an explicit screen label";
            Path screens = libraryRoot.resolve("screens");
            if (!Files.isDirectory(screens)) return "REVIEW_ONLY: no negative-screen library";
            int groups = 0;
            try (var dirs = Files.list(screens)) {
                for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                    if (dir.getFileName().toString().equals(group)) continue;
                    try (var files = Files.list(dir)) {
                        Path reference = files.filter(Files::isRegularFile)
                                .filter(p -> p.toString().endsWith(".png"))
                                .sorted(Comparator.reverseOrder()).findFirst().orElse(null);
                        if (reference == null) continue;
                        BufferedImage negative = ImageIO.read(reference.toFile());
                        if (negative == null || negative.getWidth() != prior.getWidth()
                                || negative.getHeight() != prior.getHeight()) continue;
                        RawImageData raw = asRaw(negative);
                        ImageSearchResultData falseHit = OpenCvPatternLocator.matchFromRawTemplate(
                                raw, next, new PointData(0, 0),
                                new PointData(raw.getWidth(), raw.getHeight()), 0);
                        if (falseHit == null || !Double.isFinite(falseHit.getMatchScore())
                                || falseHit.getMatchScore() >= 80)
                            return "REVIEW_ONLY: possible false positive on " + dir.getFileName();
                        groups++;
                    }
                    if (groups >= 8) break; // check up to eight distinct negative contexts
                }
            }
            return groups < 2 ? "REVIEW_ONLY: need two independent negative screen groups" : "VERIFIED";
        } catch (Exception ex) {
            return "REVIEW_ONLY: validation unavailable (" + ex.getClass().getSimpleName() + ")";
        }
    }

    private static String expectedGroup(Path libraryRoot, String resource) throws IOException {
        Properties groups = new Properties();
        Path properties = libraryRoot.resolve("template-groups.properties");
        if (Files.isRegularFile(properties)) {
            try (var reader = Files.newBufferedReader(properties)) { groups.load(reader); }
        }
        String explicit = groups.getProperty(resource);
        if (explicit != null && explicit.matches("[a-z0-9_-]{2,40}")) return explicit;
        String relative = resource.substring("/templates/".length());
        int slash = relative.indexOf('/');
        if (slash < 0) return relative.equals("city.png") ? "home" : null;
        String folder = relative.substring(0, slash);
        if (Set.of("serveemu", "common", "numbers", "navigation", "validation",
                "shortcuts", "sidebar", "leftmenu", "skip").contains(folder)) return null;
        return folder;
    }

    /** Rough structural consistency check with the template rectangle excluded. */
    static boolean sameScreenOutsideTarget(BufferedImage before, BufferedImage after,
                                           int x, int y, int width, int height) {
        if (before.getWidth() != after.getWidth() || before.getHeight() != after.getHeight()) return false;
        double diff = 0;
        int samples = 0;
        for (int row = 16; row < before.getHeight() - 16; row += 19)
            for (int col = 16; col < before.getWidth() - 16; col += 19) {
                if (Math.abs(col - x) < width / 2 + 24 && Math.abs(row - y) < height / 2 + 24) continue;
                int a = before.getRGB(col, row), b = after.getRGB(col, row);
                diff += (Math.abs((a >> 16 & 255) - (b >> 16 & 255))
                        + Math.abs((a >> 8 & 255) - (b >> 8 & 255))
                        + Math.abs((a & 255) - (b & 255))) / 3.0;
                samples++;
            }
        return samples >= 100 && diff / samples < 23;
    }

    static RawImageData asRaw(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        byte[] pixels = new byte[w * h * 4];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int argb = image.getRGB(x, y);
            int pos = (y * w + x) * 4;
            pixels[pos] = (byte) (argb >>> 16);
            pixels[pos + 1] = (byte) (argb >>> 8);
            pixels[pos + 2] = (byte) argb;
            pixels[pos + 3] = (byte) 255;
        }
        return RawImageData.capture(pixels, w, h, 32);
    }

    static double averageRgbDifference(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return Double.POSITIVE_INFINITY;
        double sum = 0;
        for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++) {
            int p = a.getRGB(x, y), q = b.getRGB(x, y);
            sum += (Math.abs((p >>> 16 & 255) - (q >>> 16 & 255))
                    + Math.abs((p >>> 8 & 255) - (q >>> 8 & 255))
                    + Math.abs((p & 255) - (q & 255))) / 3.0;
        }
        return sum / (a.getWidth() * (double) a.getHeight());
    }

    /** Existing diagnostics settings govern storage and LAN sharing. This
     * reuses the established, explicitly opt-in upload path; never bypass it.
     */
    private static int publishRepresentativeFailures(Session session) {
        DiagnosticSnapshotStore store = DiagnosticSnapshotStore.forCurrentWorkspace();
        if (!store.isEnabled()) return 0;
        int copied = 0;
        for (Stats stat : session.stats.values().stream()
                .sorted(Comparator.comparingInt((Stats item) -> item.misses).reversed()).toList()) {
            if (copied >= 4) break;
            List<Path> frames;
            synchronized (stat) { frames = List.copyOf(stat.failures); }
            if (frames.isEmpty()) continue;
            try {
                BufferedImage image = ImageIO.read(frames.get(0).toFile());
                if (image == null) continue;
                if (store.write(asRaw(image), "taskcoach" + session.task.name().toLowerCase(Locale.ROOT),
                        "miss-" + stat.name.toLowerCase(Locale.ROOT), Instant.now()).isPresent()) copied++;
            } catch (Exception ignored) {
                // Nonessential upload may never change game task behavior.
            }
        }
        return copied;
    }

    private static void pruneOldRuns(Path root) throws IOException {
        if (!Files.isDirectory(root)) return;
        try (var dirs = Files.list(root)) {
            List<Path> all = dirs.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().matches("\\d{8}-\\d{6}-[a-z0-9]+"))
                    .sorted(Comparator.reverseOrder()).toList();
            for (int index = 15; index < all.size(); index++) {
                try (var files = Files.walk(all.get(index))) {
                    for (Path entry : files.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(entry);
                    }
                }
            }
        }
    }

    public static Summary snapshot() {
        Session session = CURRENT.get();
        if (session == null) return new Summary("", "IDLE", 0, 0, 0, 0, 0, 0, "", "No coached task selected");
        return new Summary(session.task.getName(), session.phase, session.checks.get(), session.hits.get(),
                session.checks.get() - session.hits.get(), session.captures.get(), session.proposals.size(),
                session.repaired.get(), session.dir.toString(), session.note);
    }
}
