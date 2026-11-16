package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.SizeData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig;
import dev.frostguard.engine.helper.BlueLabelActionDetector;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.tasks.diagnostics.TaskDiagnosticSnapshots;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.engine.service.StatisticsService;

import java.time.LocalDateTime;
import java.awt.image.BufferedImage;
import java.io.IOException;
import dev.frostguard.vision.ocr.OcrException;
import java.util.Locale;

public class DoExplorationRoutine extends DelayedTask {

    private static final BlueLabelActionDetector.Region EXPLORE_BLUE_BUTTON =
            new BlueLabelActionDetector.Region(214, 1139, 507, 1219, 292, 1156, 435, 1196);

    private static final PointData EXPLORATION_ENTRY_TOP_LEFT = new PointData(40, 1190);
    private static final PointData EXPLORATION_ENTRY_BOTTOM_RIGHT = new PointData(100, 1250);
    private static final PointData QUICK_DEPLOY_TOP_LEFT = new PointData(55, 1170);
    private static final PointData QUICK_DEPLOY_BOTTOM_RIGHT = new PointData(330, 1220);
    private static final PointData FIGHT_BUTTON_TOP_LEFT = new PointData(390, 1170);
    private static final PointData FIGHT_BUTTON_BOTTOM_RIGHT = new PointData(670, 1220);
    // Verified live Victory screen: "Tap anywhere to exit" at the footer.
    // Chest claims are separate from the Quick Exploration completion receipt.
    private static final GreenLabelActionDetector.Region OUTER_CHEST_CLAIM =
            new GreenLabelActionDetector.Region(535,830,701,909);
    private static final GreenLabelActionDetector.Region INNER_IDLE_CLAIM =
            new GreenLabelActionDetector.Region(213,875,507,972);
    private static final PointData VICTORY_CONTINUE_TOP_LEFT = new PointData(290, 1140);
    private static final PointData VICTORY_CONTINUE_BOTTOM_RIGHT = new PointData(430, 1205);

    private boolean battleOutcomeUnknown;

    private static final long MAX_FIGHTING_WINDOW_MS = 120_000L;
    private static final long RESULT_SEARCH_START_DELAY_MS = 1_500L;
    private static final long RESULT_DETECTION_WINDOW_MS = 25_000L;
    private static final long RESULT_DETECTION_DELAY_MS = 1_000L;
    private static final long EXPLORATION_SCREEN_LOAD_DELAY_MS = 500L;
    private static final long EXPLORE_BUTTON_TAP_DELAY_MS = 300L;
    private static final long QUICK_DEPLOY_DELAY_MS = 300L;
    private static final long VICTORY_CONTINUE_DELAY_MS = 200L;

    public DoExplorationRoutine(AccountDescriptor profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected LaunchPoint getRequiredStartLocation() {
        return LaunchPoint.ANY;
    }

    protected void execute() {
        battleOutcomeUnknown = false;
        // A previous run may have ended after Confirm while Quick Exploration
        // was still animating or already showing its completed Rewards. That
        // is not a new normal battle and must be resolved before any navigation.
        QuickExplorationFrame.State existing=QuickExplorationFrame.classify(
                emuManager.captureScreen(EMULATOR_NUMBER),EMULATOR_NUMBER);
        if (existing!=QuickExplorationFrame.State.OTHER) {
            logInfo("Resuming pending Quick Exploration result, state="+existing);
            completeQuickRouteAndSchedule();
            return;
        }
        // Resume a confirmation left by a previous run instead of navigating
        // blindly through a pending modal or starting another battle.
        QuickDialogOutcome pending = inspectQuickExplorationConfirmation();
        if (pending != QuickDialogOutcome.NOT_PRESENT) {
            if (pending == QuickDialogOutcome.CONFIRMED) {
                logInfo("Resumed Quick Exploration started; verifying the result and Rewards");
                completeQuickRouteAndSchedule();
            } else {
                markWorkIncomplete("Exploration: pending Quick Exploration confirmation not verified");
                logWarning("Pending Quick Exploration modal is unresolved; "
                        + TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER,
                                "exploration", "quick-confirm-unresolved"));
                reschedule(LocalDateTime.now().plusMinutes(10));
            }
            return;
        }
        logInfo("Opening exploration screen...");
        if (!explorationTitleVisible()) navigationHelper.ensureCorrectScreenLocation(LaunchPoint.WORLD);
        if (!openExplorationScreen()) {
            markWorkIncomplete("Exploration: page not opened or title unverified");
            logWarning("Exploration page could not be verified; " + TaskDiagnosticSnapshots.capture(
                    emuManager, EMULATOR_NUMBER, "exploration", "entry-unverified"));
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }

        logInfo("Searching exploration button...");
        ImageSearchResultData exploreButton = detectExploreButton();
        if (exploreButton == null || !exploreButton.isFound()) {
            markWorkIncomplete("Exploration: Explore button not verified");
            logWarning("Exploration button not found");
            rescheduleInOneHour();
            return;
        }

        logInfo("Exploring...");
        tapExploreButton(exploreButton);
        // Live serve-emu: the strength confirmation appears immediately after
        // Explore, before the formation screen. Do not tap Quick Deploy or Fight
        // through it; the confirmed shortcut executes the battle automatically.
        QuickDialogOutcome afterExplore = awaitQuickConfirmationAfterExplore();
        if (afterExplore == QuickDialogOutcome.CONFIRMED) {
            logInfo("Quick Exploration started; waiting for completion and Rewards claim");
            completeQuickRouteAndSchedule();
            return;
        }
        if (afterExplore == QuickDialogOutcome.UNRESOLVED) {
            markWorkIncomplete("Exploration: Quick confirmation unverified, Fight not started");
            logWarning("Explore opened an unverified confirmation; no battle input sent; "
                    + TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER,
                            "exploration", "quick-confirm-unresolved"));
            reschedule(LocalDateTime.now().plusMinutes(10));
            return;
        }

        boolean fightingWindowReached = fightUntilStoppedOrExpired();
        if (fightingWindowReached) {
            logWarning("Exploration fighting window reached 2 minutes. Rescheduling...");
        }

        if (battleOutcomeUnknown) {
            markWorkIncomplete("Exploration: Fight or game result not confirmed; must retry");
            logWarning("Exploration outcome remains unknown. Retrying in 10 minutes, not recording completion.");
            reschedule(LocalDateTime.now().plusMinutes(10));
        } else {
            rescheduleInOneHour();
        }
    }

    private void completeQuickRouteAndSchedule() {
        if (completeQuickRoute()) {
            rescheduleInOneHour();
        } else {
            markWorkIncomplete("Quick Exploration result, rewards or optional chest not verified");
            logWarning("Quick Exploration receipt/claim unresolved; "
                    + TaskDiagnosticSnapshots.capture(emuManager,EMULATOR_NUMBER,
                            "exploration","quick-rewards-unverified"));
            reschedule(LocalDateTime.now().plusMinutes(10));
        }
    }

    private boolean completeQuickRoute() {
        QuickExplorationFlow.Outcome receipt=QuickExplorationFlow.finish(
                ()->{checkPreemption();return emuManager.captureScreen(EMULATOR_NUMBER);},
                point->{checkPreemption();tapInside(new PointData(point.getX()-9,point.getY()-9),
                        new PointData(point.getX()+9,point.getY()+9));},
                ()->sleepTask(650),EMULATOR_NUMBER);
        if (receipt!=QuickExplorationFlow.Outcome.REWARDS_CLOSED) {
            logWarning("Quick Exploration completed Rewards not confirmed/closed; "
                    + TaskDiagnosticSnapshots.capture(emuManager,EMULATOR_NUMBER,
                            "exploration","quick-reward-sheet-pending"));
            return false;
        }
        logInfo("Quick Exploration completed and its Total Rewards receipt closed; checking optional chest");
        if (!claimQuickRouteChestIfAvailable())return false;
        return returnHomeAfterQuickRoute();
    }

    private boolean returnHomeAfterQuickRoute() {
        var first=emuManager.captureScreen(EMULATOR_NUMBER);
        if(ExplorationPageFrame.title(first)) {
            var arrow=ExplorationPageFrame.verifiedBackCandidate(first);
            if(!arrow.isFound())return false;
            sleepTask(190);
            var second=ExplorationPageFrame.verifiedBackCandidate(
                    emuManager.captureScreen(EMULATOR_NUMBER));
            if(!RecognitionConsensus.agrees(arrow,second,15))return false;
            tapVerifiedSquadAction(second);
            sleepTask(550);
            if(ExplorationPageFrame.title(emuManager.captureScreen(EMULATOR_NUMBER)))return false;
        }
        try {
            navigationHelper.ensureCorrectScreenLocation(LaunchPoint.HOME,this::checkPreemption);
            logInfo("Quick Exploration, optional income and City Home verified");
            return true;
        } catch(dev.frostguard.engine.error.HomeNotFoundException missing) {
            logWarning("Quick Exploration rewards received but Home not verified: "+missing.getMessage());
            return false;
        }
    }

    /**
     * After a completed Quick run, Exploration may also expose the ordinary
     * green chest Claim. That is a different reward from Total Rewards.
     * Never tap a random green control: require Exploration title, stable
     * outer/inner Claim buttons and a distinct Rewards screen in succession.
     */
    private boolean claimQuickRouteChestIfAvailable() {
        var initial=emuManager.captureScreen(EMULATOR_NUMBER);
        if (!ExplorationPageFrame.title(initial)) return false;
        var outer=GreenLabelActionDetector.detect(ImageConverter.toBufferedImage(initial),OUTER_CHEST_CLAIM);
        if (!outer.isFound()) {
            logInfo("No additional green Exploration Chest claim visible after Quick Rewards");
            return true;
        }
        sleepTask(200);
        var secondFrame=emuManager.captureScreen(EMULATOR_NUMBER);
        var second=ExplorationPageFrame.title(secondFrame)
                ? GreenLabelActionDetector.detect(ImageConverter.toBufferedImage(secondFrame),OUTER_CHEST_CLAIM)
                : ImageSearchResultData.miss();
        if (!RecognitionConsensus.agrees(outer,second,10))return false;
        tapVerifiedSquadAction(second);
        sleepTask(600);

        if (!quickIdleIncomeVisible()) {
            logWarning("Outer chest Claim did not open verified Idle Income");
            return false;
        }
        var firstInner=GreenLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)),INNER_IDLE_CLAIM);
        if (!firstInner.isFound())return false;
        sleepTask(180);
        var secondInner=GreenLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)),INNER_IDLE_CLAIM);
        if (!RecognitionConsensus.agrees(firstInner,secondInner,10) || !quickIdleIncomeVisible())return false;
        tapVerifiedSquadAction(secondInner);
        sleepTask(680);
        var reward=emuManager.captureScreen(EMULATOR_NUMBER);
        if(!ExplorationIncomeRewardsFrame.visible(reward)) {
            logWarning("Idle Income Claim tapped but current Rewards sheet not recognized");
            return false;
        }
        sleepTask(210);
        if(!ExplorationIncomeRewardsFrame.visible(emuManager.captureScreen(EMULATOR_NUMBER)))return false;
        // The verified screen itself says "Tap anywhere to exit". This is
        // a blank lower area, not a purchase or another Claim button.
        tapInside(new PointData(345,1145),new PointData(365,1165));
        sleepTask(700);
        var after=emuManager.captureScreen(EMULATOR_NUMBER);
        if(ExplorationIncomeRewardsFrame.visible(after))return false;
        boolean confirmed=ExplorationPageFrame.title(after);
        if(confirmed)logInfo("Quick Exploration extra chest/Idle Income Rewards claimed and exit verified");
        return confirmed;
    }

    private boolean quickIdleIncomeVisible() {
        return templateSearchHelper.locatePattern(
                TemplatesEnum.SERVE_EMU_IDLE_INCOME_TITLE_NOV_2026,
                SearchConfig.builder().withMaxAttempts(1).withThreshold(90)
                        .withArea(AreaData.of(210,233,520,330)).build()).isFound();
    }

    private boolean openExplorationScreen() {
        if (explorationTitleVisible()) return true;
        logInfo("Tapping exploration entry area...");
        tapInside(EXPLORATION_ENTRY_TOP_LEFT, EXPLORATION_ENTRY_BOTTOM_RIGHT);
        sleepTask(EXPLORATION_SCREEN_LOAD_DELAY_MS);
        return explorationTitleVisible();
    }

    private boolean explorationTitleVisible() {
        // Current 720x1280 page: NOV_2026 matched 100% in live Task Coach.
        // Earlier SERVE_EMU_EXPLORATION_TITLE and original
        // EXPLORATION_SCREEN_TITLE scored 37-50% on that exact page.
        // They are retained as archived assets but no longer scanned in a
        // fallback cascade. If the current title fails, do not infer a page.
        return templateSearchHelper.locatePattern(
                TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE_NOV_2026,
                SearchConfig.builder().withMaxAttempts(1).withThreshold(92)
                        .withArea(AreaData.of(65, 0, 335, 115)).build()).isFound();
    }

    private ImageSearchResultData detectExploreButton() {
        ImageSearchResultData existing = templateSearchHelper.locatePattern(
                TemplatesEnum.EXPLORATION_BUTTON,
                SearchConfig.builder().withMaxAttempts(1).withThreshold(86)
                        .withArea(AreaData.of(160, 1100, 540, 1230)).build());
        if (existing.isFound()) {
            sleepTask(150);
            var second = templateSearchHelper.locatePattern(TemplatesEnum.EXPLORATION_BUTTON,
                    SearchConfig.builder().withMaxAttempts(1).withThreshold(86)
                            .withArea(AreaData.of(160, 1100, 540, 1230)).build());
            if (RecognitionConsensus.agrees(existing, second, 10)) return second;
        }
        // Current 2026 UI uses a different rendering from explore.png.
        // The screenshot-grounded fallback requires the right blue button and
        // its light label on two independent normalized frames.
        var first = BlueLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)), EXPLORE_BLUE_BUTTON);
        if (!first.isFound()) {
            logWarning("Exploration button not matched; " + TaskDiagnosticSnapshots.capture(
                    emuManager, EMULATOR_NUMBER, "exploration", "explore-button-missing"));
            return ImageSearchResultData.miss();
        }
        sleepTask(250);
        var second = BlueLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)), EXPLORE_BLUE_BUTTON);
        if (RecognitionConsensus.agrees(first, second, 8)) {
            logInfo("Explore action verified by two-frame blue-button geometry");
            return second;
        }
        logWarning("Exploration blue action not confirmed in two frames; "
                + TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER, "exploration", "explore-unconfirmed"));
        return ImageSearchResultData.miss();
    }

    private void tapExploreButton(ImageSearchResultData exploreButton) {
        logInfo("Exploration button found at " + exploreButton.getPoint()
                + " with size " + exploreButton.getTemplateSize() + ". Tapping detected area...");
        tapSearchResultArea(exploreButton);
        sleepTask(EXPLORE_BUTTON_TAP_DELAY_MS);
    }

    private boolean fightUntilStoppedOrExpired() {
        long fightingStartedAt = System.nanoTime();
        int battleNumber = 1;

        while (!isFightingWindowExpired(fightingStartedAt)) {
            logInfo("Starting exploration battle #" + battleNumber + "...");
            QuickDialogOutcome alreadyPending = inspectQuickExplorationConfirmation();
            if (alreadyPending == QuickDialogOutcome.UNRESOLVED) {
                battleOutcomeUnknown = true;
                logWarning("Exploration confirmation was already present but unverified; "
                        + TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER,
                                "exploration", "quick-confirm-unresolved"));
                return false;
            }
            if (alreadyPending == QuickDialogOutcome.NOT_PRESENT) {
                if (!startBattle()) {
                    battleOutcomeUnknown = true;
                    return false;
                }
            } else logInfo("Previous Quick Exploration confirmation handled; not tapping Fight again");

            logInfo("Waiting " + RESULT_SEARCH_START_DELAY_MS
                    + "ms before checking the result and any late confirmation...");
            if (!sleepWithinFightingWindow(RESULT_SEARCH_START_DELAY_MS, fightingStartedAt)) {
                break;
            }

            logInfo("Waiting for battle result...");
            BattleResult battleResult = waitForBattleResult(fightingStartedAt);
            if (battleResult == BattleResult.VICTORY) {
                if (!handleVictory()) return false;
                battleNumber++;
            } else if (battleResult == BattleResult.DEFEAT) {
                handleDefeat();
                return false;
            } else if (battleResult == BattleResult.QUICK_CONFIRMED) {
                logInfo("Late Quick Exploration confirmed; handling result instead of manual battle");
                if (!completeQuickRoute()) battleOutcomeUnknown=true;
                return false;
            } else {
                battleOutcomeUnknown = true;
                logWarning(battleResult == BattleResult.CONFIRMATION_UNRESOLVED
                        ? "Quick Exploration confirmation remained unverified; no further battle taps."
                        : "Battle locked: Neither victory nor defeat appeared after battle animation.");
                return false;
            }
        }

        return true;
    }

    boolean startBattle() {
        boolean useQuickDeploy = Boolean.TRUE.equals(profile.getConfig(
                ConfigurationKeyEnum.DO_EXPLORATION_QUICK_DEPLOY_BOOL, Boolean.class));
        // Both actions must belong to Squad Settings. The earlier fixed taps
        // could hit World, Victory, or a loading frame instead of starting combat.
        ImageSearchResultData fight = findVerifiedSquadAction(TemplatesEnum.INTEL_JOURNEY_FIGHT_LIVE);
        if (!fight.isFound()) {
            logWarning("Exploration Fight not confirmed on Squad Settings; "
                    + TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER,
                            "exploration", "squad-fight-missing"));
            return false;
        }
        if (useQuickDeploy) {
            ImageSearchResultData quick = findVerifiedSquadAction(TemplatesEnum.EXPLORATION_QUICK_DEPLOY_LIVE);
            if (!quick.isFound()) {
                logWarning("Quick Deploy is enabled but no matching button was confirmed; "
                        + TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER,
                                "exploration", "quick-deploy-missing"));
                return false;
            }
            logInfo("Verified Quick Deploy; selecting a fresh formation");
            tapVerifiedSquadAction(quick);
            sleepTask(QUICK_DEPLOY_DELAY_MS);
            fight = findVerifiedSquadAction(TemplatesEnum.INTEL_JOURNEY_FIGHT_LIVE);
            if (!fight.isFound()) {
                logWarning("Fight button no longer visible after Quick Deploy; refusing blind fight tap");
                return false;
            }
        } else {
            logInfo("Quick Deploy disabled; retaining saved formation");
        }
        logInfo("Verified Fight on Squad Settings; tapping once");
        tapVerifiedSquadAction(fight);
        return true;
    }

    /** Tap the center of the positively located button, not a hardcoded screen box. */
    private void tapVerifiedSquadAction(ImageSearchResultData match) {
        PointData center = match.getPoint();
        tapInside(new PointData(center.getX() - 26, center.getY() - 18),
                new PointData(center.getX() + 26, center.getY() + 18));
    }

    ImageSearchResultData findVerifiedSquadAction(TemplatesEnum action) {
        AreaData searchArea = action == TemplatesEnum.EXPLORATION_QUICK_DEPLOY_LIVE
                ? AreaData.of(30, 1145, 360, 1275) : AreaData.of(365, 1145, 710, 1275);
        return templateSearchHelper.locateAction(action,
                SearchConfig.builder().withMaxAttempts(1).withThreshold(90)
                        .withArea(searchArea).build());
    }

    /** A short bounded scan handles a modal that animates in after the Explore tap. */
    QuickDialogOutcome awaitQuickConfirmationAfterExplore() {
        for (int attempt = 0; attempt < 4; attempt++) {
            QuickDialogOutcome outcome = inspectQuickExplorationConfirmation();
            if (outcome != QuickDialogOutcome.NOT_PRESENT) return outcome;
            if (attempt < 3) sleepTask(350);
        }
        return QuickDialogOutcome.NOT_PRESENT;
    }

    /**
     * The confirmation usually follows Explore but can animate in late. Inspect
     * after Explore and during result polling rather than sending blind Fight taps.
     * Never confirm any other orange/blue modal without matching its text.
     */
    QuickDialogOutcome inspectQuickExplorationConfirmation() {
        RawImageData captured = emuManager.captureScreen(EMULATOR_NUMBER);
        // Latest Task Coach: the Squad Settings heroes and badges form an
        // orange/blue false-positive for the quick-modal geometry (y~=1025).
        // Two precise known templates confirm this is NOT a confirmation:
        // the Squad Settings header and the independently visible Fight CTA.
        // The actual Fight action still goes through its two-frame gate.
        if (ExplorationSquadFrame.isSquadReady(captured)) {
            logDebug("Squad Settings and Fight CTA confirmed; skipping false Quick modal OCR");
            return QuickDialogOutcome.NOT_PRESENT;
        }
        BufferedImage first = ImageConverter.toBufferedImage(captured);
        PointData target = quickConfirmationButton(first);
        if (target == null) return QuickDialogOutcome.NOT_PRESENT;
        sleepTask(180);
        BufferedImage second = ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER));
        PointData confirmed = quickConfirmationButton(second);
        if (confirmed == null || Math.abs(target.getY() - confirmed.getY()) > 24) {
            logWarning("Quick confirmation colors appeared, but modal geometry was unstable");
            return QuickDialogOutcome.UNRESOLVED;
        }
        String wording;
        try {
            AreaData textArea = quickConfirmationTextArea(second, confirmed);
            wording = emuManager.readText(EMULATOR_NUMBER,
                    textArea.topLeft(), textArea.bottomRight(), OcrSettingsData.forTextBlock());
        } catch (IOException | OcrException error) {
            logWarning("Quick confirmation OCR could not be verified: " + error.getClass().getSimpleName());
            return QuickDialogOutcome.UNRESOLVED;
        }
        if (!isQuickExplorationDialog(wording)) {
            logWarning("Refusing to confirm visually similar modal; Quick Exploration text not verified (OCR length="
                    + (wording == null ? 0 : wording.length()) + ")");
            return QuickDialogOutcome.UNRESOLVED;
        }
        logInfo("Quick Exploration text and Cancel/Confirm buttons verified; tapping Confirm once");
        tapInside(new PointData(confirmed.getX() - 10, confirmed.getY() - 10),
                new PointData(confirmed.getX() + 10, confirmed.getY() + 10));
        sleepTask(700);
        BufferedImage after = ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER));
        if (quickConfirmationButton(after) != null) {
            logWarning("Quick Exploration confirmation remained after one Confirm tap");
            return QuickDialogOutcome.UNRESOLVED;
        }
        logInfo("Quick Exploration confirmation dismissed; no additional manual Fight taps required");
        return QuickDialogOutcome.CONFIRMED;
    }

    static AreaData quickConfirmationTextArea(BufferedImage frame, PointData button) {
        int width = frame.getWidth();
        int top = Math.max(0, button.getY() - frame.getHeight() * 25 / 100);
        int bottom = Math.max(top + 1, button.getY() - frame.getHeight() * 7 / 100);
        return AreaData.of(width * 10 / 100, top, width * 90 / 100, bottom);
    }

    enum QuickDialogOutcome { NOT_PRESENT, CONFIRMED, UNRESOLVED }

    static boolean isQuickExplorationDialog(String text) {
        if (text == null) return false;
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return normalized.contains("quickexploration")
                && (normalized.contains("squadpower") || normalized.contains("exceedstheenemy"));
    }

    static PointData quickConfirmationButton(BufferedImage frame) {
        if (frame == null) return null;
        int w = frame.getWidth(), h = frame.getHeight();
        if (w < 300 || h < 300) return null;
        int bestScore = 0, bestY = -1;
        java.util.List<Integer> pairedRows = new java.util.ArrayList<>();
        // A dialog has orange Cancel on the left and blue Confirm on the right,
        // on the same horizontal band. This avoids tapping unrelated blue controls.
        for (int y = h * 25 / 100; y < h * 83 / 100; y += 3) {
            int orange = 0, blue = 0;
            for (int x = w * 8 / 100; x < w * 44 / 100; x += 3) {
                int rgb = frame.getRGB(x, y);
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                if (r > 205 && g > 65 && g < 190 && b < 80) orange++;
            }
            for (int x = w * 57 / 100; x < w * 92 / 100; x += 3) {
                int rgb = frame.getRGB(x, y);
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                if (r < 125 && g > 105 && g < 220 && b > 180 && b > r + 70) blue++;
            }
            if (orange >= w / 55 && blue >= w / 55) {
                pairedRows.add(y);
                if (orange + blue > bestScore) {
                    bestScore = orange + blue;
                    bestY = y;
                }
            }
        }
        if (bestY < 0) return null;
        // The real 2026 quick-confirm modal buttons are around y=820/1280.
        // Squad Settings hero cards incorrectly create paired orange/blue
        // stripes around y=1025. The confirm button can never be that low on
        // the known modal; reject that geometry before costly OCR or a tap.
        if (bestY < h * 55 / 100 || bestY > h * 74 / 100) return null;
        // Prefer the visual centre, not the highest-scoring line near a rounded
        // border. The verified live button is centred around (512, 822) on 720x1280.
        int minY = bestY, maxY = bestY;
        for (int row : pairedRows) {
            if (Math.abs(row - bestY) <= h * 8 / 100) {
                minY = Math.min(minY, row);
                maxY = Math.max(maxY, row);
            }
        }
        return new PointData(w * 71 / 100, (minY + maxY) / 2);
    }

    private boolean handleVictory() {
        logInfo("Exploration Victory detected; exiting through verified footer");
        tapInside(VICTORY_CONTINUE_TOP_LEFT, VICTORY_CONTINUE_BOTTOM_RIGHT);
        sleepTask(600);
        if (isTemplateFound(TemplatesEnum.EXPLORATION_VICTORY)) {
            // Allow the result animation to settle once; never tap twice without
            // a fresh recognized Victory frame.
            sleepTask(300);
            if (isTemplateFound(TemplatesEnum.EXPLORATION_VICTORY)) {
                logWarning("Exploration Victory did not close; stopping without another Fight tap; "
                        + TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER,
                                "exploration", "victory-exit-unverified"));
                battleOutcomeUnknown = true;
                return false;
            }
        }
        StatisticsService.obtain().addToCounter(profile, "Exploration Fights Won", 1);
        return true;
    }

    private void handleDefeat() {
        logInfo("Defeated.. Rescheduling...");
        StatisticsService.obtain().addToCounter(profile, "Exploration Fights Lost", 1);
    }

    private BattleResult waitForBattleResult(long fightingStartedAt) {
        long detectionStartedAt = System.nanoTime();
        int attempt = 1;

        while (!isElapsed(detectionStartedAt, RESULT_DETECTION_WINDOW_MS)
                && !isFightingWindowExpired(fightingStartedAt)) {
            QuickDialogOutcome modal = inspectQuickExplorationConfirmation();
            if (modal == QuickDialogOutcome.UNRESOLVED) {
                logWarning("Unexpected confirmation could not be safely handled; "
                        + TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER,
                                "exploration", "quick-confirm-unresolved"));
                return BattleResult.CONFIRMATION_UNRESOLVED;
            }
            if (modal == QuickDialogOutcome.CONFIRMED) {
                logInfo("Quick Exploration modal resolved during battle result polling");
                return BattleResult.QUICK_CONFIRMED;
            }
            if (attempt <= 2 || attempt % 5 == 0) logInfo("Battle result detection attempt #" + attempt + "...");
            if (isTemplateFound(TemplatesEnum.EXPLORATION_VICTORY)) {
                logInfo("Victory template detected.");
                return BattleResult.VICTORY;
            }

            if (isTemplateFound(TemplatesEnum.EXPLORATION_DEFEAT)) {
                logInfo("Defeat template detected.");
                return BattleResult.DEFEAT;
            }

            if (attempt <= 2 || attempt % 5 == 0) logDebug("Battle result absent; waiting for screen transition");
            sleepWithinDetectionWindow(detectionStartedAt, fightingStartedAt);
            attempt++;
        }

        return BattleResult.NOT_FOUND;
    }

    private boolean isTemplateFound(TemplatesEnum template) {
        ImageSearchResultData result = templateSearchHelper.locatePattern(
                template,
                SearchConfig.builder().withMaxAttempts(1).withDelay(0L).build());
        return result != null && result.isFound();
    }

    private void tapSearchResultArea(ImageSearchResultData result) {
        SizeData templateSize = result.getTemplateSize();
        if (templateSize == null || templateSize.getWidth() <= 0 || templateSize.getHeight() <= 0) {
            tapInside(result);
            return;
        }

        PointData center = result.getPoint();
        PointData topLeft = new PointData(
                center.getX() - templateSize.getWidth() / 2,
                center.getY() - templateSize.getHeight() / 2);
        PointData bottomRight = new PointData(
                center.getX() + templateSize.getWidth() / 2,
                center.getY() + templateSize.getHeight() / 2);
        tapInside(topLeft, bottomRight);
    }

    private boolean sleepWithinFightingWindow(long durationMs, long fightingStartedAt) {
        long remainingMs = remainingMs(fightingStartedAt, MAX_FIGHTING_WINDOW_MS);
        if (remainingMs <= 0L) {
            return false;
        }

        sleepTask(Math.min(durationMs, remainingMs));
        return !isFightingWindowExpired(fightingStartedAt);
    }

    private void sleepWithinDetectionWindow(long detectionStartedAt, long fightingStartedAt) {
        long detectionRemainingMs = remainingMs(detectionStartedAt, RESULT_DETECTION_WINDOW_MS);
        long fightingRemainingMs = remainingMs(fightingStartedAt, MAX_FIGHTING_WINDOW_MS);
        long sleepMs = Math.min(RESULT_DETECTION_DELAY_MS, Math.min(detectionRemainingMs, fightingRemainingMs));

        if (sleepMs > 0L) {
            sleepTask(sleepMs);
        }
    }

    private boolean isFightingWindowExpired(long fightingStartedAt) {
        return isElapsed(fightingStartedAt, MAX_FIGHTING_WINDOW_MS);
    }

    private boolean isElapsed(long startedAt, long durationMs) {
        return remainingMs(startedAt, durationMs) <= 0L;
    }

    private long remainingMs(long startedAt, long durationMs) {
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        return durationMs - elapsedMs;
    }

    private void rescheduleInOneHour() {
        logInfo("Exploration task finished. Rescheduling in 1 hour...");
        reschedule(LocalDateTime.now().plusHours(1));
    }

    private enum BattleResult {
        VICTORY,
        DEFEAT,
        NOT_FOUND,
        CONFIRMATION_UNRESOLVED,
        QUICK_CONFIRMED
    }
}
