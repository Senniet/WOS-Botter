package dev.frostguard.engine.diagnostics;

import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.api.runtime.WorkspacePaths;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/** Local reversible template overlays; bundled PNG files are never modified. */
public final class VerifiedTemplateOverrides {
    private VerifiedTemplateOverrides() {}
    private static final ConcurrentHashMap<String, Boolean> ACTIVE = new ConcurrentHashMap<>();

    static Path root() {
        return WorkspacePaths.current().cache().resolve("task-template-coach").resolve("verified-overrides");
    }

    static Path pathFor(String resource) {
        if (resource == null || !resource.matches("/templates/[a-zA-Z0-9_/.-]+\\.png")
                || resource.contains("..")) throw new IllegalArgumentException("Invalid template resource");
        Path root = root();
        Path path = root.resolve(resource.substring("/templates/".length())).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Unsafe template path");
        return path;
    }

    public static boolean isActive(String resource) {
        return ACTIVE.computeIfAbsent(resource, r -> {
            try { return Files.isRegularFile(pathFor(r)); }
            catch (IllegalArgumentException ex) { return false; }
        });
    }

    /** Persist an independently verified repair atomically and retain its metadata. */
    public static synchronized Path activate(String resource, Path candidate,
                                            int centerX, int centerY, int frameWidth, int frameHeight)
            throws IOException {
        Path target = pathFor(resource);
        if (!Files.isRegularFile(candidate) || Files.size(candidate) > 1_000_000L)
            throw new IOException("Invalid candidate file");
        var image = javax.imageio.ImageIO.read(candidate.toFile());
        if (image == null || image.getWidth() < 24 || image.getHeight() < 24
                || image.getWidth() > 500 || image.getHeight() > 500)
            throw new IOException("Candidate image size or contents are invalid");
        if (centerX < 0 || centerY < 0 || frameWidth < 1 || frameHeight < 1)
            throw new IllegalArgumentException("Invalid verified coordinates");
        if (Files.exists(target)) throw new IOException("Existing override must be rolled back first");
        Files.createDirectories(target.getParent());
        Path pending = Files.createTempFile(target.getParent(), "verified-", ".pending");
        Properties metadata = new Properties();
        metadata.setProperty("centerX", Integer.toString(centerX));
        metadata.setProperty("centerY", Integer.toString(centerY));
        metadata.setProperty("width", Integer.toString(frameWidth));
        metadata.setProperty("height", Integer.toString(frameHeight));
        Path meta = target.resolveSibling(target.getFileName() + ".properties");
        try {
            Files.copy(candidate, pending, StandardCopyOption.REPLACE_EXISTING);
            try (var output = Files.newOutputStream(meta)) { metadata.store(output, "Verified local template override"); }
            try { Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(pending, target); }
            ACTIVE.put(resource, true);
            return target;
        } finally { Files.deleteIfExists(pending); }
    }

    public static synchronized boolean rollback(String resource) throws IOException {
        Path target = pathFor(resource);
        boolean existed = Files.deleteIfExists(target);
        Files.deleteIfExists(target.resolveSibling(target.getFileName() + ".properties"));
        ACTIVE.put(resource, false);
        return existed;
    }

    /** Only accept very strong matches near a pre-validated spot at the same resolution. */
    public static ImageSearchResultData tryMatch(RawImageData frame, String resource, double threshold,
                                                   PointData searchTopLeft, PointData searchBottomRight) {
        if (resource == null || !isActive(resource) || frame == null || !frame.isValid()) return null;
        Path target;
        try { target = pathFor(resource); }
        catch (IllegalArgumentException ex) { return null; }
        if (!Files.isRegularFile(target)) { ACTIVE.put(resource, false); return null; }
        try {
            Properties props = new Properties();
            Path meta = target.resolveSibling(target.getFileName() + ".properties");
            if (!Files.isRegularFile(meta) || Files.size(target) > 1_000_000L) return null;
            try (var input = Files.newInputStream(meta)) { props.load(input); }
            int x = Integer.parseInt(props.getProperty("centerX"));
            int y = Integer.parseInt(props.getProperty("centerY"));
            if (Integer.parseInt(props.getProperty("width")) != frame.getWidth()
                    || Integer.parseInt(props.getProperty("height")) != frame.getHeight()) return null;
            int margin = 32;
            int left = Math.max(Math.max(0, x - margin - 75), searchTopLeft.getX());
            int top = Math.max(Math.max(0, y - margin - 75), searchTopLeft.getY());
            int right = Math.min(Math.min(frame.getWidth(), x + margin + 75), searchBottomRight.getX());
            int bottom = Math.min(Math.min(frame.getHeight(), y + margin + 75), searchBottomRight.getY());
            if (right <= left || bottom <= top) return null;
            PointData tl = new PointData(left, top);
            PointData br = new PointData(right, bottom);
            ImageSearchResultData match = OpenCvPatternLocator.matchFromRawTemplate(frame,
                    Files.readAllBytes(target), tl, br, Math.max(95, threshold));
            if (match == null || !match.isFound() || match.getPoint() == null) return null;
            if (Math.abs(match.getPoint().getX() - x) > 16
                    || Math.abs(match.getPoint().getY() - y) > 16) return null;
            return match;
        } catch (RuntimeException | IOException ignored) {
            return null;
        }
    }
}
