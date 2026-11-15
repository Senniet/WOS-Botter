package dev.frostguard.app.panel.misc;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import javax.imageio.ImageIO;

/**
 * Operator-labelled, local-only screen reference library and conservative
 * classification. Capturing a screen never clicks or changes the game.
 * Only two agreeing positive frames AND two negative screen groups can
 * establish a context-specific verification. It is not proof of a safe click.
 */
public final class ScreenTemplateAudit {
    public static final int MAX_POSITIVE_SCREENS = 4;
    public static final int MAX_NEGATIVE_GROUPS = 3;
    public static final double STRONG_MATCH = 90.0;
    public static final int POSITION_TOLERANCE = 24;
    private static final DateTimeFormatter CAPTURE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT);

    private ScreenTemplateAudit() {}

    public record Match(double score, int x, int y) {
        public boolean strong() { return Double.isFinite(score) && score >= STRONG_MATCH && x >= 0 && y >= 0; }
    }

    public enum Verdict {
        VERIFIED_CONTEXT,
        CONSISTENT_NEEDS_NEGATIVES,
        CROSS_SCREEN_COLLISION,
        POSITION_INCONSISTENT,
        NOT_CONFIRMED,
        NEEDS_TWO_FRAMES,
        SMALL_TEMPLATE_REVIEW,
        GENERIC_GROUP_REVIEW
    }

    public static Verdict classify(List<Match> positive, List<Match> negative,
                                   int independentNegativeGroups, int templateWidth, int templateHeight) {
        if (positive.size() < 2) return Verdict.NEEDS_TWO_FRAMES;
        int required = Math.max(2, (positive.size() * 3 + 3) / 4); // at least 75% strong
        List<Match> strong = positive.stream().filter(Match::strong).toList();
        if (strong.size() < required) return Verdict.NOT_CONFIRMED;
        int cluster = 0;
        for (Match candidate : strong) {
            int near = 0;
            for (Match other : strong) {
                if (Math.abs(candidate.x() - other.x())
                        + Math.abs(candidate.y() - other.y()) <= POSITION_TOLERANCE) near++;
            }
            cluster = Math.max(cluster, near);
        }
        if (cluster < required) return Verdict.POSITION_INCONSISTENT;
        // Small symbols (digits, plus/minus, etc.) can match unrelated UI.
        if (templateWidth < 18 || templateHeight < 18) return Verdict.SMALL_TEMPLATE_REVIEW;
        if (negative.stream().anyMatch(Match::strong)) return Verdict.CROSS_SCREEN_COLLISION;
        if (independentNegativeGroups < 2) return Verdict.CONSISTENT_NEEDS_NEGATIVES;
        return Verdict.VERIFIED_CONTEXT;
    }

    /** A resource folder like serveemu contains templates from many real screens. */
    public static Verdict restrictSharedGroup(String group, boolean explicitlyAssigned, Verdict verdict) {
        if (!explicitlyAssigned && verdict == Verdict.VERIFIED_CONTEXT
                && java.util.Set.of("serveemu", "common", "numbers", "navigation", "validation",
                        "shortcuts", "sidebar", "leftmenu", "skip").contains(group)) {
            return Verdict.GENERIC_GROUP_REVIEW;
        }
        return verdict;
    }

    public static String groupForTemplate(Path file) {
        String normalized = file.toAbsolutePath().normalize().toString().replace('\\', '/');
        int start = normalized.lastIndexOf("/templates/");
        if (start < 0) return "unclassified";
        String relative = normalized.substring(start + "/templates/".length());
        int separator = relative.indexOf('/');
        if (separator > 0) return relative.substring(0, separator).toLowerCase(Locale.ROOT);
        if (relative.equalsIgnoreCase("city.png")) return "home";
        if (relative.equalsIgnoreCase("world.png")) return "world";
        return "unclassified";
    }

    /** Map legacy/shared image paths to the screen where they are expected. */
    public static Map<String, String> loadAssignments(Path root) throws IOException {
        Properties props = new Properties();
        Path path = root.resolve("template-groups.properties");
        if (Files.isRegularFile(path)) {
            try (var reader = Files.newBufferedReader(path)) { props.load(reader); }
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : props.stringPropertyNames()) {
            String group = props.getProperty(name);
            if (name.startsWith("/templates/") && group.matches("[a-z0-9_-]{2,40}")) {
                result.put(name, group);
            }
        }
        return result;
    }

    public static void assign(Path root, String resource, String group) throws IOException {
        if (resource == null || !resource.startsWith("/templates/")
                || group == null || !group.matches("[a-z0-9_-]{2,40}")) {
            throw new IllegalArgumentException("Invalid template or screen label");
        }
        Map<String, String> assignments = loadAssignments(root);
        assignments.put(resource, group);
        Properties props = new Properties();
        props.putAll(assignments);
        Files.createDirectories(root);
        Path staged = Files.createTempFile(root, "groups", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(staged)) {
                props.store(writer, "Local-only screen labels for template auditing");
            }
            try {
                Files.move(staged, root.resolve("template-groups.properties"),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(staged, root.resolve("template-groups.properties"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    public static String resourceForTemplate(Path template) {
        String path = template.toAbsolutePath().normalize().toString().replace('\\', '/');
        int marker = path.lastIndexOf("/templates/");
        return marker < 0 ? "" : path.substring(marker);
    }

    public static String assignedGroup(Path template, Map<String, String> assignments) {
        return assignments.getOrDefault(resourceForTemplate(template), groupForTemplate(template));
    }

    public static Path save(Path root, String group, Path screenshot) throws IOException {
        if (group == null || !group.matches("[a-z0-9_-]{2,40}")) {
            throw new IllegalArgumentException("Select a valid screen group");
        }
        BufferedImage image = ImageIO.read(screenshot.toFile());
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IOException("Screenshot is not a valid image");
        }
        // Avoid comparing matches from emulators with incompatible resolutions.
        for (List<Path> existing : loadScreens(root).values()) {
            if (existing.isEmpty()) continue;
            BufferedImage prior = ImageIO.read(existing.get(0).toFile());
            if (prior == null || image.getWidth() != prior.getWidth()
                    || image.getHeight() != prior.getHeight()) {
                throw new IOException("Different screenshot resolution; use one resolution per library");
            }
        }
        Path directory = root.resolve("screens").resolve(group);
        Files.createDirectories(directory);
        // Always store PNG; clipboard screenshots may have a different extension.
        Path target = directory.resolve(CAPTURE_TIMESTAMP.format(LocalDateTime.now())
                + "-" + UUID.randomUUID().toString().substring(0, 8) + ".png");
        if (!ImageIO.write(image, "png", target.toFile())) {
            throw new IOException("PNG image writer unavailable");
        }
        // Identical files are not independent confirmation frames.
        try (var files = Files.list(directory)) {
            for (Path existing : files.filter(Files::isRegularFile).toList()) {
                if (existing.equals(target) || !existing.toString().endsWith(".png")) continue;
                if (Files.size(existing) == Files.size(target) && Files.mismatch(existing, target) == -1) {
                    Files.deleteIfExists(target);
                    throw new IOException("Identical screenshot already saved. Capture a changed frame.");
                }
            }
        }
        // The same image cannot be evidence for two DIFFERENT screen labels.
        for (Map.Entry<String, List<Path>> entry : loadScreens(root).entrySet()) {
            if (entry.getKey().equals(group)) continue;
            for (Path existing : entry.getValue()) {
                if (Files.size(existing) == Files.size(target) && Files.mismatch(existing, target) == -1) {
                    Files.deleteIfExists(target);
                    throw new IOException("Identical screen already labelled as " + entry.getKey());
                }
            }
        }
        return target;
    }

    public static Map<String, List<Path>> loadScreens(Path root) throws IOException {
        Path folder = root.resolve("screens");
        Map<String, List<Path>> results = new LinkedHashMap<>();
        if (!Files.isDirectory(folder)) return results;
        try (var groups = Files.list(folder)) {
            for (Path group : groups.filter(Files::isDirectory).sorted().toList()) {
                if (!group.getFileName().toString().matches("[a-z0-9_-]{2,40}")) continue;
                try (var files = Files.list(group)) {
                    List<Path> screens = files.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                            .sorted(Comparator.reverseOrder()).limit(MAX_POSITIVE_SCREENS).toList();
                    if (!screens.isEmpty()) results.put(group.getFileName().toString(), screens);
                }
            }
        }
        return results;
    }

    public static List<Path> negativeScreens(Map<String, List<Path>> screens, String expectedGroup) {
        List<Path> negatives = new ArrayList<>();
        for (var entry : screens.entrySet()) {
            if (entry.getKey().equals(expectedGroup) || entry.getValue().isEmpty()) continue;
            negatives.add(entry.getValue().get(0));
            if (negatives.size() >= MAX_NEGATIVE_GROUPS) break;
        }
        return negatives;
    }

    public static String csvEscape(String text) {
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
