package dev.frostguard.tasks.city;

import dev.frostguard.api.domain.RawImageData;

/**
 * An intentionally narrow, 720x1280-only detector for the November 2026
 * Whiteout Survival troop panel. A selected glowing tier, the immediately
 * higher *grey/locked* tier and the blue Train CTA must be visible together.
 *
 * This is a positive fallback for the currently selected top unlocked tier;
 * it does NOT select a different tier or infer a valid action from a blue
 * background alone. Callers additionally verify the training panel's tab,
 * check two captures, and verify subsequent queue state after the tap.
 *
 * Probe pixels were validated against real Infantry/Lancer and CITY frames
 * from the user's Serve-EMU Task Coach capture, 15 November 2026.
 */
final class TrainingPanelFrameEvidence {
    private TrainingPanelFrameEvidence() {}

    static boolean currentTopUnlockedTierIsTrainable(RawImageData frame) {
        if (frame == null || !frame.isValid() || frame.getWidth() != 720
                || frame.getHeight() != 1280 || frame.getBytesPerPixel() != 4) return false;
        // The centre selected card has a bright silver/cyan outline. Requiring
        // three independent rim samples prevents animated highlights from
        // making an unrelated screen look like a selected troop.
        int bright = 0;
        for (int[] p : SELECTED_TIER_RIM) {
            if (silverRim(frame, p[0], p[1])) bright++;
        }
        if (bright < 3) return false;

        // The next card to the RIGHT is dark because that tier is still
        // locked. This is what makes "currently selected" mean "highest
        // available" instead of an arbitrary previously selected low tier.
        for (int[] p : NEXT_LOCKED_TIER) {
            int r = channel(frame, p[0], p[1], 0);
            int g = channel(frame, p[0], p[1], 1);
            int b = channel(frame, p[0], p[1], 2);
            if (r + g + b >= 400) return false;
        }

        // Two patches inside the real BLUE "Train" button (not the orange
        // premium-currency "Finish" button). City and unlock backgrounds do
        // not have these two exact bright-blue patches in this geometry.
        for (int[] p : TRAIN_BUTTON_BLUE) {
            int r = channel(frame, p[0], p[1], 0);
            int g = channel(frame, p[0], p[1], 1);
            int b = channel(frame, p[0], p[1], 2);
            if (!(r <= 115 && g >= 150 && b >= 210 && g-r >= 50 && b-g >= 55))
                return false;
        }
        return true;
    }

    // Fixed points are ONLY valid for the current 2026 720x1280 layout.
    private static final int[][] SELECTED_TIER_RIM = {
            {300, 640}, {265, 650}, {255, 680}, {303, 743}
    };
    private static final int[][] NEXT_LOCKED_TIER = {
            {440, 650}, {455, 684}, {440, 720}
    };
    private static final int[][] TRAIN_BUTTON_BLUE = {
            {560, 1100}, {400, 1130}
    };

    private static boolean silverRim(RawImageData frame, int x, int y) {
        int r=channel(frame,x,y,0), g=channel(frame,x,y,1), b=channel(frame,x,y,2);
        return r >= 192 && g >= 205 && b >= 210 && b-r <= 37;
    }

    private static int channel(RawImageData frame, int x, int y, int channel) {
        return frame.getData()[(y * 720 + x) * 4 + channel] & 0xff;
    }
}
