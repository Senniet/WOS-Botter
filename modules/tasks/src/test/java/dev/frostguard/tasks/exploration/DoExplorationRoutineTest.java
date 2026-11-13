package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.runtime.WorkspacePaths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoExplorationRoutineTest {

    @BeforeAll
    static void createsRuntimeWorkspaceBeforeRoutineConstruction() throws IOException {
        Files.createDirectories(WorkspacePaths.current().root());
    }

    @Test
    void usesQuickDeployWhenEnabled() {
        TestableDoExplorationRoutine routine = routineWithQuickDeploy(true);

        routine.startBattle();

        assertEquals(List.of(
                new PointData(55, 1170),
                new PointData(390, 1170)), routine.tapTopLefts);
        assertEquals(List.of(300L), routine.sleepDurations);
    }

    @Test
    void keepsSavedFormationWhenQuickDeployIsDisabled() {
        TestableDoExplorationRoutine routine = routineWithQuickDeploy(false);

        routine.startBattle();

        assertEquals(List.of(new PointData(390, 1170)), routine.tapTopLefts);
        assertEquals(List.of(), routine.sleepDurations);
    }

    @Test
    void quickDeployRemainsEnabledByDefault() {
        AccountDescriptor profile = profile();

        assertTrue(profile.getConfig(ConfigurationKeyEnum.DO_EXPLORATION_QUICK_DEPLOY_BOOL, Boolean.class));
    }

    @Test
    void identifiesSpecificQuickExplorationMessageOnly() {
        assertTrue(DoExplorationRoutine.isQuickExplorationDialog(
                "Squad Power far exceeds the enemy. Start Quick Exploration?"));
        assertTrue(!DoExplorationRoutine.isQuickExplorationDialog(
                "Purchase confirmation: are you sure?"));
    }

    @Test
    void detectsRealMaskedQuickExplorationDialogAndCoversItsPromptText() throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/serveemu/quick-exploration-modal-masked.png")) {
            assertTrue(stream != null, "Masked diagnostic frame missing");
            BufferedImage frame = ImageIO.read(stream);
            PointData button = DoExplorationRoutine.quickConfirmationButton(frame);
            assertTrue(button != null, "Real dialog Confirm button must be recognized");
            assertTrue(button.getX() > 450 && button.getX() < 620);
            assertTrue(button.getY() >= 770 && button.getY() <= 870);
            var text = DoExplorationRoutine.quickConfirmationTextArea(frame, button);
            assertTrue(text.topLeft().getY() < 625);
            assertTrue(text.bottomRight().getY() > 675);
        }
    }

    @Test
    void recognizesPairedDialogButtonsAndRejectsUnrelatedBlueButton() {
        BufferedImage frame = new BufferedImage(720, 1280, BufferedImage.TYPE_INT_RGB);
        for (int y = 760; y < 825; y++) {
            for (int x = 45; x < 345; x++) frame.setRGB(x, y, new Color(255, 115, 0).getRGB());
            for (int x = 390; x < 680; x++) frame.setRGB(x, y, new Color(50, 150, 245).getRGB());
        }
        PointData button = DoExplorationRoutine.quickConfirmationButton(frame);
        assertTrue(button != null);
        assertEquals(720 * 74 / 100, button.getX());
        assertTrue(button.getY() >= 760 && button.getY() < 825);
        for (int y = 760; y < 825; y++)
            for (int x = 45; x < 345; x++) frame.setRGB(x, y, Color.BLACK.getRGB());
        assertTrue(DoExplorationRoutine.quickConfirmationButton(frame) == null);
    }

    private TestableDoExplorationRoutine routineWithQuickDeploy(boolean enabled) {
        AccountDescriptor profile = profile();
        profile.setConfig(ConfigurationKeyEnum.DO_EXPLORATION_QUICK_DEPLOY_BOOL, enabled);
        return new TestableDoExplorationRoutine(profile);
    }

    private AccountDescriptor profile() {
        return new AccountDescriptor(1L, "Test", "1", true, 1L, 30L);
    }

    private static final class TestableDoExplorationRoutine extends DoExplorationRoutine {
        private final List<PointData> tapTopLefts = new ArrayList<>();
        private final List<Long> sleepDurations = new ArrayList<>();

        private TestableDoExplorationRoutine(AccountDescriptor profile) {
            super(profile, TpDailyTaskEnum.DO_EXPLORATION);
        }

        @Override
        public void tapInside(PointData topLeft, PointData bottomRight) {
            tapTopLefts.add(topLeft);
        }

        @Override
        protected void sleepTask(long millis) {
            sleepDurations.add(millis);
        }
    }
}
