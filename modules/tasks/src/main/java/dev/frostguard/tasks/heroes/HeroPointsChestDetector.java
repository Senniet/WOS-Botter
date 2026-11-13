package dev.frostguard.tasks.heroes;

import java.awt.image.BufferedImage;

/** Detects only the red reward badge beside the Recruitment Points Chest. */
final class HeroPointsChestDetector {
    static final int LEFT = 650;
    static final int TOP = 395;
    static final int WIDTH = 65;
    static final int HEIGHT = 75;
    private static final int MIN_RED_PIXELS = 75;

    private HeroPointsChestDetector() {}

    static boolean isReady(BufferedImage screen) {
        if (screen == null || screen.getWidth() < LEFT + WIDTH
                || screen.getHeight() < TOP + HEIGHT) return false;
        int red = 0;
        for (int y = TOP; y < TOP + HEIGHT; y++) {
            for (int x = LEFT; x < LEFT + WIDTH; x++) {
                int rgb = screen.getRGB(x, y);
                int r = (rgb >> 16) & 255;
                int g = (rgb >> 8) & 255;
                int b = rgb & 255;
                if (r > 150 && r > g * 1.7 && r > b * 1.5) red++;
            }
        }
        return red >= MIN_RED_PIXELS;
    }
}
