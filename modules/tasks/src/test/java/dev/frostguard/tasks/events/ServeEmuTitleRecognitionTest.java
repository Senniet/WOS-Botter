package dev.frostguard.tasks.events;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Privacy-safe title-only reproductions of live 720x1280 serve-emu diagnostics. */
class ServeEmuTitleRecognitionTest {
    @BeforeAll static void cv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }

    @Test void dailyMissionTitleMatchesOnlyDailyScreen() throws Exception {
        assertTrue(match("daily", TemplatesEnum.SERVE_EMU_DAILY_TITLE, 170, 105, 545, 210));
        assertFalse(match("exploration", TemplatesEnum.SERVE_EMU_DAILY_TITLE, 170, 105, 545, 210));
    }

    @Test void explorationTitleMatchesOnlyExplorationScreen() throws Exception {
        assertTrue(match("exploration", TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE, 50, 65, 330, 160));
        assertFalse(match("daily", TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE, 50, 65, 330, 160));
    }

    @Test void incomeTitleMatchesOnlyIdleIncomeOverlay() throws Exception {
        assertTrue(match("income", TemplatesEnum.SERVE_EMU_IDLE_INCOME_TITLE, 235, 275, 555, 385));
        assertFalse(match("daily", TemplatesEnum.SERVE_EMU_IDLE_INCOME_TITLE, 235, 275, 555, 385));
    }

    @Test void homeAndWorldAnchorsHaveIndependentMatches() throws Exception {
        assertTrue(match("city-anchor", TemplatesEnum.SERVE_EMU_CITY_ANCHOR,
                590, 1145, 715, 1275));
        assertFalse(match("city-anchor", TemplatesEnum.SERVE_EMU_WORLD_ANCHOR,
                590, 1145, 715, 1275));
        assertTrue(match("world-anchor", TemplatesEnum.SERVE_EMU_WORLD_ANCHOR,
                590, 1145, 715, 1275));
        assertFalse(match("world-anchor", TemplatesEnum.SERVE_EMU_CITY_ANCHOR,
                590, 1145, 715, 1275));
    }

    @Test void rewardsOverlayIsNotConfusedWithExplorationPage() throws Exception {
        assertTrue(match("rewards", TemplatesEnum.SERVE_EMU_REWARDS_BANNER,
                235, 285, 495, 425));
        assertFalse(match("exploration", TemplatesEnum.SERVE_EMU_REWARDS_BANNER,
                235, 285, 495, 425));
    }

    @Test void currentMailHeaderAndClaimFooterAreScreenScoped() throws Exception {
        assertTrue(match("mailTitle", TemplatesEnum.SERVE_EMU_MAIL_TITLE, 75, 75, 210, 150));
        assertFalse(match("daily", TemplatesEnum.SERVE_EMU_MAIL_TITLE, 75, 75, 210, 150));
        assertTrue(match("mailReadClaim", TemplatesEnum.SERVE_EMU_MAIL_READ_CLAIM,
                390, 1180, 720, 1279));
        assertFalse(match("exploration", TemplatesEnum.SERVE_EMU_MAIL_READ_CLAIM,
                390, 1180, 720, 1279));
    }

    @Test void currentDailyTabOnChapterPageIsRecognizedOnlyAtFooter() throws Exception {
        assertTrue(match("dailyTabButton", TemplatesEnum.SERVE_EMU_DAILY_TAB_BUTTON,
                300, 1050, 720, 1240));
        assertFalse(match("exploration", TemplatesEnum.SERVE_EMU_DAILY_TAB_BUTTON,
                300, 1050, 720, 1240));
    }

    @Test void obtainMoreHeaderAndPremiumBuyControlAreRecognizedSafely() throws Exception {
        assertTrue(match("stamina-premium", TemplatesEnum.SERVE_EMU_STAMINA_TITLE,
                250, 130, 490, 220));
        assertTrue(match("stamina-premium", TemplatesEnum.SERVE_EMU_PREMIUM_BUY,
                470, 490, 700, 640));
        assertFalse(match("mailTitle", TemplatesEnum.SERVE_EMU_PREMIUM_BUY,
                470, 490, 700, 640));
    }

    @Test void currentHeaderBackIsRecognizedWithoutFalseRootMatch() throws Exception {
        assertTrue(match("headerBack", TemplatesEnum.SERVE_EMU_HEADER_BACK, 0, 65, 110, 155));
        assertFalse(match("daily", TemplatesEnum.SERVE_EMU_HEADER_BACK, 0, 65, 110, 155));
    }

    @Test void currentMailUnreadMarkersCanBeFoundInRightColumn() throws Exception {
        assertTrue(match("mail-markers", TemplatesEnum.MAIL_UNCLAIMED_REWARDS,
                625, 175, 715, 1160));
    }

    @Test void legacyMailFooterRemainsRecognizedForBlueStacks() throws Exception {
        assertTrue(match("mailReadClaimLegacy", TemplatesEnum.SERVE_EMU_MAIL_READ_CLAIM_LEGACY,
                390, 1180, 720, 1279));
        assertFalse(match("exploration", TemplatesEnum.SERVE_EMU_MAIL_READ_CLAIM_LEGACY,
                390, 1180, 720, 1279));
    }

    @Test void arenaStageButtonsAreScreenScoped() throws Exception {
        assertTrue(match("arenaTitle", TemplatesEnum.SERVE_EMU_ARENA_TITLE, 70, 70, 470, 150));
        assertTrue(match("arenaChallenge", TemplatesEnum.SERVE_EMU_ARENA_CHALLENGE, 190, 1150, 535, 1270));
        assertTrue(match("arenaListTitle", TemplatesEnum.SERVE_EMU_ARENA_LIST_TITLE, 230, 170, 510, 280));
        assertTrue(match("arenaDefenseTitle", TemplatesEnum.SERVE_EMU_ARENA_DEFENSE_TITLE, 65, 70, 550, 150));
        assertTrue(match("arenaQuickDeploy", TemplatesEnum.SERVE_EMU_ARENA_QUICK_DEPLOY, 50, 1140, 370, 1260));
        assertTrue(match("arenaSave", TemplatesEnum.SERVE_EMU_ARENA_SAVE, 375, 1140, 695, 1260));
        assertFalse(match("daily", TemplatesEnum.SERVE_EMU_ARENA_CHALLENGE, 190, 1150, 535, 1270));
    }

    @Test void researchTitleMatchesLiveTechTreeWithoutConfusingArena() throws Exception {
        assertTrue(match("researchTitle", TemplatesEnum.SERVE_EMU_RESEARCH_TITLE, 75, 65, 380, 155));
        assertFalse(match("arenaTitle", TemplatesEnum.SERVE_EMU_RESEARCH_TITLE, 75, 65, 380, 155));
    }

    @Test void currentGatherDeployLabelIsRecognizedOnlyInFormation() throws Exception {
        assertTrue(match("gatherDeploy", TemplatesEnum.SERVE_EMU_GATHER_DEPLOY,
                400, 1140, 705, 1270));
        assertFalse(match("arenaTitle", TemplatesEnum.SERVE_EMU_GATHER_DEPLOY,
                400, 1140, 705, 1270));
    }

    @Test void currentVipFreeClaimRequiresTheRightScreenAndChecksLockedStatus() throws Exception {
        assertTrue(match("vipHud", TemplatesEnum.SERVE_EMU_VIP_HUD, 426, 105, 544, 170));
        assertTrue(match("vipTitle", TemplatesEnum.SERVE_EMU_VIP_TITLE, 78, 70, 195, 150));
        assertTrue(match("vipLocked", TemplatesEnum.SERVE_EMU_VIP_LOCKED, 515, 760, 675, 886));
        assertTrue(match("vipCalendar", TemplatesEnum.SERVE_EMU_VIP_CALENDAR, 568, 285, 690, 403));
        assertTrue(match("vipSignIn", TemplatesEnum.SERVE_EMU_VIP_SIGNIN, 40, 265, 510, 385));
        assertFalse(match("arenaTitle", TemplatesEnum.SERVE_EMU_VIP_HUD, 426, 105, 544, 170));
        assertFalse(match("daily", TemplatesEnum.SERVE_EMU_VIP_LOCKED, 515, 760, 675, 886));
    }

    @Test void modernHeroRecruitmentRecognizesOnlyFreeActionsAndRewardExit() throws Exception {
        assertTrue(match("heroRecruitTitle", TemplatesEnum.SERVE_EMU_HERO_RECRUIT_TITLE,
                390, 130, 710, 315));
        assertTrue(match("heroEpicFree", TemplatesEnum.SERVE_EMU_HERO_FREE_BUTTON,
                40, 1150, 350, 1262));
        assertTrue(match("heroRewardExit", TemplatesEnum.SERVE_EMU_HERO_REWARD_EXIT,
                190, 1140, 530, 1240));
        assertFalse(match("heroRecruitTitle", TemplatesEnum.SERVE_EMU_HERO_FREE_BUTTON,
                40, 1150, 350, 1262));
        assertFalse(match("arenaTitle", TemplatesEnum.SERVE_EMU_HERO_REWARD_EXIT,
                190, 1140, 530, 1240));
    }

    @Test void currentIntelJourneyExploreButtonMatchesHistoricalFailureFrame() throws Exception {
        assertTrue(match("intelJourneyExplore", TemplatesEnum.SERVE_EMU_INTEL_JOURNEY_EXPLORE,
                220, 565, 510, 690));
        assertFalse(match("daily", TemplatesEnum.SERVE_EMU_INTEL_JOURNEY_EXPLORE,
                220, 565, 510, 690));
    }

    @Test void currentIntelRescueButtonMatchesHistoricalFailureFrame() throws Exception {
        assertTrue(match("intelSurvivorRescue", TemplatesEnum.SERVE_EMU_INTEL_SURVIVOR_RESCUE,
                220, 565, 510, 690));
        assertFalse(match("daily", TemplatesEnum.SERVE_EMU_INTEL_SURVIVOR_RESCUE,
                220, 565, 510, 690));
    }

    @Test void heroRecruitmentRewardsExitIsVerifiedNotPaidRecruitButton() throws Exception {
        assertTrue(match("heroRewards", TemplatesEnum.SERVE_EMU_HERO_REWARDS_TITLE, 230, 215, 500, 355));
        assertTrue(match("heroRewards", TemplatesEnum.SERVE_EMU_HERO_REWARDS_EXIT, 190, 1150, 525, 1240));
        assertFalse(match("daily", TemplatesEnum.SERVE_EMU_HERO_REWARDS_EXIT, 190, 1150, 525, 1240));
    }

    private static boolean match(String scene, TemplatesEnum template, int left, int top, int right, int bottom)
            throws Exception {
        BufferedImage image = ImageIO.read(Objects.requireNonNull(
                ServeEmuTitleRecognitionTest.class.getResource("/serveemu/" + scene + "-title-only.png")));
        byte[] pixels = new byte[image.getWidth() * image.getHeight() * 4];
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int rgb = image.getRGB(x, y), i = (y * image.getWidth() + x) * 4;
            pixels[i] = (byte) (rgb >> 16);
            pixels[i + 1] = (byte) (rgb >> 8);
            pixels[i + 2] = (byte) rgb;
            pixels[i + 3] = (byte) 255;
        }
        return OpenCvPatternLocator.locatePattern(RawImageData.capture(pixels, image.getWidth(),
                image.getHeight(), 32), template.getTemplate(), new PointData(left, top),
                new PointData(right, bottom), 90).isFound();
    }
}
