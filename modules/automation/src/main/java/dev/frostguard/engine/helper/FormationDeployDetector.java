package dev.frostguard.engine.helper;

import dev.frostguard.api.domain.ImageSearchResultData;
import java.awt.image.BufferedImage;

/**
 * Secondary formation-specific visual evidence, when text templates miss
 * BlueStacks' scaled blue Deploy control. Coordinates use 720x1280 normalized
 * portrait captures. Not a general-purpose button classifier.
 */
public final class FormationDeployDetector {
    private FormationDeployDetector() {}

    public static ImageSearchResultData detect(BufferedImage image) {
        if (image == null || image.getWidth() != 720 || image.getHeight() != 1280) {
            return ImageSearchResultData.miss();
        }
        // These patches are the blue interior, not the text/icon or outer border.
        if (!blueFill(image, 445, 1193, 473, 1238, 0.85)
                || !blueFill(image, 630, 1193, 670, 1238, 0.80)
                || !blueFill(image, 430, 1188, 680, 1240, 0.55)) {
            return ImageSearchResultData.miss();
        }
        // The label region contains light letters with dark outlines;
        // plain blue navigation bars cannot satisfy this evidence.
        int white = countWhite(image, 505, 1185, 600, 1214);
        if (white < 110 || white > 1800) return ImageSearchResultData.miss();

        // Click only inside the demonstrated button, not on the stamina cost.
        return ImageSearchResultData.hit(548, 1210, 90, 110, 30);
    }

    private static boolean blueFill(BufferedImage image, int x0, int y0,
                                    int x1, int y1, double required) {
        int seen = 0, blue = 0;
        for (int y = y0; y < y1; y += 3) {
            for (int x = x0; x < x1; x += 3) {
                int rgb = image.getRGB(x, y);
                int r = rgb >>> 16 & 255, g = rgb >>> 8 & 255, b = rgb & 255;
                if (b > 205 && g > 110 && r < 175 && b - r > 75) blue++;
                seen++;
            }
        }
        return seen > 0 && blue >= seen * required;
    }

    private static int countWhite(BufferedImage image, int x0, int y0, int x1, int y1) {
        int white = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int rgb = image.getRGB(x, y);
                if ((rgb >>> 16 & 255) > 205
                        && (rgb >>> 8 & 255) > 205
                        && (rgb & 255) > 205) white++;
            }
        }
        return white;
    }
}
