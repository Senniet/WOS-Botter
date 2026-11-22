package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.RawImageData;

/**
 * Only the gold Buy & Use button on the recognized Obtain more screen.
 * Orange buttons on unrelated pages or a blank frame must never pass.
 */
final class CityGemReplenishButtonFrame {
    private static final int[][] GOLD_SAMPLES = {
            {510, 370}, {510, 403}, {646, 404}, {645, 370}
    };
    private CityGemReplenishButtonFrame() {}

    static boolean hasGoldenBuyAndUse(RawImageData frame) {
        if (!CityOwnedResourceButtonFrame.isObtainMore(frame)
                || frame.getBytesPerPixel() != 4) return false;
        int gold = 0;
        byte[] pixels = frame.getData();
        for (int[] xy : GOLD_SAMPLES) {
            int offset = (xy[1] * 720 + xy[0]) * 4;
            int r = pixels[offset] & 255, g = pixels[offset + 1] & 255,
                    b = pixels[offset + 2] & 255;
            if (r >= 225 && g >= 130 && g <= 235 && b < 90 && r - b >= 170)
                gold++;
        }
        return gold >= 3;
    }
}
