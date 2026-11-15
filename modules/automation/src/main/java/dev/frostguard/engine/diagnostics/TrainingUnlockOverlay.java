package dev.frostguard.engine.diagnostics;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * Detects the full-screen, blue troop-unlock animation with the explicit
 * "Tap anywhere to continue" instruction seen after Infantry training.
 * Neither a blue screen nor a text match alone authorises a tap.
 */
public final class TrainingUnlockOverlay {
    private TrainingUnlockOverlay() {}

    public static boolean visible(RawImageData frame) {
        if (frame == null || !frame.isValid() || frame.getWidth() != 720
                || frame.getHeight() != 1280) return false;
        if (!isBlueUnlockBackdrop(frame)) return false;
        ImageSearchResultData prompt = OpenCvPatternLocator.locatePattern(
                frame,
                TemplatesEnum.TRAINING_TROOP_UNLOCK_CONTINUE_2026.getTemplate(),
                new PointData(120, 1185), new PointData(610, 1270), 92.0);
        return prompt != null && prompt.isFound() && prompt.getPoint() != null
                && prompt.getPoint().getY() >= 1210 && prompt.getPoint().getY() <= 1255;
    }

    private static boolean isBlueUnlockBackdrop(RawImageData frame) {
        // Sample five unoccupied border spots without converting the entire
        // 720x1280 framebuffer; the native ADB capture is RGBA8888.
        if (frame.getBpp() != 32 && frame.getBpp() != 4) return false;
        byte[] raw = frame.getData();
        int[][] samples = {{35, 245}, {680, 245}, {30, 935}, {675, 935}, {350, 100}};
        int valid = 0;
        for (int[] point : samples) {
            int i = (point[1] * 720 + point[0]) * 4;
            int r = raw[i] & 255, g = raw[i + 1] & 255, b = raw[i + 2] & 255;
            if (r >= 60 && r <= 140 && g >= 110 && g <= 190
                    && b >= 145 && b <= 230 && g - r >= 22 && b - g >= 17) valid++;
        }
        return valid >= 4;
    }
}
