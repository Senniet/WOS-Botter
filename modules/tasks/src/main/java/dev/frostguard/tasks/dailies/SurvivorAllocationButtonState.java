package dev.frostguard.tasks.dailies;

import java.awt.image.BufferedImage;

/**
 * Distinguishes the enabled blue Allocate All action from its disabled grey
 * state inside the 2026 Survivor Status overlay. The printed label is identical
 * in both states, so a template match alone cannot prove an enabled action.
 *
 * The caller must separately confirm the Survivor Status title and the control
 * identity before using this color-only status classifier.
 */
final class SurvivorAllocationButtonState {

    enum State {
        ENABLED,
        DISABLED,
        UNKNOWN
    }

    private static final int EXPECTED_WIDTH = 720;
    private static final int EXPECTED_HEIGHT = 1280;

    // Background patches either side of the text on the 2026 720x1280 panel.
    // Avoid the white label, button border and rounded corners.
    private static final int[][] SAMPLE_PATCHES = {
            {230, 1091, 260, 1126},
            {455, 1091, 485, 1126}
    };

    private SurvivorAllocationButtonState() {
    }

    static boolean statusTabSelected(BufferedImage frame) {
        if (frame == null || frame.getWidth() != EXPECTED_WIDTH || frame.getHeight() != EXPECTED_HEIGHT) {
            return false;
        }
        // The Status tab is light blue; Details is dark blue on both 2026
        // real frames. The reverse indicates Details was selected instead.
        int left = frame.getRGB(95, 1230);
        int right = frame.getRGB(525, 1230);
        int lr = left >>> 16 & 255;
        int lg = left >>> 8 & 255;
        int rr = right >>> 16 & 255;
        int rg = right >>> 8 & 255;
        return lr >= 175 && lg >= 190 && rr <= 120 && rg <= 150;
    }

    static State classify(BufferedImage frame) {
        if (frame == null || frame.getWidth() != EXPECTED_WIDTH || frame.getHeight() != EXPECTED_HEIGHT) {
            return State.UNKNOWN;
        }
        int blue = 0;
        int grey = 0;
        int total = 0;
        for (int[] rect : SAMPLE_PATCHES) {
            for (int y = rect[1]; y < rect[3]; y += 2) {
                for (int x = rect[0]; x < rect[2]; x += 2) {
                    int pixel = frame.getRGB(x, y);
                    int r = pixel >>> 16 & 255;
                    int g = pixel >>> 8 & 255;
                    int b = pixel & 255;
                    if (b >= 190 && g >= 115 && r <= 165 && b - r >= 65) {
                        blue++;
                    }
                    if (r >= 75 && r <= 160 && Math.abs(r - g) <= 20
                            && Math.abs(g - b) <= 25 && Math.abs(b - r) <= 40) {
                        grey++;
                    }
                    total++;
                }
            }
        }
        // Require both patches to have an overwhelmingly consistent state.
        if (blue >= total * 0.70 && grey <= total * 0.10) {
            return State.ENABLED;
        }
        if (grey >= total * 0.70 && blue <= total * 0.10) {
            return State.DISABLED;
        }
        return State.UNKNOWN;
    }
}
