package dev.frostguard.app.panel.launcher;

import com.sun.management.OperatingSystemMXBean;
import dev.frostguard.app.panel.profile.ProfileAux;
import dev.frostguard.api.domain.QueueProfileStateData;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Map;

public class DashboardLayoutController {
    @FXML private Label accountCount;
    @FXML private Label accountDetail;
    @FXML private Label queueCount;
    @FXML private Label queueDetail;
    @FXML private Label engineState;
    @FXML private Label uptimeValue;
    @FXML private VBox accountRows;
    @FXML private Label accountEmpty;
    @FXML private Label cpuValue;
    @FXML private javafx.scene.control.ProgressBar cpuBar;
    @FXML private Label memoryValue;
    @FXML private javafx.scene.control.ProgressBar memoryBar;
    @FXML private Label adbValue;
    @FXML private Label routineState;
    @FXML private VBox activityRows;
    @FXML private Label activityEmpty;

    public void updateSnapshot(Collection<ProfileAux> profiles,
                               Map<Long, QueueProfileStateData> queues,
                               Map<Long, String> currentTasks,
                               Map<Long, String> nextTasks,
                               java.util.List<String> recentActivity,
                               boolean engineRunning, boolean allQueuesPaused,
                               String uptime) {
        int profileCount = profiles == null ? 0 : profiles.size();
        int active = queues == null ? 0 : (int) queues.values().stream().filter(q -> !q.isPaused()).count();
        int waiting = queues == null ? 0 : (int) queues.values().stream().filter(QueueProfileStateData::isPaused).count();

        accountCount.setText(profileCount + (profileCount == 1 ? " Account" : " Accounts"));
        accountDetail.setText(profileCount == 0 ? "Nog geen accounts ingesteld" : "Profielen in SurvivalOps");
        queueCount.setText((active + waiting) + " Wachtrijen");
        queueDetail.setText(active + " actief · " + waiting + " gepauzeerd");
        engineState.setText(!engineRunning ? "Gestopt" : allQueuesPaused ? "Gepauzeerd" : "Actief");
        uptimeValue.setText(uptime == null ? "—" : uptime);

        accountRows.getChildren().clear();
        if (profiles != null) {
            for (ProfileAux profile : profiles) {
                QueueProfileStateData queue = queues == null ? null : queues.get(profile.getId());
                String state = queue == null ? "Niet actief" : queue.isPaused() ? "Gepauzeerd" : "Actief";
                HBox row = new HBox(10);
                row.getStyleClass().add("dashboard-account-row");
                Label name = new Label(profile.getName() == null ? "Onbekend account" : profile.getName());
                name.getStyleClass().add("dashboard-row-name");
                name.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(name, Priority.ALWAYS);
                Label status = new Label(state);
                status.getStyleClass().add(queue == null ? "status-idle" : queue.isPaused() ? "status-paused" : "status-active");
                Label emulator = new Label(profile.getEmulatorNumber() == null || profile.getEmulatorNumber().isBlank()
                        ? "Emulator niet ingesteld" : "Emulator " + profile.getEmulatorNumber());
                emulator.getStyleClass().add("dashboard-row-detail");
                row.getChildren().addAll(name, status, emulator);
                accountRows.getChildren().add(row);
            }
        }
        accountEmpty.setVisible(profileCount == 0);
        accountEmpty.setManaged(profileCount == 0);
        String currentTask = firstTask(currentTasks);
        String nextTask = firstTask(nextTasks);
        routineState.setText(engineRunning
                ? (allQueuesPaused ? "Automatisering is gepauzeerd." : "Automatisering draait.")
                    + (currentTask != null ? " Actieve taak: " + currentTask
                    : nextTask == null ? " Er staat geen volgende taak in de wachtrij." : " Volgende taak: " + nextTask)
                : "Geen actieve routine. Start automatisering om taken uit te voeren.");
        activityRows.getChildren().clear();
        if (recentActivity != null) {
            recentActivity.stream().limit(5).forEach(entry -> {
                Label line = new Label(entry);
                line.getStyleClass().add("dashboard-activity-line");
                line.setWrapText(true);
                line.setMaxWidth(Double.MAX_VALUE);
                activityRows.getChildren().add(line);
            });
        }
        boolean hasActivity = !activityRows.getChildren().isEmpty();
        activityEmpty.setVisible(!hasActivity);
        activityEmpty.setManaged(!hasActivity);
        adbValue.setText("Controleer Apparaat (ADB)");

        updateSystemMetrics();
    }

    private void updateSystemMetrics() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof OperatingSystemMXBean osBean) {
            long totalMemory = osBean.getTotalMemorySize();
            long freeMemory = osBean.getFreeMemorySize();
            long usedMemory = Math.max(0, totalMemory - freeMemory);
            memoryValue.setText(formatBytes(usedMemory) + " / " + formatBytes(totalMemory));
            memoryBar.setProgress(totalMemory > 0 ? Math.min(1.0, (double) usedMemory / totalMemory) : 0);
            double load = osBean.getCpuLoad();
            if (Double.isFinite(load) && load >= 0) {
                cpuValue.setText(Math.round(load * 100) + "%");
                cpuBar.setProgress(Math.min(1.0, load));
            } else {
                cpuValue.setText("Niet beschikbaar");
                cpuBar.setProgress(0);
            }
        } else {
            cpuValue.setText("Niet beschikbaar");
            cpuBar.setProgress(0);
            memoryValue.setText("Niet beschikbaar");
            memoryBar.setProgress(0);
        }
    }

    private static String firstTask(Map<Long, String> tasks) {
        return tasks == null ? null : tasks.values().stream()
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format("%.1f GB", bytes / (1024d * 1024 * 1024));
        return String.format("%.0f MB", bytes / (1024d * 1024));
    }
}