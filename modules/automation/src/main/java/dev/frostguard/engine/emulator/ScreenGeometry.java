package dev.frostguard.engine.emulator;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;

/**
 * BlueStacks portrait capture adaptation. UI recognition always sees the
 * historical 720x1280 coordinate system; Android input uses device pixels.
 * Landscape/unsupported captures are intentionally not transformed.
 */
public final class ScreenGeometry {
    public static final int REFERENCE_WIDTH = 720;
    public static final int REFERENCE_HEIGHT = 1280;
    private final int sourceWidth;
    private final int sourceHeight;

    private ScreenGeometry(int width, int height) {
        sourceWidth = width;
        sourceHeight = height;
    }

    public static ScreenGeometry forPortraitCapture(RawImageData image) {
        if (image == null || !image.isValid()) return null;
        int width = image.getWidth(), height = image.getHeight();
        if (width > height || Math.abs(width * 16L - height * 9L) > width * 16L / 33L) {
            return null;
        }
        return new ScreenGeometry(width, height);
    }

    public boolean needsResize() {
        return sourceWidth != REFERENCE_WIDTH || sourceHeight != REFERENCE_HEIGHT;
    }

    public PointData toDevice(PointData point) {
        int x = Math.max(0, Math.min(sourceWidth - 1,
                (int) Math.round(point.getX() * (double) sourceWidth / REFERENCE_WIDTH)));
        int y = Math.max(0, Math.min(sourceHeight - 1,
                (int) Math.round(point.getY() * (double) sourceHeight / REFERENCE_HEIGHT)));
        return new PointData(x, y);
    }

    public RawImageData normalize(RawImageData capture) {
        if (!needsResize()) return capture;
        int pixelBytes = capture.getBytesPerPixel();
        if (pixelBytes != 2 && pixelBytes != 4) {
            throw new IllegalArgumentException("Unsupported BlueStacks capture depth");
        }
        byte[] source = capture.getData();
        byte[] target = new byte[REFERENCE_WIDTH * REFERENCE_HEIGHT * pixelBytes];
        for (int y = 0; y < REFERENCE_HEIGHT; y++) {
            // Sampling pixel centers reduces bias at the screen edges.
            double sy = (y + 0.5) * sourceHeight / REFERENCE_HEIGHT - 0.5;
            int y0 = Math.max(0, Math.min(sourceHeight - 1, (int) Math.floor(sy)));
            int y1 = Math.min(sourceHeight - 1, y0 + 1);
            double fy = Math.max(0, Math.min(1, sy - y0));
            for (int x = 0; x < REFERENCE_WIDTH; x++) {
                double sx = (x + 0.5) * sourceWidth / REFERENCE_WIDTH - 0.5;
                int x0 = Math.max(0, Math.min(sourceWidth - 1, (int) Math.floor(sx)));
                int x1 = Math.min(sourceWidth - 1, x0 + 1);
                double fx = Math.max(0, Math.min(1, sx - x0));
                int dest = (y * REFERENCE_WIDTH + x) * pixelBytes;
                if (pixelBytes == 2) {
                    int nearestX = Math.min(sourceWidth - 1, Math.max(0, (int) Math.round(sx)));
                    int nearestY = Math.min(sourceHeight - 1, Math.max(0, (int) Math.round(sy)));
                    int src = (nearestY * sourceWidth + nearestX) * pixelBytes;
                    System.arraycopy(source, src, target, dest, pixelBytes);
                } else {
                    int a = (y0 * sourceWidth + x0) * pixelBytes;
                    int b = (y0 * sourceWidth + x1) * pixelBytes;
                    int c = (y1 * sourceWidth + x0) * pixelBytes;
                    int d = (y1 * sourceWidth + x1) * pixelBytes;
                    for (int channel = 0; channel < pixelBytes; channel++) {
                        double top = (source[a + channel] & 255) * (1 - fx)
                                + (source[b + channel] & 255) * fx;
                        double bottom = (source[c + channel] & 255) * (1 - fx)
                                + (source[d + channel] & 255) * fx;
                        target[dest + channel] = (byte) Math.round(top * (1 - fy) + bottom * fy);
                    }
                }
            }
        }
        return RawImageData.capture(target, REFERENCE_WIDTH, REFERENCE_HEIGHT, capture.getBpp());
    }
}
