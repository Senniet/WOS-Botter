package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.SizeData;
import dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig;
import dev.frostguard.engine.helper.BlueLabelActionDetector;
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
    private static final PointData VICTORY_CONTINUE_TOP_LEFT = new PointData(400, 990);
    private static final PointData VICTORY_CONTINUE_BOTTOM_RIGHT = new PointData(658, 1038);

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
        // Resume a confirmation left by a previous run instead of navigating
        // blindly through a pending modal or starting another battle.
        QuickDialogOutcome pending = inspectQuickExplorationConfirmation();
        if (pending != QuickDialogOutcome.NOT_PRESENT) {
            if (pending == QuickDialogOutcome.CONFIRMED) {
                BattleResult resumed = waitForBattleResult(System.nanoTime());
                if (resumed == BattleResult.VICTORY) handleVictory();
                else if (resumed == BattleResult.DEFEAT) handleDefeat();
                else logWarning("Previously pending Quick Exploration has an unknown outcome; not starting a new battle");
            } else {
                logWarning("Pending Quick Exploration modal is unresolved; "
                        + TaskDiagnosticSnapshots.capture(emuManager, EMULATOR_NUMBER,
                                "exploration", "quick-confirm-unresolved"));
            }
            reschedule(LocalDateTime.now().plusMinutes(10));
            return;
        }
        logInfo("Opening exploration screen...");
        if (!explorationTitleVisible()) navigationHelper.ensureCorrectScreenLocation(LaunchPoint.WORLD);
        if (!openExplorationScreen()) {
            logWarning("Exploration page could not be verified; " + TaskDiagnosticSnapshots.capture(
                    emuManager, EMULATOR_NUMBER, "exploration", "entry-unverified"));
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }

        logInfo("Searching exploration button...");
        ImageSearchResultData exploreButton = detectExploreButton();
        if (exploreButton == null || !exploreButton.isFound()) {
            logWarning("Exploration button not found");
            rescheduleInOneHour();
            return;
        }

        logInfo("Exploring...");
        tapExploreButton(exploreButton);

        boolean fightingWindowReached = fightUntilStoppedOrExpired();
        if (fightingWindowReached) {
            logWarning("Exploration fighting window reached 2 minutes. Rescheduling...");
        }

        if (battleOutcomeUnknown) {
            logWarning("Exploration outcome remains unknown. Retrying in 10 minutes, not recording completion.");
            reschedule(LocalDateTime.now().plusMinutes(10));
        } else {
            rescheduleInOneHour();
        }
    }

    private boolean openExplorationScreen() {
        if (explorationTitleVisible()) return true;
        logInfo("Tapping exploration entry area...");
        tapInside(EXPLORATION_ENTRY_TOP_LEFT, EXPLORATION_ENTRY_BOTTOM_RIGHT);
        sleepTask(EXPLORATION_SCREEN_LOAD_DELAY_MS);
        return explorationTitleVisible();
    }

    private boolean explorationTitleVisible() {
        if (templateSearchHelper.locatePattern(TemplatesEnum.EXPLORATION_SCREEN_TITLE,
                SearchConfig.builder().withMaxAttempts(2).withDelay(150).withThreshold(90)
                        .withArea(AreaData.of(70, 5, 365, 150)).build()).isFound()) return true;
        return templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_EXPLORATION_TITLE,
                SearchConfig.builder().withMaxAttempts(2).withDelay(150).withThreshold(90)
                        .withArea(AreaData.of(50, 65, 330, 160)).build()).isFound();
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
            if (alreadyPending == QuickDialogOutcome.NOT_PRESENT) startBattle();
            else logInfo("Previous Quick Exploration confirmation handled; not tapping Fight again");

            logInfo("Waiting " + RESULT_SEARCH_START_DELAY_MS
                    + "ms before checking the result and any late confirmation...");
            if (!sleepWithinFightingWindow(RESULT_SEARCH_START_DELAY_MS, fightingStartedAt)) {
                break;
            }

            logInfo("Waiting for battle result...");
            BattleResult battleResult = waitForBattleResult(fightingStartedAt);
            if (battleResult == BattleResult.VICTORY) {
                handleVictory();
                battleNumber++;
            } else if (battleResult == BattleResult.DEFEAT) {
                handleDefeat();
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

    void startBattle() {
        boolean useQuickDeploy = profile.getConfig(
                ConfigurationKeyEnum.DO_EXPLORATION_QUICK_DEPLOY_BOOL, Boolean.class);
        if (useQuickDeploy) {
            logInfo("Tapping quick deploy...");
            tapInside(QUICK_DEPLOY_TOP_LEFT, QUICK_DEPLOY_BOTTOM_RIGHT);
            sleepTask(QUICK_DEPLOY_DELAY_MS);
        } else {
            logInfo("Quick Deploy is disabled. Keeping the saved exploration formation.");
        }
        logInfo("Tapping fight button...");
        tapInside(FIGHT_BUTTON_TOP_LEFT, FIGHT_BUTTON_BOTTOM_RIGHT);
    }

    /**
     * The confirmation may appear several seconds after Fight. Inspect it on
     * every battle-result poll, rather than only in a 450ms post-tap window.
     * Never confirm any other orange/blue modal without matching its text.
     */
    QuickDialogOutcome inspectQuickExplorationConfirmation() {
        BufferedImage first = ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER));
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
                    textArea.topLeft(), textArea.bottomRight());
        } catch (IOException | OcrException error) {
            logWarning("Quick confirmation OCR could not be verified: " + error.getClass().getSimpleName());
            return QuickDialogOutcome.UNRESOLVED;
        }
        if (!isQuickExplorationDialog(wording)) {
            logWarning("Refusing to confirm a visually similar modal without the Quick Exploration wording");
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
        logInfo("Quick Exploration confirmation dismissed; battle outcome still needs verification");
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
            if (orange >= w / 55 && blue >= w / 55 && orange + blue > bestScore) {
                bestScore = orange + blue;
                bestY = y;
            }
        }
        return bestY < 0 ? null : new PointData(w * 74 / 100, bestY);
    }

    private void handleVictory() {
        logInfo("Victory! Continue...");
        StatisticsService.obtain().addToCounter(profile, "Exploration Fights Won", 1);
        logInfo("Tapping victory continue area...");
        tapInside(VICTORY_CONTINUE_TOP_LEFT, VICTORY_CONTINUE_BOTTOM_RIGHT);
        sleepTask(VICTORY_CONTINUE_DELAY_MS);
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
                attempt = 1;
                continue;
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
        CONFIRMATION_UNRESOLVED
    }
}
