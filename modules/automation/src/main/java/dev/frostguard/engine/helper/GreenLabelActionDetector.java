package dev.frostguard.engine.helper;

import java.awt.image.BufferedImage;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.vision.color.GameColors;

/** Conservative fallback for a *known* green, white-labelled button region.
 * Caller must first establish the owning screen and confirm on a second frame.
 */
public final class GreenLabelActionDetector {
    private GreenLabelActionDetector() {}

    public record Region(int left, int top, int right, int bottom) {
        public Region {
            if (left < 0 || top < 0 || right <= left || bottom <= top)
                throw new IllegalArgumentException("Invalid button bounds");
        }
    }

    public static ImageSearchResultData detect(BufferedImage frame, Region area) {
        if (frame == null || area == null || frame.getWidth() != 720 || frame.getHeight() != 1280
                || area.right() > frame.getWidth() || area.bottom() > frame.getHeight())
            return ImageSearchResultData.miss();
        int green = 0, white = 0, total = 0;
        // A green body with a light label must be visible inside the anchored area.
        for (int y = area.top() + 5; y < area.bottom() - 5; y += 3) {
            for (int x = area.left() + 5; x < area.right() - 5; x += 3) {
                int rgb = frame.getRGB(x, y);
                if (GameColors.isVividGreen(rgb)) green++;
                if ((rgb >> 16 & 255) > 205 && (rgb >> 8 & 255) > 205 && (rgb & 255) > 205) white++;
                total++;
            }
        }
        if (total == 0 || green < total * 0.25 || white < total * 0.015 || white > total * 0.55)
            return ImageSearchResultData.miss();
        // Require a green button fill in the left and right thirds. This
        // excludes green status text, badges, and tiny accidental highlights.
        if (!hasGreenFill(frame, area.left() + 9, area.left() + (area.right() - area.left()) / 3,
                area.top() + 12, area.bottom() - 12)
            || !hasGreenFill(frame, area.right() - (area.right() - area.left()) / 3,
                area.right() - 9, area.top() + 12, area.bottom() - 12))
            return ImageSearchResultData.miss();
        return ImageSearchResultData.hit((area.left() + area.right()) / 2,
                (area.top() + area.bottom()) / 2, 92,
                area.right() - area.left(), area.bottom() - area.top());
    }

    private static boolean hasGreenFill(BufferedImage image, int left, int right, int top, int bottom) {
        int green = 0, total = 0;
        for (int y = top; y < bottom; y += 3)
            for (int x = left; x < right; x += 3) {
                if (GameColors.isVividGreen(image.getRGB(x, y))) green++;
                total++;
            }
        return total > 0 && green >= total * .35;
    }
}
