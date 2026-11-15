package dev.frostguard.engine.diagnostics;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A bounded, self-contained Task Coach evidence ZIP. Does not include account
 * configuration, log files, secrets, databases, or arbitrary directories.
 */
final class TaskCoachEvidenceBundle {
    // Task Coach can produce 70 captures plus up to 16 proposals. The server
    // accepts a bounded total of at most 96 image files and 4 text/manifest files.
    private static final int MAX_IMAGE_FILES = 90;
    private static final long MAX_TOTAL_IMAGE_BYTES = 25L * 1024 * 1024;
    private static final long MAX_INDIVIDUAL_IMAGE_BYTES = 4L * 1024 * 1024;
    private static final long MAX_TEXT_BYTES = 3L * 1024 * 1024;
    private static final int MAX_ZIP_BYTES = 30 * 1024 * 1024;

    private TaskCoachEvidenceBundle() {}

    record Package(byte[] zip, String sessionId, int imageFiles, int omittedImages,
                   boolean includedScreenshots) {}

    static Package create(Path directory, boolean includeScreenshots) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IOException("Task Coach evidence directory missing");
        }
        String session = directory.getFileName().toString();
        if (!session.matches("[0-9]{8}-[0-9]{6}-[a-z0-9]{1,20}")) {
            throw new IOException("Invalid Task Coach session name");
        }
        List<Path> textFiles = new ArrayList<>();
        for (String name : List.of("summary.txt", "recognition-events.csv", "run.txt")) {
            Path file = directory.resolve(name);
            if (Files.isRegularFile(file) && Files.size(file) <= MAX_TEXT_BYTES) textFiles.add(file);
        }
        if (textFiles.stream().noneMatch(f -> f.getFileName().toString().equals("summary.txt"))
                || textFiles.stream().noneMatch(f -> f.getFileName().toString().equals("recognition-events.csv"))) {
            throw new IOException("Task Coach summary or recognition CSV not ready");
        }
        List<Path> candidates = new ArrayList<>();
        for (String subdir : List.of("proposals", "captures")) {
            Path folder = directory.resolve(subdir);
            if (!Files.isDirectory(folder)) continue;
            try (var files = Files.list(folder)) {
                candidates.addAll(files.filter(Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().matches("[a-zA-Z0-9_-]{1,115}\\.png"))
                        .sorted(Comparator.comparing(Path::getFileName)).toList());
            }
        }
        // Review proposals first. Captures are FULL resolution JPEGs, so all
        // phases of a coached run fit: old PNG uploads exhausted 25MiB before
        // important late task screenshots (e.g. Alliance Triumph claim state).
        // Originals remain untouched and lossless in the local workspace.
        candidates.sort(Comparator.comparingInt(TaskCoachEvidenceBundle::imagePriority)
                .thenComparing(Path::toString));
        int included = 0, omitted = 0, jpegCaptures = 0;
        long imageBytes = 0;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Path file : textFiles) {
                zip.putNextEntry(new ZipEntry(file.getFileName().toString()));
                zip.write(Files.readAllBytes(file));
                zip.closeEntry();
            }
            for (Path file : candidates) {
                if (!includeScreenshots) {
                    omitted++;
                    continue;
                }
                long length = Files.size(file);
                if (included >= MAX_IMAGE_FILES || length > MAX_INDIVIDUAL_IMAGE_BYTES) {
                    omitted++;
                    continue;
                }
                byte[] source = Files.readAllBytes(file);
                if (source.length < 8 || !isPng(source)) {
                    omitted++;
                    continue;
                }
                boolean isCapture = file.getParent().getFileName().toString().equals("captures");
                byte[] data = source;
                String name = file.getParent().getFileName() + "/" + file.getFileName();
                if (isCapture) {
                    data = fullResolutionJpeg(source);
                    name = name.substring(0, name.length() - 4) + ".jpg";
                }
                if (imageBytes + data.length > MAX_TOTAL_IMAGE_BYTES) {
                    omitted++;
                    continue;
                }
                zip.putNextEntry(new ZipEntry(name));
                zip.write(data);
                zip.closeEntry();
                imageBytes += data.length;
                if (isCapture) jpegCaptures++;
                included++;
            }
            JSONObject manifest = new JSONObject()
                    .put("schema", 1)
                    .put("session_id", session)
                    .put("image_files_included", included)
                    .put("image_files_omitted", omitted)
                    .put("jpeg_captures_full_resolution", jpegCaptures)
                    .put("screenshots_enabled", includeScreenshots)
                    .put("description", "Task Coach local task evidence; no application configuration or database");
            zip.putNextEntry(new ZipEntry("upload-manifest.json"));
            zip.write(manifest.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        if (bytes.size() > MAX_ZIP_BYTES) {
            throw new IOException("Task Coach evidence archive exceeds 30 MiB limit");
        }
        return new Package(bytes.toByteArray(), session, included, omitted, includeScreenshots);
    }

    /** Full pixel dimensions, encoded as high-quality JPEG for evidence review.
     * Never use these lossy copies as automatic template-repair candidates;
     * proposal PNGs are transferred without modification.
     */
    static byte[] fullResolutionJpeg(byte[] png) throws IOException {
        BufferedImage original = ImageIO.read(new java.io.ByteArrayInputStream(png));
        if (original == null || original.getWidth() <= 0 || original.getHeight() <= 0
                || (long) original.getWidth() * original.getHeight() > 4_000_000L)
            throw new IOException("Invalid evidence frame dimensions");
        BufferedImage rgb = new BufferedImage(original.getWidth(), original.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        var graphics = rgb.createGraphics();
        try { graphics.drawImage(original, 0, 0, null); }
        finally { graphics.dispose(); }
        var writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("No JPEG writer available");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream encoded = new ByteArrayOutputStream(180_000);
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(encoded)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.9f);
            writer.write(null, new javax.imageio.IIOImage(rgb, null, null), params);
            output.flush();
        } finally { writer.dispose(); }
        return encoded.toByteArray();
    }

    private static int imagePriority(Path file) {
        String path = file.toString().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (path.contains("/proposals/")) return 0;
        if (path.contains("screen-blocker") || path.contains("-miss")) return 1;
        return 2;
    }

    private static boolean isPng(byte[] data) {
        byte[] magic = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        for (int i = 0; i < magic.length; i++) if (data[i] != magic[i]) return false;
        return true;
    }
}
