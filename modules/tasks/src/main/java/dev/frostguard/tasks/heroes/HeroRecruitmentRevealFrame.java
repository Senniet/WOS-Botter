package dev.frostguard.tasks.heroes;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * Identifies the full-screen duplicate hero reveal shown after an earned
 * Epic/Advanced free claim. This is NOT the usual Rewards overlay: the game
 * requires one tap to advance from the hero card to Rewards, then a second
 * confirmed safe tap to exit Rewards. The target is a static part of the
 * "Hero already owned" footer (the actual hero name is intentionally excluded).
 */
final class HeroRecruitmentRevealFrame {
    private static final PointData FOOTER_START = new PointData(42, 1197);
    private static final PointData FOOTER_END = new PointData(420, 1279);

    private HeroRecruitmentRevealFrame() {}

    static boolean alreadyOwned(RawImageData frame) {
        if (frame == null || !frame.isValid()
                || frame.getWidth() != 720 || frame.getHeight() != 1280) return false;
        return OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_HERO_ALREADY_OWNED_REVEAL_NOV_2026.getTemplate(),
                FOOTER_START, FOOTER_END, 88).isFound();
    }
}
