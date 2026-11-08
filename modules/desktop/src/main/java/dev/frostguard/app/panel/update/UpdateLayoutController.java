package dev.frostguard.app.panel.update;

import dev.frostguard.api.runtime.RuntimeChannel;
import dev.frostguard.api.runtime.WorkspacePaths;
import dev.frostguard.app.BuildMetadata;
import dev.frostguard.app.bootstrap.ApplicationLifecycle;
import dev.frostguard.update.InstallerHandoff;
import dev.frostguard.update.PreparedUpdate;
import dev.frostguard.update.ProjectUpdateKey;
import dev.frostguard.update.RunningBuild;
import dev.frostguard.update.SemanticVersion;
import dev.frostguard.update.UpdateCandidate;
import dev.frostguard.update.UpdateEndpointResolver;
import dev.frostguard.update.UpdateException;
import dev.frostguard.update.UpdateManager;
import dev.frostguard.update.UpdatePlatform;
import dev.frostguard.update.WindowsAuthenticodeVerifier;
import dev.frostguard.update.WindowsInstallerHandoff;
import dev.frostguard.engine.service.ScheduleService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class UpdateLayoutController {
    @FXML private Label labelCurrentVersion;
    @FXML private Label labelChannel;
    @FXML private Label labelStatus;
    @FXML private VBox availableUpdatePane;
    @FXML private Label labelAvailableVersion;
    @FXML private Label labelArtifactDetails;
    @FXML private Hyperlink linkReleaseNotes;
    @FXML private Button buttonCheck;
    @FXML private Button buttonDownloadInstall;
    @FXML private ProgressIndicator progress;
    @FXML private VBox channelSwitchPane;
    @FXML private Label labelChannelSwitch;
    @FXML private Button buttonSwitchChannel;

    private final UpdateEndpointResolver endpoints = new UpdateEndpointResolver();
    private final UpdateManager manager = new UpdateManager(
            new WindowsAuthenticodeVerifier(), new WindowsInstallerHandoff());
    private final ChannelSwitchService channelSwitcher = new ChannelSwitchService();
    private RunningBuild runningBuild;
    private UpdateCandidate candidate;
    private RuntimeChannel switchTarget;
    private final ScheduledExecutorService updateScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "survivalops-update-check");
        thread.setDaemon(true);
        return thread;
    });
    private volatile PreparedUpdate preparedUpdate;
    private volatile boolean updateHandoffQueued;
    private volatile boolean automationPausedForUpdate;

    @FXML
    private void initialize() {
        runningBuild = runningBuild();
        labelCurrentVersion.setText("Current version: " + runningBuild.version());
        labelChannel.setText("Edition: " + (isSurvivalOps() ? "Whiteout Survival" : runningBuild.channel().directoryName()));
        setAvailableUpdateVisible(false);
        progress.setVisible(false);
        configureChannelSwitch();

        if (!runningBuild.permitsAutomaticUpdates()) {
            buttonCheck.setDisable(true);
            if (isSurvivalOps()) {
                labelStatus.setText("SurvivalOps updates are not yet configured: its dedicated signing key is missing.");
            } else if (runningBuild.pullRequestBuild()) {
                labelStatus.setText("PR-test builds cannot use automatic updates.");
            } else if (runningBuild.channel() == RuntimeChannel.DEVELOPMENT) {
                labelStatus.setText("Development builds do not use release update feeds.");
            } else {
                labelStatus.setText("This build has no valid pinned project update key.");
            }
        } else if (runningBuild.platform().operatingSystem() != UpdatePlatform.OperatingSystem.WINDOWS) {
            buttonCheck.setDisable(true);
            labelStatus.setText("Automatic installer handoff is currently available on Windows only.");
        } else {
            labelStatus.setText("Automatically checking the SurvivalOps release feed.");
            if (isSurvivalOps()) {
                updateScheduler.schedule(this::checkForUpdatesInBackground, 45, TimeUnit.SECONDS);
                updateScheduler.scheduleAtFixedRate(this::checkForUpdatesInBackground,
                        6, 6, TimeUnit.HOURS);
            }
        }
    }

    @FXML
    private void handleSwitchChannel() {
        if (switchTarget == null) {
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Open SurvivalOps " + switchTarget.displayName());
        confirmation.setHeaderText(switchTarget == RuntimeChannel.NIGHTLY
                ? "Try SurvivalOps Nightly as a separate installation?"
                : "Return to SurvivalOps Stable?");
        confirmation.setContentText("Stable and Nightly keep separate profiles, tasks, schedules, Telegram "
                + "settings, and databases. Both can run side by side. A first Nightly launch can copy a "
                + "one-time Stable snapshot, but later changes are not synchronized.");
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
        }
        try {
            ChannelSwitchService.Result result = channelSwitcher.open(switchTarget);
            labelStatus.setText(result == ChannelSwitchService.Result.LAUNCHED_INSTALLED
                    ? "SurvivalOps " + switchTarget.displayName() + " was launched separately."
                    : "Opened the SurvivalOps " + switchTarget.displayName() + " release page.");
        } catch (Exception failure) {
            showFailure(new UpdateException("Could not open SurvivalOps " + switchTarget.displayName()
                    + ": " + failure.getMessage(), failure));
        }
    }

    @FXML
    private void handleCheck() {
        setBusy(true, "Checking for updates...");
        runAsync(() -> {
            URI endpoint = endpoints.resolve(runningBuild.channel())
                    .orElseThrow(() -> new UpdateException("No update feed is configured for this channel"));
            return manager.check(endpoint, runningBuild);
        }, this::showCheckResult);
    }

    @FXML
    private void handleDownloadInstall() {
        if (candidate == null) {
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Update SurvivalOps");
        confirmation.setHeaderText("Download SurvivalOps " + candidate.version() + " and restart?");
        confirmation.setContentText("Channel: " + candidate.channel().directoryName() + "\n"
                + "Download: " + formatSize(candidate.artifact().size()) + "\n\n"
                + "SurvivalOps will verify the signed release and download it. Automation will be paused "
                + "without interrupting an active routine; installation starts only after the routine finishes. "
                + "Your workspace and settings are preserved.");
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
        }
        setBusy(true, "Downloading and verifying the authenticated installer...");
        runAsync(() -> manager.prepare(candidate, WorkspacePaths.current().cache()), this::beginHandoff);
    }

    @FXML
    private void openReleaseNotes() {
        if (candidate == null || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(candidate.releaseNotes());
        } catch (Exception exception) {
            showFailure(new UpdateException("Could not open release notes: " + exception.getMessage(), exception));
        }
    }

    private void showCheckResult(Optional<UpdateCandidate> result) {
        setBusy(false, result.isPresent() ? "An update is available." : "SurvivalOps is up to date.");
        candidate = result.orElse(null);
        setAvailableUpdateVisible(candidate != null);
        if (candidate != null) {
            labelAvailableVersion.setText("SurvivalOps " + candidate.version() + " is available");
            labelArtifactDetails.setText(candidate.channel().directoryName() + " / " + candidate.platform().key()
                    + " / " + formatSize(candidate.artifact().size())
                    + "\nChoose Install now to download and verify it. You can safely choose Later.");
            linkReleaseNotes.setText(candidate.releaseNotes().toString());
            buttonDownloadInstall.setText("Nu installeren");
            if (isSurvivalOps()) {
                labelStatus.setText("A new SurvivalOps version is available. Installation requires your approval.");
            }
        }
    }

    @FXML
    private void handleLater() {
        setAvailableUpdateVisible(false);
        labelStatus.setText("Update postponed. SurvivalOps will check again automatically.");
    }

    private void checkForUpdatesInBackground() {
        if (!isSurvivalOps() || !runningBuild.permitsAutomaticUpdates() || manager.isOperationActive()) return;
        try {
            URI endpoint = endpoints.resolve(runningBuild.channel()).orElse(null);
            if (endpoint == null) return;
            Optional<UpdateCandidate> result = manager.check(endpoint, runningBuild);
            Platform.runLater(() -> {
                if (result.isPresent()) showCheckResult(result);
                else if (candidate == null) labelStatus.setText("SurvivalOps is up to date.");
            });
        } catch (Exception failure) {
            Platform.runLater(() -> labelStatus.setText("Automatic update check failed: "
                    + (failure.getMessage() == null ? "unknown error" : failure.getMessage())));
        }
    }

    private void beginHandoff(PreparedUpdate prepared) {
        preparedUpdate = prepared;
        try {
            // Pause queue dispatch first so another routine cannot start while we wait for the current one.
            ScheduleService.obtain().suspendEngine();
            automationPausedForUpdate = true;
        } catch (Exception failure) {
            showFailure(new UpdateException("Could not safely pause automation for the update: "
                    + failure.getMessage(), failure));
            return;
        }
        if (hasExecutingBotRoutine()) {
            if (!updateHandoffQueued) {
                updateHandoffQueued = true;
                labelStatus.setText("Update downloaded and verified. Waiting for the active bot routine to finish; "
                        + "SurvivalOps will not interrupt it.");
                updateScheduler.scheduleAtFixedRate(this::tryApplyPreparedUpdate, 10, 10, TimeUnit.SECONDS);
            }
            return;
        }
        applyPreparedUpdate(prepared);
    }

    private boolean hasExecutingBotRoutine() {
        try {
            return ScheduleService.obtain().hasExecutingTasks();
        } catch (Exception failure) {
            // Fail closed: if task state cannot be established, do not shut down automation.
            labelStatus.setText("Cannot confirm that automation is idle. Update installation is paused.");
            return true;
        }
    }

    private void tryApplyPreparedUpdate() {
        PreparedUpdate ready = preparedUpdate;
        if (ready == null || hasExecutingBotRoutine()) return;
        updateScheduler.shutdown();
        Platform.runLater(() -> applyPreparedUpdate(ready));
    }

    private void applyPreparedUpdate(PreparedUpdate prepared) {
        if (hasExecutingBotRoutine()) {
            labelStatus.setText("A bot routine became active. Installation postponed until it finishes.");
            return;
        }
        labelStatus.setText("Update verified and automation idle. Restarting SurvivalOps to apply it...");
        InstallerHandoff.HandoffSession session = null;
        try {
            session = manager.stageHandoff(prepared, ProcessHandle.current().pid(),
                    currentNativeLauncher(), WorkspacePaths.current().root());
            UpdateExitCoordinator coordinator = new UpdateExitCoordinator(
                    ApplicationLifecycle::stopForUpdate,
                    ApplicationLifecycle::exitAfterUpdateHandoff,
                    ApplicationLifecycle::exitAfterCancelledUpdate);
            coordinator.execute(session);
        } catch (Exception exception) {
            if (session != null) {
                session.cancel();
            }
            showFailure(exception);
        }
    }

    static Path currentNativeLauncher() throws UpdateException {
        String configured = System.getProperty("jpackage.app-path", "").trim();
        if (configured.isEmpty()) {
            throw new UpdateException("The installed SurvivalOps launcher could not be resolved");
        }
        Path launcher = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isRegularFile(launcher)) {
            throw new UpdateException("The installed SurvivalOps launcher does not exist: " + launcher);
        }
        return launcher;
    }

    private <T> void runAsync(Callable<T> operation, Consumer<T> success) {
        Thread.ofVirtual().name("frostguard-update-operation").start(() -> {
            try {
                T result = operation.call();
                Platform.runLater(() -> success.accept(result));
            } catch (Exception exception) {
                Platform.runLater(() -> showFailure(exception));
            }
        });
    }

    private void showFailure(Exception exception) {
        if (automationPausedForUpdate) {
            try {
                ScheduleService.obtain().resumeEngine();
                automationPausedForUpdate = false;
            } catch (Exception resumeFailure) {
                labelStatus.setText("Update failed and automation could not be resumed automatically: "
                        + resumeFailure.getMessage());
            }
        }
        setBusy(false, exception.getMessage() == null ? "Update operation failed." : exception.getMessage());
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("SurvivalOps update failed");
        alert.setHeaderText("The current installation was not replaced.");
        alert.setContentText(labelStatus.getText());
        alert.show();
    }

    private void setBusy(boolean busy, String status) {
        progress.setVisible(busy);
        buttonCheck.setDisable(busy || !runningBuild.permitsAutomaticUpdates());
        buttonDownloadInstall.setDisable(busy);
        labelStatus.setText(status);
    }

    private void setAvailableUpdateVisible(boolean visible) {
        availableUpdatePane.setVisible(visible);
        availableUpdatePane.setManaged(visible);
    }

    private void configureChannelSwitch() {
        boolean supported = !isSurvivalOps() && supportsChannelSwitch(runningBuild.channel());
        channelSwitchPane.setVisible(supported);
        channelSwitchPane.setManaged(supported);
        if (!supported) {
            switchTarget = null;
            return;
        }
        switchTarget = runningBuild.channel().alternateRelease();
        buttonSwitchChannel.setText(switchTarget == RuntimeChannel.NIGHTLY ? "Try Nightly" : "Return to Stable");
        labelChannelSwitch.setText("SurvivalOps " + switchTarget.displayName()
                + " is a separate application with its own settings and workspace.");
    }

    private static boolean isSurvivalOps() {
        return "SurvivalOps".equalsIgnoreCase(System.getProperty("frostguard.brand.name", "").trim());
    }

    static boolean supportsChannelSwitch(RuntimeChannel channel) {
        return channel != null && channel.isPublicRelease();
    }

    static RunningBuild runningBuild() {
        String version = Optional.ofNullable(UpdateLayoutController.class.getPackage().getImplementationVersion())
                .filter(value -> !value.isBlank())
                .orElseGet(() -> System.getProperty("frostguard.version", "2.1.0"));
        BuildMetadata metadata = BuildMetadata.current();
        return new RunningBuild(SemanticVersion.parse(version), SemanticVersion.parse(version),
                WorkspacePaths.current().channel(), UpdatePlatform.current(),
                metadata.pullRequestBuild(), ProjectUpdateKey.current(), metadata.authenticodePublisher());
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes / 1024.0;
        if (value < 1024) return String.format(java.util.Locale.ROOT, "%.1f KiB", value);
        value /= 1024.0;
        if (value < 1024) return String.format(java.util.Locale.ROOT, "%.1f MiB", value);
        return String.format(java.util.Locale.ROOT, "%.1f GiB", value / 1024.0);
    }
}
