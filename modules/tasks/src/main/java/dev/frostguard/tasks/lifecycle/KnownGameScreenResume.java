package dev.frostguard.tasks.lifecycle;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Strict screen evidence for fast Initialize on an already-running game.
 *
 * <p>An Android Back action is allowed only when a real WOS page title and
 * its normal header Back icon are both recognized on the same 720x1280 frame.
 * Rewards, loading, login and update overlays cannot authorize a Back action.
 */
final class KnownGameScreenResume {
    enum Page {
        HERO_RECRUITMENT, HEROES, SQUAD_SETTINGS, DAILY, EXPLORATION, MAIL, VIP, ARENA, RESEARCH
    }

    record PagePattern(Page page, TemplatesEnum template, PointData topLeft, PointData bottomRight) { }

    private static final int PAGE_THRESHOLD = 91;
    private static final int BACK_THRESHOLD = 91;
    private static final PointData BACK_TOP_LEFT = new PointData(0, 65);
    private static final PointData BACK_BOTTOM_RIGHT = new PointData(110, 155);

    private static final PagePattern[] PAGES = {
        new PagePattern(Page.HERO_RECRUITMENT, TemplatesEnum.SERVE_EMU_HERO_RECRUIT_TITLE,
                new PointData(390, 130), new PointData(710, 315)),
        new PagePattern(Page.HERO_RECRUITMENT, TemplatesEnum.SERVE_EMU_HERO_MODERN_RECRUIT_TITLE,
                new PointData(390, 130), new PointData(720, 315)),
        new PagePattern(Page.HEROES, TemplatesEnum.SERVE_EMU_HEROES_PAGE_TITLE,
                new PointData(80, 70), new PointData(265, 160)),
        new PagePattern(Page.SQUAD_SETTINGS, TemplatesEnum.SERVE_EMU_SQUAD_SETTINGS_TITLE,
                new PointData(70, 70), new PointData(350, 165)),
        new PagePattern(Page.DAILY, TemplatesEnum.SERVE_EMU_DAILY_TITLE,
                new PointData(170, 105), new PointData(545, 210)),
        new PagePattern(Page.EXPLORATION, TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE,
                new PointData(50, 65), new PointData(330, 160)),
        new PagePattern(Page.MAIL, TemplatesEnum.SERVE_EMU_MAIL_TITLE,
                new PointData(75, 75), new PointData(210, 150)),
        new PagePattern(Page.VIP, TemplatesEnum.SERVE_EMU_VIP_TITLE,
                new PointData(78, 70), new PointData(195, 150)),
        new PagePattern(Page.ARENA, TemplatesEnum.SERVE_EMU_ARENA_TITLE,
                new PointData(70, 70), new PointData(470, 150)),
        new PagePattern(Page.RESEARCH, TemplatesEnum.SERVE_EMU_RESEARCH_TITLE,
                new PointData(75, 65), new PointData(380, 155))
    };

    private KnownGameScreenResume() { }

    static Page findSafeBackPage(RawImageData frame) {
        if (frame == null || !frame.isValid() || frame.getWidth() != 720 || frame.getHeight() != 1280) {
            return null;
        }
        boolean headerBack = OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_HEADER_BACK.getTemplate(), BACK_TOP_LEFT,
                BACK_BOTTOM_RIGHT, BACK_THRESHOLD).isFound();
        if (!headerBack) {
            headerBack = OpenCvPatternLocator.locatePattern(frame,
                    TemplatesEnum.SERVE_EMU_HERO_MODERN_HEADER_BACK.getTemplate(), BACK_TOP_LEFT,
                    BACK_BOTTOM_RIGHT, BACK_THRESHOLD).isFound();
        }
        if (!headerBack) return null;
        for (PagePattern candidate : PAGES) {
            if (OpenCvPatternLocator.locatePattern(frame, candidate.template().getTemplate(),
                    candidate.topLeft(), candidate.bottomRight(), PAGE_THRESHOLD).isFound()) {
                return candidate.page();
            }
        }
        return null;
    }

    /**
     * Bounded execution shared by real Initialize and deterministic fake-frame
     * tests. The caller supplies fresh post-action frames; this never invents a
     * click for an unrecognized, changed or external screen.
     */
    static ResumeOutcome returnHome(Page firstPage, int maxBackActions,
            BooleanSupplier gameForeground, Runnable pressGameBack,
            Runnable settleAfterBack, BooleanSupplier verifyHome,
            Supplier<Page> verifyNextKnownPage) {
        if (firstPage == null || maxBackActions <= 0) {
            return new ResumeOutcome(false, 0, "no confirmed in-game page");
        }
        Page current = firstPage;
        for (int actions = 0; actions < maxBackActions; actions++) {
            if (!gameForeground.getAsBoolean()) {
                return new ResumeOutcome(false, actions, "game not foreground");
            }
            pressGameBack.run();
            settleAfterBack.run();
            if (verifyHome.getAsBoolean()) {
                return new ResumeOutcome(true, actions + 1, "home/world verified");
            }
            current = verifyNextKnownPage.get();
            if (current == null) {
                return new ResumeOutcome(false, actions + 1, "next page not recognized");
            }
        }
        return new ResumeOutcome(false, maxBackActions, "bounded Back limit reached");
    }

    record ResumeOutcome(boolean homeVerified, int backActions, String reason) { }

    /** Require the exact same known page on two captures before first Back. */
    static boolean confirmsSamePage(Page first, Page second) {
        return first != null && first == second;
    }
}
