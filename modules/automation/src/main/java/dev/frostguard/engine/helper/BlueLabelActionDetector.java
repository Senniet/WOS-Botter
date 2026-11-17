package dev.frostguard.engine.helper;

import dev.frostguard.api.domain.ImageSearchResultData;
import java.awt.image.BufferedImage;

/** Reusable colour-and-label fallback for known blue action button regions.
 * The caller must independently verify the current screen and action postcondition.
 * Never infer an action from a blue rectangle alone.
 */
public final class BlueLabelActionDetector {
    private BlueLabelActionDetector() {}

    public record Region(int left, int top, int right, int bottom,
                         int labelLeft, int labelTop, int labelRight, int labelBottom) {
        public Region {
            if (left < 0 || top < 0 || right <= left || bottom <= top
                    || labelLeft < left || labelRight > right || labelTop < top
                    || labelBottom > bottom || labelLeft >= labelRight || labelTop >= labelBottom) {
                throw new IllegalArgumentException("Invalid blue action button region");
            }
        }
    }

    public static ImageSearchResultData detect(BufferedImage image, Region area) {
        if (image == null || area == null || image.getWidth() != 720 || image.getHeight() != 1280
                || area.right() > image.getWidth() || area.bottom() > image.getHeight())
            return ImageSearchResultData.miss();
        int width = area.right() - area.left(), height = area.bottom() - area.top();
        if (!blueFraction(image, area.left() + 8, area.top() + height / 3,
                area.left() + width / 5, area.bottom() - height / 5, 0.80)
                || !blueFraction(image, area.right() - width / 5, area.top() + height / 3,
                area.right() - 8, area.bottom() - height / 5, 0.80)
                || !blueFraction(image, area.left() + 8, area.top() + height / 3,
                area.right() - 8, area.bottom() - height / 6, 0.50))
            return ImageSearchResultData.miss();

        int white = 0, total = 0;
        for (int y = area.labelTop(); y < area.labelBottom(); y++)
            for (int x = area.labelLeft(); x < area.labelRight(); x++) {
                int c = image.getRGB(x, y), r = c >>> 16 & 255, g = c >>> 8 & 255, b = c & 255;
                if (r > 205 && g > 205 && b > 205) white++;
                total++;
            }
        if (white < total * 0.05 || white > total * 0.70)
            return ImageSearchResultData.miss();

        int cx = (area.left() + area.right()) / 2;
        int cy = (area.labelTop() + area.labelBottom()) / 2;
        return ImageSearchResultData.hit(cx, cy, 90, 70, 24);
    }

    private static boolean blueFraction(BufferedImage image, int x0, int y0,
                                         int x1, int y1, double minimum) {
        int blue = 0, total = 0;
        for (int y = y0; y < y1; y += 3)
            for (int x = x0; x < x1; x += 3) {
                int c = image.getRGB(x, y);
                int r = c >>> 16 & 255, g = c >>> 8 & 255, b = c & 255;
                if (b > 205 && g > 110 && r < 175 && b - r > 75) blue++;
                total++;
            }
        return total > 0 && blue >= total * minimum;
    }
}
