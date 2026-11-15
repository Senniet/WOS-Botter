package dev.frostguard.app.panel.misc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeSet;
import dev.frostguard.api.runtime.WorkspacePaths;
import java.util.ArrayList;
import java.util.List;

import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.engine.diagnostics.TaskTemplateCoach;
import dev.frostguard.engine.diagnostics.VerifiedTemplateOverrides;
import dev.frostguard.engine.service.ScheduleService;
import dev.frostguard.engine.schedule.TaskQueue;
import dev.frostguard.engine.service.ProfileService;
import dev.frostguard.vision.ocr.OcrEngine;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

public class DebuggingLayoutController {

    @FXML
    private Rectangle selectionBox;

    private double dragStartX = -1;
    private double dragStartY = -1;
    private boolean isDragging = false;

    @FXML
    private StackPane imageContainer;

    @FXML
    private ImageView screenshotImageView;

    @FXML
    private Label dragDropHint;

    @FXML
    private Pane crosshairPane;

    @FXML
    private Line crosshairX;

    @FXML
    private Line crosshairY;

    @FXML
    private Label coordinatesLabel;

    @FXML
    private TextField searchTextField;

    @FXML
    private ListView<File> templateListView;

    @FXML
    private ComboBox<String> actionComboBox;

    @FXML
    private Button btnRunAction;

    @FXML
    private TextArea logTextArea;

    @FXML
    private ComboBox<AccountDescriptor> profileComboBox;

    @FXML
    private Button btnCaptureAdb;

    @FXML
    private ComboBox<String> auditGroupComboBox;

    @FXML
    private Button btnAuditLibrary;

    @FXML
    private ComboBox<TpDailyTaskEnum> coachTaskComboBox;

    @FXML
    private javafx.scene.control.CheckBox coachAutoRepairCheckBox;

    @FXML
    private Label coachStatusLabel;

    private javafx.animation.Timeline coachStatusTimer;
    private String lastCoachPhase = "IDLE";

    private File currentScreenshotFile;
    private List<File> allTemplates = new ArrayList<>();

    @FXML
    public void initialize() {
        // Apply rounded clip to screenshot image
        Rectangle clip = new Rectangle(316, 576);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        screenshotImageView.setClip(clip);

        setupDragAndDrop();
        setupProfiles();
        loadTemplates();
        populateAuditGroups();
        setupTaskCoach();
        setupSearchFilter();
        setupActions();
    }

    private void setupProfiles() {
        if (profileComboBox != null) {
            profileComboBox.setOnShowing(e -> {
                try {
                    List<AccountDescriptor> profiles = ProfileService.obtain().fetchAllAccounts();
                    if (profiles != null) {
                        AccountDescriptor currentlySelected = profileComboBox.getValue();
                        profileComboBox.setItems(FXCollections.observableArrayList(profiles));
                        profileComboBox.setConverter(new javafx.util.StringConverter<>() {
                            @Override
                            public String toString(AccountDescriptor p) {
                                return p == null ? "" : p.getName() + " (Emulator " + p.getEmulatorNumber() + ")";
                            }

                            @Override
                            public AccountDescriptor fromString(String string) {
                                return null;
                            }
                        });

                        if (currentlySelected != null) {
                            // Try to maintain selection
                            for (AccountDescriptor p : profiles) {
                                if (p.getEmulatorNumber() == currentlySelected.getEmulatorNumber()) {
                                    profileComboBox.getSelectionModel().select(p);
                                    break;
                                }
                            }
                        } else if (!profiles.isEmpty()) {
                            profileComboBox.getSelectionModel().selectFirst();
                        }
                    }
                } catch (Exception ex) {
                    log("Error loading profiles: " + ex.getMessage());
                }
            });
        }
    }

    @FXML
    private void handleMouseMoved(MouseEvent event) {
        if (currentScreenshotFile == null || screenshotImageView.getImage() == null)
            return;

        // Unlock if the user explicitly moves the mouse inside the container
        if (isCrosshairLocked) {
            isCrosshairLocked = false;
        }

        Image img = screenshotImageView.getImage();
        double mouseX = event.getX();
        double mouseY = event.getY();

        // Ensure coordinates are within image view bounds
        double viewWidth = 316.0; // matched fitWidth
        double viewHeight = 576.0; // matched fitHeight

        // Offset handle logic - the imageContainer is slightly larger than the clip
        double offsetX = (imageContainer.getWidth() - viewWidth) / 2.0;
        double offsetY = (imageContainer.getHeight() - viewHeight) / 2.0;

        double relX = mouseX - offsetX;
        double relY = mouseY - offsetY;

        if (relX < 0 || relX > viewWidth || relY < 0 || relY > viewHeight) {
            crosshairPane.setVisible(false);
            return;
        }

        double origWidth = img.getWidth();
        double origHeight = img.getHeight();

        int imgX = (int) ((relX / viewWidth) * origWidth);
        int imgY = (int) ((relY / viewHeight) * origHeight);

        imgX = Math.max(0, Math.min(imgX, (int) origWidth - 1));
        imgY = Math.max(0, Math.min(imgY, (int) origHeight - 1));

        crosshairX.setStartX(0);
        crosshairX.setEndX(imageContainer.getWidth());
        crosshairX.setStartY(mouseY);
        crosshairX.setEndY(mouseY);

        crosshairY.setStartX(mouseX);
        crosshairY.setEndX(mouseX);
        crosshairY.setStartY(0);
        crosshairY.setEndY(imageContainer.getHeight());

        coordinatesLabel.setText(String.format("X: %d\nY: %d", imgX, imgY));

        // Keep label within bounds
        double labelX = mouseX + 15;
        double labelY = mouseY + 15;
        if (labelX + coordinatesLabel.getWidth() > imageContainer.getWidth()) {
            labelX = mouseX - 15 - coordinatesLabel.getWidth();
        }
        if (labelY + coordinatesLabel.getHeight() > imageContainer.getHeight()) {
            labelY = mouseY - 15 - coordinatesLabel.getHeight();
        }

        coordinatesLabel.setLayoutX(labelX);
        coordinatesLabel.setLayoutY(labelY);

        if (!crosshairPane.isVisible() && !isDragging) {
            crosshairPane.setVisible(true);
            crosshairX.setVisible(true);
            crosshairY.setVisible(true);
            coordinatesLabel.setVisible(true);
        }
    }

    @FXML
    private void handleMousePressed(MouseEvent event) {
        if (currentScreenshotFile == null || screenshotImageView.getImage() == null)
            return;

        dragStartX = event.getX();
        dragStartY = event.getY();
        isDragging = true;

        selectionBox.setX(dragStartX);
        selectionBox.setY(dragStartY);
        selectionBox.setWidth(0);
        selectionBox.setHeight(0);
        selectionBox.setVisible(true);

        // Hide crosshair lines when dragging
        crosshairX.setVisible(false);
        crosshairY.setVisible(false);
        coordinatesLabel.setVisible(false);
    }

    @FXML
    private void handleMouseDragged(MouseEvent event) {
        if (!isDragging || currentScreenshotFile == null)
            return;

        double currentX = event.getX();
        double currentY = event.getY();

        // Clamp to imageContainer bounds
        currentX = Math.max(0, Math.min(currentX, imageContainer.getWidth()));
        currentY = Math.max(0, Math.min(currentY, imageContainer.getHeight()));

        double x = Math.min(dragStartX, currentX);
        double y = Math.min(dragStartY, currentY);
        double width = Math.abs(currentX - dragStartX);
        double height = Math.abs(currentY - dragStartY);

        selectionBox.setX(x);
        selectionBox.setY(y);
        selectionBox.setWidth(width);
        selectionBox.setHeight(height);

        if (!crosshairPane.isVisible()) {
            crosshairPane.setVisible(true);
        }
    }

    @FXML
    private void handleMouseReleased(MouseEvent event) {
        if (!isDragging)
            return;
        isDragging = false;

        if ("OCR".equals(actionComboBox.getValue())) {
            performOCROnSelection();
        }
    }

    @FXML
    private void handleMouseEntered(MouseEvent event) {
        if (currentScreenshotFile != null && !isCrosshairLocked) {
            crosshairPane.setVisible(true);
        }
    }

    @FXML
    private void handleMouseExited(MouseEvent event) {
        if (crosshairPane != null && !isCrosshairLocked) {
            crosshairPane.setVisible(false);
        }
    }

    private void setupSearchFilter() {
        if (searchTextField != null) {
            searchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == null || newValue.trim().isEmpty()) {
                    templateListView.getItems().setAll(allTemplates);
                } else {
                    String lowerCaseFilter = newValue.toLowerCase();
                    List<File> filteredList = new ArrayList<>();
                    for (File file : allTemplates) {
                        if (file.getName().toLowerCase().contains(lowerCaseFilter)) {
                            filteredList.add(file);
                        }
                    }
                    templateListView.getItems().setAll(filteredList);
                }
            });
        }
    }

    private void setupDragAndDrop() {
        imageContainer.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                boolean isImage = db.getFiles().stream().anyMatch(file -> {
                    String name = file.getName().toLowerCase();
                    return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                            || name.endsWith(".bmp");
                });
                if (isImage) {
                    event.acceptTransferModes(TransferMode.COPY);
                    imageContainer.getStyleClass().add("drag-hover");
                }
            }
            event.consume();
        });

        imageContainer.setOnDragExited(event -> {
            imageContainer.getStyleClass().remove("drag-hover");
            event.consume();
        });

        imageContainer.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;

            imageContainer.getStyleClass().remove("drag-hover");

            if (db.hasFiles()) {
                for (File file : db.getFiles()) {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                            || name.endsWith(".bmp")) {
                        loadScreenshot(file);
                        success = true;
                        break;
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        imageContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    if (event.isShortcutDown() && event.getCode() == javafx.scene.input.KeyCode.V) {
                        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                        if (clipboard.hasImage()) {
                            Image image = clipboard.getImage();
                            screenshotImageView.setImage(image);
                            dragDropHint.setVisible(false);
                            try {
                                File tempFile = File.createTempFile("clipboard_screenshot", ".png");
                                tempFile.deleteOnExit();
                                javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", tempFile);
                                currentScreenshotFile = tempFile;
                                log("Loaded screenshot from clipboard");
                                log("Size: " + image.getWidth() + "x" + image.getHeight());
                            } catch (Exception e) {
                                log("Error saving clipboard image to temp file: " + e.getMessage());
                            }
                        } else if (clipboard.hasFiles()) {
                            for (File file : clipboard.getFiles()) {
                                String name = file.getName().toLowerCase();
                                if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp")) {
                                    loadScreenshot(file);
                                    break;
                                }
                            }
                        }
                    }
                });
            }
        });
    }

    private void loadScreenshot(File file) {
        try {
            Image image = new Image(new FileInputStream(file));
            screenshotImageView.setImage(image);
            dragDropHint.setVisible(false);
            currentScreenshotFile = file;
            log("Loaded screenshot: " + file.getAbsolutePath());
            log("Size: " + image.getWidth() + "x" + image.getHeight());
        } catch (FileNotFoundException e) {
            log("Error loading screenshot: " + e.getMessage());
        }
    }

    private void loadTemplates() {
        List<File> possibleTemplateDirs = new ArrayList<>();
        possibleTemplateDirs.add(new File("modules/vision/src/main/resources/templates"));
        possibleTemplateDirs.add(new File("../modules/vision/src/main/resources/templates"));
        possibleTemplateDirs.add(new File("../../modules/vision/src/main/resources/templates"));
        possibleTemplateDirs.add(new File("templates"));
        // jpackage stages input/templates under <install>/app/templates,
        // while the launcher lives in <install>/SurvivalOps.exe. The process
        // working directory is not guaranteed to be the installation folder.
        String launcherPath = System.getProperty("jpackage.app-path", "");
        if (!launcherPath.isBlank()) {
            File launcherDir = new File(launcherPath).getAbsoluteFile().getParentFile();
            if (launcherDir != null) {
                possibleTemplateDirs.add(new File(launcherDir, "app/templates"));
                possibleTemplateDirs.add(new File(launcherDir, "templates"));
            }
        }
        File currentDir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (currentDir != null && currentDir.exists()) {
            possibleTemplateDirs.add(new File(currentDir, "modules/vision/src/main/resources/templates"));
            currentDir = currentDir.getParentFile();
        }

        File templateDir = null;
        for (File dir : possibleTemplateDirs) {
            if (dir.isDirectory() && containsTemplateImages(dir)) {
                templateDir = dir;
                break;
            }
        }

        allTemplates.clear();
        if (templateDir != null && templateDir.exists() && templateDir.isDirectory()) {
            findImageFiles(templateDir, allTemplates);
            log("Found " + allTemplates.size() + " templates.");
        } else {
            log("Template directory not found. Please verify location.");
            log("User Dir: " + System.getProperty("user.dir"));
        }

        templateListView.getItems().setAll(allTemplates);
        templateListView.setCellFactory(param -> new ListCell<File>() {
            private ImageView imageView = new ImageView();

            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                if (empty || file == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(file.getName());
                    try {
                        Image icon = new Image(new FileInputStream(file), 32, 32, true, true);
                        imageView.setImage(icon);
                        setGraphic(imageView);
                    } catch (FileNotFoundException e) {
                        setGraphic(null);
                    }
                }
            }
        });
    }

    private boolean containsTemplateImages(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (file.isDirectory() && containsTemplateImages(file)) return true;
            String name = file.getName().toLowerCase(java.util.Locale.ROOT);
            if (file.isFile() && (name.endsWith(".png") || name.endsWith(".jpg")
                    || name.endsWith(".bmp"))) return true;
        }
        return false;
    }

    private void findImageFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findImageFiles(file, list);
                } else {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".bmp")) {
                        list.add(file);
                    }
                }
            }
        }
    }

    private void setupTaskCoach() {
        List<TpDailyTaskEnum> selectable = java.util.Arrays.stream(TpDailyTaskEnum.values())
                .filter(t -> t != TpDailyTaskEnum.INITIALIZE
                        && t != TpDailyTaskEnum.CREATE_CHARACTER
                        && t != TpDailyTaskEnum.SKIP_TUTORIAL
                        && t != TpDailyTaskEnum.CUSTOM_TASK
                        && t != TpDailyTaskEnum.DUMMY_TASK)
                .sorted(java.util.Comparator.comparing(TpDailyTaskEnum::getName)).toList();
        coachTaskComboBox.setItems(FXCollections.observableArrayList(selectable));
        coachStatusTimer = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(1), event -> refreshTaskCoachStatus(false)));
        coachStatusTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        coachStatusLabel.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) coachStatusTimer.stop();
            else coachStatusTimer.play();
        });
        refreshTaskCoachStatus(false);
    }

    @FXML
    private void handleStartTaskCoach(ActionEvent event) {
        AccountDescriptor profile = profileComboBox.getValue();
        TpDailyTaskEnum task = coachTaskComboBox.getValue();
        if (profile == null || task == null || profile.getId() == null) {
            log("Choose an ADB profile and the exact task to diagnose.");
            return;
        }
        TaskQueue queue;
        try {
            queue = ScheduleService.obtain().getCoordinator().getQueue(profile.getId());
        } catch (Exception ex) {
            log("Cannot access task queue: " + ex.getMessage());
            return;
        }
        if (queue == null || !queue.isActive()) {
            log("Start the bot/profile first; Task Coach uses the normal task queue.");
            return;
        }
        if (queue.hasExecutingTask()) {
            log("Another task is executing. Try again when it has completed.");
            return;
        }
        boolean newlyArmed = false;
        try {
            var status = TaskTemplateCoach.arm(profile.getId(), profile.getEmulatorNumber(), task,
                    coachAutoRepairCheckBox.isSelected());
            newlyArmed = true;
            queue.runNow(task, false);
            log("Task Coach armed: " + task.getName() + " for selected profile."
                    + " The routine will execute once via the existing queue.");
            log("Evidence will be saved locally in: " + status.directory());
            refreshTaskCoachStatus(false);
        } catch (Exception ex) {
            if (newlyArmed) TaskTemplateCoach.stop();
            log("Could not start Task Coach: " + ex.getMessage());
        }
    }

    @FXML
    private void handleStopTaskCoach(ActionEvent event) {
        TaskTemplateCoach.stop();
        log("Stopped Task Coach evidence collection. Running game task continues normally.");
        refreshTaskCoachStatus(false);
    }

    @FXML
    private void handleRollbackCoachTemplate(ActionEvent event) {
        File template = templateListView.getSelectionModel().getSelectedItem();
        if (template == null) {
            log("Select a repaired template in the template list first.");
            return;
        }
        String resource = ScreenTemplateAudit.resourceForTemplate(template.toPath());
        try {
            boolean reverted = VerifiedTemplateOverrides.rollback(resource);
            log(reverted ? "Rolled back verified repair for " + resource
                    + ". Bundled original is now used again."
                    : "No active verified repair found for " + resource);
        } catch (Exception ex) {
            log("Could not roll back template repair: " + ex.getMessage());
        }
    }

    @FXML
    private void handleShowTaskCoach(ActionEvent event) {
        refreshTaskCoachStatus(true);
        var status = TaskTemplateCoach.snapshot();
        if (status.directory().isBlank()) return;
        Path summary = Path.of(status.directory()).resolve("summary.txt");
        if (!Files.isRegularFile(summary)) {
            log("Detailed report appears after the task completes (or diagnostics are stopped).");
            return;
        }
        try {
            List<String> lines = Files.readAllLines(summary);
            for (int index = 0; index < Math.min(85, lines.size()); index++) log(lines.get(index));
            if (lines.size() > 85) log("... " + (lines.size() - 85)
                    + " more lines in " + summary);
            log("Individual scores and locations: "
                    + Path.of(status.directory()).resolve("recognition-events.csv"));
        } catch (Exception ex) {
            log("Could not read Task Coach report: " + ex.getMessage());
        }
    }

    private void refreshTaskCoachStatus(boolean detailed) {
        var status = TaskTemplateCoach.snapshot();
        coachStatusLabel.setText(status.phase() + " | " + status.task()
                + " | checks " + status.checks() + ", hits " + status.hits()
                + ", misses " + status.misses() + ", captures " + status.captures()
                + ", proposed " + status.proposals() + ", repaired " + status.repaired()
                + "\n" + status.note());
        if (!lastCoachPhase.equals(status.phase())) {
            lastCoachPhase = status.phase();
            if (status.phase().equals("COMPLETED") || status.phase().equals("ERROR")) {
                log("Task Coach: " + status.note() + ". Output: " + status.directory());
            }
        }
        if (detailed) {
            log("Task Coach " + status.phase() + ": " + status.task() + " — " + status.note());
            log("Checks: " + status.checks() + ", matched: " + status.hits()
                    + ", missed: " + status.misses() + ", evidence: " + status.captures()
                    + ", staged suggestions: " + status.proposals() + ", active repairs: " + status.repaired());
            log("Evidence directory: " + status.directory());
        }
    }

    private void populateAuditGroups() {
        TreeSet<String> groups = new TreeSet<>();
        for (File template : allTemplates) {
            String group = ScreenTemplateAudit.groupForTemplate(template.toPath());
            if (!group.equals("unclassified")) groups.add(group);
        }
        groups.add("world");
        auditGroupComboBox.setItems(FXCollections.observableArrayList(groups));
        if (groups.contains("home")) auditGroupComboBox.setValue("home");
    }

    @FXML
    private void handleSaveAuditScreen(ActionEvent event) {
        String group = auditGroupComboBox.getValue();
        if (currentScreenshotFile == null) {
            log("Capture an emulator screenshot before saving it to the screen library.");
            return;
        }
        if (group == null) {
            log("Select the screen group that ACTUALLY appears in the screenshot.");
            return;
        }
        try {
            Path root = WorkspacePaths.current().cache().resolve("template-screen-library");
            Path saved = ScreenTemplateAudit.save(root, group, currentScreenshotFile.toPath());
            log("Screen saved as " + group + " at " + saved + ". No screenshot is uploaded.");
            log("Capture a second independent screenshot of the SAME screen, and two other screen groups, "
                    + "to enable reliable positive/negative verification.");
        } catch (Exception ex) {
            log("Could not save labelled screen: " + ex.getMessage());
        }
    }

    @FXML
    private void handleAssignAuditTemplate(ActionEvent event) {
        File selected = templateListView.getSelectionModel().getSelectedItem();
        String group = auditGroupComboBox.getValue();
        if (selected == null || group == null) {
            log("Select both a template and the screen group where it SHOULD appear.");
            return;
        }
        String resource = ScreenTemplateAudit.resourceForTemplate(selected.toPath());
        try {
            Path root = WorkspacePaths.current().cache().resolve("template-screen-library");
            ScreenTemplateAudit.assign(root, resource, group);
            log("Expected context assigned: " + resource + " -> " + group
                    + ". Kept locally; it does NOT modify in-game recognition rules.");
        } catch (Exception ex) {
            log("Could not save expected screen: " + ex.getMessage());
        }
    }

    @FXML
    private void handleAuditLibrary(ActionEvent event) {
        if (btnRunAction.isDisabled()) {
            log("A screenshot scan is already running; finish it before auditing a group.");
            return;
        }
        String group = auditGroupComboBox.getValue();
        if (group == null || group.isBlank()) {
            log("Choose a screen group first.");
            return;
        }
        Path root = WorkspacePaths.current().cache().resolve("template-screen-library");
        final Map<String, List<Path>> screenGroups;
        try {
            screenGroups = ScreenTemplateAudit.loadScreens(root);
        } catch (Exception ex) {
            log("Could not load screenshot library: " + ex.getMessage());
            return;
        }
        List<Path> positives = screenGroups.getOrDefault(group, List.of());
        if (positives.size() < 2) {
            log("Group " + group + " has " + positives.size() + " saved screenshot(s). "
                    + "Capture two independent screenshots of this screen first.");
            return;
        }
        List<Path> negatives = ScreenTemplateAudit.negativeScreens(screenGroups, group);
        final Map<String, String> assignments;
        try {
            assignments = ScreenTemplateAudit.loadAssignments(root);
        } catch (java.io.IOException ex) {
            log("Could not load saved template assignments: " + ex.getMessage());
            return;
        }
        List<File> candidates = allTemplates.stream()
                .filter(file -> ScreenTemplateAudit.assignedGroup(file.toPath(), assignments).equals(group)).toList();
        if (candidates.isEmpty()) {
            log("No templates assigned to group " + group + ".");
            return;
        }
        log("Auditing " + candidates.size() + " " + group + " templates against "
                + positives.size() + " positive and " + negatives.size() + " other-screen captures.");
        btnAuditLibrary.setDisable(true);
        btnRunAction.setDisable(true);
        javafx.concurrent.Task<List<String>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<String> call() throws Exception {
                List<AuditFrame> positiveFrames = readAuditFrames(positives);
                List<AuditFrame> negativeFrames = readAuditFrames(negatives);
                List<String> report = new ArrayList<>();
                List<String> rows = new ArrayList<>();
                rows.add("group,template,positive_frames,positive_hits,negative_groups,negative_hits,best_positive_score,best_negative_score,status");
                java.util.EnumMap<ScreenTemplateAudit.Verdict, Integer> counts =
                        new java.util.EnumMap<>(ScreenTemplateAudit.Verdict.class);
                int num = 0;
                for (File template : candidates) {
                    if (isCancelled()) break;
                    num++;
                    String resource = template.getAbsolutePath().replace("\\", "/");
                    int index = resource.lastIndexOf("/templates/");
                    if (index < 0) {
                        report.add("ERROR " + template.getName() + ": unexpected template location");
                        continue;
                    }
                    resource = resource.substring(index);
                    java.awt.image.BufferedImage sprite = javax.imageio.ImageIO.read(template);
                    if (sprite == null || OpenCvPatternLocator.class.getResource(resource) == null) {
                        report.add("ERROR " + template.getName() + ": missing/invalid template resource");
                        continue;
                    }
                    List<ScreenTemplateAudit.Match> positiveScores = new ArrayList<>();
                    List<ScreenTemplateAudit.Match> negativeScores = new ArrayList<>();
                    for (AuditFrame frame : positiveFrames) {
                        positiveScores.add(auditMatch(frame, resource, sprite));
                    }
                    // Only probe other screen categories for potentially stable positives.
                    if (positiveScores.stream().filter(ScreenTemplateAudit.Match::strong).count() >= 2) {
                        for (AuditFrame frame : negativeFrames) {
                            negativeScores.add(auditMatch(frame, resource, sprite));
                        }
                    }
                    int posHits = (int) positiveScores.stream().filter(ScreenTemplateAudit.Match::strong).count();
                    int negHits = (int) negativeScores.stream().filter(ScreenTemplateAudit.Match::strong).count();
                    double bestPos = positiveScores.stream().mapToDouble(ScreenTemplateAudit.Match::score)
                            .filter(Double::isFinite).max().orElse(0);
                    double bestNeg = negativeScores.stream().mapToDouble(ScreenTemplateAudit.Match::score)
                            .filter(Double::isFinite).max().orElse(0);
                    ScreenTemplateAudit.Verdict verdict = ScreenTemplateAudit.classify(positiveScores,
                            negativeScores, negativeFrames.size(), sprite.getWidth(), sprite.getHeight());
                    verdict = ScreenTemplateAudit.restrictSharedGroup(group,
                            assignments.containsKey(resource), verdict);
                    counts.merge(verdict, 1, Integer::sum);
                    rows.add(ScreenTemplateAudit.csvEscape(group) + ","
                            + ScreenTemplateAudit.csvEscape(resource) + "," + positiveScores.size() + ","
                            + posHits + "," + negativeFrames.size() + "," + negHits + ","
                            + String.format(java.util.Locale.ROOT, "%.2f,%.2f,", bestPos, bestNeg)
                            + verdict.name());
                    if (verdict != ScreenTemplateAudit.Verdict.VERIFIED_CONTEXT) {
                        report.add(String.format(java.util.Locale.ROOT,
                                "%s %s (positive %d/%d, best %.1f%%; other screens %d/%d, max %.1f%%)",
                                verdict, resource, posHits, positiveScores.size(), bestPos,
                                negHits, negativeScores.size(), bestNeg));
                    }
                    if (num % 5 == 0) updateMessage("Group " + group + ": " + num + "/" + candidates.size());
                }
                Path reports = root.resolve("reports");
                Files.createDirectories(reports);
                String filename = "audit-" + group + "-" + java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
                Path csv = reports.resolve(filename);
                Files.writeString(csv, String.join("\n", rows) + "\n", StandardCharsets.UTF_8);
                report.add(0, "Context audit: " + group + " — " + counts + ". Full CSV: " + csv);
                report.add("A verified CONTEXT is not permission to click; actions still need their own confirmation.");
                return report;
            }
        };
        task.messageProperty().addListener((obs, before, after) -> log(after));
        task.setOnSucceeded(done -> {
            for (String line : task.getValue()) log(line);
            btnAuditLibrary.setDisable(false);
            btnRunAction.setDisable(false);
        });
        task.setOnFailed(failed -> {
            log("Screen library audit failed: " + task.getException());
            btnAuditLibrary.setDisable(false);
            btnRunAction.setDisable(false);
        });
        task.setOnCancelled(cancelled -> {
            btnAuditLibrary.setDisable(false);
            btnRunAction.setDisable(false);
        });
        Thread worker = new Thread(task, "survivalops-context-template-audit");
        worker.setDaemon(true);
        worker.start();
    }

    private record AuditFrame(byte[] png, int width, int height) {}

    private static List<AuditFrame> readAuditFrames(List<Path> screenshots) throws java.io.IOException {
        List<AuditFrame> frames = new ArrayList<>();
        for (Path path : screenshots) {
            byte[] png = Files.readAllBytes(path);
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(path.toFile());
            if (image == null) throw new java.io.IOException("Invalid stored screenshot: " + path);
            frames.add(new AuditFrame(png, image.getWidth(), image.getHeight()));
        }
        return frames;
    }

    private static ScreenTemplateAudit.Match auditMatch(AuditFrame frame, String resource,
                                                         java.awt.image.BufferedImage template) {
        if (template.getWidth() > frame.width() || template.getHeight() > frame.height()) {
            return new ScreenTemplateAudit.Match(0, -1, -1);
        }
        ImageSearchResultData matched = OpenCvPatternLocator.locatePattern(frame.png(), resource,
                new PointData(0, 0), new PointData(frame.width(), frame.height()), 0.0);
        if (matched == null || matched.getPoint() == null) {
            return new ScreenTemplateAudit.Match(matched == null ? 0 : matched.getMatchPercentage(), -1, -1);
        }
        return new ScreenTemplateAudit.Match(matched.getMatchPercentage(),
                matched.getPoint().getX(), matched.getPoint().getY());
    }

    private void setupActions() {
        actionComboBox.setItems(FXCollections.observableArrayList("Template Search", "OCR", "Scan Visible Templates"));
        actionComboBox.getSelectionModel().selectFirst();
        
        // pernerch/2026-07-02: Dynamic visibility control for template search UI elements
        // Template list and search field are only shown when "Template Search" action is selected
        // This prevents UI clutter when using OCR mode
        actionComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            boolean isTemplateSearch = "Template Search".equals(newValue)
                    || "Scan Visible Templates".equals(newValue);
            searchTextField.setVisible(isTemplateSearch);
            searchTextField.setManaged(isTemplateSearch);
            templateListView.setVisible(isTemplateSearch);
            templateListView.setManaged(isTemplateSearch);
        });
        
        // Initialize visibility based on initial selection
        boolean isTemplateSearch = "Template Search".equals(actionComboBox.getValue());
        searchTextField.setVisible(isTemplateSearch);
        searchTextField.setManaged(isTemplateSearch);
        templateListView.setVisible(isTemplateSearch);
        templateListView.setManaged(isTemplateSearch);
    }

    @FXML
    private void handleRunAction(ActionEvent event) {
        String action = actionComboBox.getValue();

        if ("OCR".equals(action)) {
            performOCROnSelection();
            return;
        }
        if ("Scan Visible Templates".equals(action)) {
            scanVisibleTemplates();
            return;
        }

        File template = templateListView.getSelectionModel().getSelectedItem();

        if (currentScreenshotFile == null) {
            log("Error: No screenshot loaded. Please drag and drop an image first.");
            return;
        }

        if (template == null) {
            log("Error: Please select a template from the list.");
            return;
        }

        log("Running action: " + action + " with template: " + template.getName());

        try {
            // Get relative path for OpenCvPatternLocator, e.g., /templates/home/petsButton.png
            String absPath = template.getAbsolutePath().replace("\\", "/");
            int templatesIndex = absPath.indexOf("/templates/");
            if (templatesIndex == -1) {
                log("Error: Selected template is not in a '/templates/' directory.");
                return;
            }
            String templateResourcePath = absPath.substring(templatesIndex);

            byte[] screenshotBytes = Files.readAllBytes(currentScreenshotFile.toPath());

            // Assuming we search the whole screenshot image
            Image img = screenshotImageView.getImage();
            int width = (int) img.getWidth();
            int height = (int) img.getHeight();

            ImageSearchResultData result = OpenCvPatternLocator.locatePattern(screenshotBytes, templateResourcePath,
                    new PointData(0, 0), new PointData(width, height), 70.0);

            if (result.isFound()) {
                log("Match Found! Coordinates: X=" + result.getPoint().getX() + ", Y=" + result.getPoint().getY()
                        + " (Accuracy: " + String.format("%.2f", result.getMatchPercentage()) + "%)");

                // Pin Crosshair at found location
                displayCrosshairAt((int) result.getPoint().getX(), (int) result.getPoint().getY());
            } else {
                log("No match found. Best Match: " + String.format("%.2f", result.getMatchPercentage()) + "%");
            }
        } catch (Exception e) {
            log("Error executing OpenCvPatternLocator: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private record TemplateAuditScore(String name, double score) {}

    /** Non-destructive screenshot audit: absence on one frame is NOT a stale template. */
    private void scanVisibleTemplates() {
        if (btnAuditLibrary.isDisabled()) {
            log("A screen-library audit is already running.");
            return;
        }
        if (currentScreenshotFile == null || screenshotImageView.getImage() == null) {
            log("Capture or load a screenshot before scanning templates.");
            return;
        }
        final File screenshot = currentScreenshotFile;
        final int width = (int) screenshotImageView.getImage().getWidth();
        final int height = (int) screenshotImageView.getImage().getHeight();
        final List<File> candidates = new ArrayList<>(templateListView.getItems());
        if (candidates.isEmpty()) {
            log("No templates in the current filter.");
            return;
        }
        btnRunAction.setDisable(true);
        btnAuditLibrary.setDisable(true);
        log("Scanning " + candidates.size() + " templates on one screenshot (no taps)...");
        javafx.concurrent.Task<List<String>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<String> call() throws Exception {
                byte[] capture = Files.readAllBytes(screenshot.toPath());
                List<String> report = new ArrayList<>();
                List<String> failures = new ArrayList<>();
                List<TemplateAuditScore> scores = new ArrayList<>();
                int checked = 0;
                int skipped = 0;
                for (File template : candidates) {
                    if (isCancelled()) break;
                    String path = template.getAbsolutePath().replace("\\", "/");
                    int marker = path.indexOf("/templates/");
                    if (marker < 0) {
                        failures.add(template.getName() + ": not under templates directory");
                        skipped++;
                        continue;
                    }
                    try {
                        String resource = path.substring(marker);
                        if (OpenCvPatternLocator.class.getResource(resource) == null) {
                            failures.add(template.getName() + ": missing bundled resource " + resource);
                            checked++;
                            continue;
                        }
                        java.awt.image.BufferedImage sprite = javax.imageio.ImageIO.read(template);
                        if (sprite == null) {
                            failures.add(template.getName() + ": unreadable image");
                            checked++;
                            continue;
                        }
                        if (sprite.getWidth() > width || sprite.getHeight() > height) {
                            failures.add(template.getName() + ": larger than screenshot");
                            checked++;
                            continue;
                        }
                        ImageSearchResultData result = OpenCvPatternLocator.locatePattern(
                                capture, resource, new PointData(0, 0),
                                new PointData(width, height), 0.0);
                        if (result == null || !Double.isFinite(result.getMatchPercentage())) {
                            failures.add(template.getName() + ": no valid score");
                        } else {
                            scores.add(new TemplateAuditScore(template.getName(),
                                    result.getMatchPercentage()));
                        }
                    } catch (RuntimeException ex) {
                        failures.add(template.getName() + ": " + ex.getClass().getSimpleName()
                                + " - " + ex.getMessage());
                    }
                    checked++;
                    if (checked % 25 == 0) updateMessage("Scanned " + checked + "/" + candidates.size());
                }
                scores.sort((left, right) -> Double.compare(right.score(), left.score()));
                long strong = scores.stream().filter(score -> score.score() >= 90).count();
                long possible = scores.stream().filter(score -> score.score() >= 70 && score.score() < 90).count();
                report.add("Scan complete: " + checked + " checked, " + strong
                        + " strong (90%+), " + possible + " possible (70-90%), "
                        + failures.size() + " errors, " + skipped + " skipped.");
                report.add("Best 30 scores (below 90% is NOT a verified match):");
                for (int i = 0; i < Math.min(30, scores.size()); i++) {
                    TemplateAuditScore score = scores.get(i);
                    report.add(String.format(java.util.Locale.ROOT, "  %.1f%% %s",
                            score.score(), score.name()));
                }
                for (String failure : failures) report.add("ERROR " + failure);
                report.add("Scores are for this screenshot only; missing elements do not imply stale assets.");
                return report;
            }
        };
        task.messageProperty().addListener((obs, oldValue, newValue) -> log(newValue));
        task.setOnSucceeded(event -> {
            for (String line : task.getValue()) log(line);
            btnRunAction.setDisable(false);
            btnAuditLibrary.setDisable(false);
        });
        task.setOnFailed(event -> {
            log("Template scan failed: " + task.getException());
            btnRunAction.setDisable(false);
            btnAuditLibrary.setDisable(false);
        });
        task.setOnCancelled(event -> {
            btnRunAction.setDisable(false);
            btnAuditLibrary.setDisable(false);
        });
        Thread worker = new Thread(task, "survivalops-template-audit");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean isCrosshairLocked = false;

    private void displayCrosshairAt(int imgX, int imgY) {
        if (screenshotImageView.getImage() == null || imageContainer == null)
            return;

        Image screenshotImage = screenshotImageView.getImage();

        double viewWidth = 316.0; // matched fitWidth
        double viewHeight = 576.0; // matched fitHeight

        // Offset logic - imageContainer vs clip
        double offsetX = (imageContainer.getWidth() - viewWidth) / 2.0;
        double offsetY = (imageContainer.getHeight() - viewHeight) / 2.0;

        double origWidth = screenshotImage.getWidth();
        double origHeight = screenshotImage.getHeight();

        double relX = (imgX / origWidth) * viewWidth;
        double relY = (imgY / origHeight) * viewHeight;

        double uiX = relX + offsetX;
        double uiY = relY + offsetY;

        // Force crosshair lines onto UI coordinate position
        crosshairX.setStartX(0);
        crosshairX.setEndX(imageContainer.getWidth());
        crosshairX.setStartY(uiY);
        crosshairX.setEndY(uiY);

        crosshairY.setStartX(uiX);
        crosshairY.setEndX(uiX);
        crosshairY.setStartY(0);
        crosshairY.setEndY(imageContainer.getHeight());

        coordinatesLabel.setText(String.format("X: %d\nY: %d", imgX, imgY));

        // Keep label within bounds
        double labelX = uiX + 15;
        double labelY = uiY + 15;
        if (labelX + coordinatesLabel.getWidth() > imageContainer.getWidth()) {
            labelX = uiX - 15 - coordinatesLabel.getWidth();
        }
        if (labelY + coordinatesLabel.getHeight() > imageContainer.getHeight()) {
            labelY = uiY - 15 - coordinatesLabel.getHeight();
        }

        coordinatesLabel.setLayoutX(labelX);
        coordinatesLabel.setLayoutY(labelY);

        crosshairPane.setVisible(true);
        crosshairX.setVisible(true);
        crosshairY.setVisible(true);
        coordinatesLabel.setVisible(true);

        isCrosshairLocked = true; // Block hover from hiding immediately
    }

    private void performOCROnSelection() {
        if (currentScreenshotFile == null || screenshotImageView.getImage() == null) {
            log("Error: No screenshot loaded.");
            return;
        }
        if (selectionBox.getWidth() <= 0 || selectionBox.getHeight() <= 0 || !selectionBox.isVisible()) {
            log("Error: No region selected. Please click and drag to select a region.");
            return;
        }

        double viewWidth = 316.0;
        double viewHeight = 576.0;

        double offsetX = (imageContainer.getWidth() - viewWidth) / 2.0;
        double offsetY = (imageContainer.getHeight() - viewHeight) / 2.0;

        Image img = screenshotImageView.getImage();
        double origWidth = img.getWidth();
        double origHeight = img.getHeight();

        double relStartX = selectionBox.getX() - offsetX;
        double relStartY = selectionBox.getY() - offsetY;

        int imgStartX = (int) ((relStartX / viewWidth) * origWidth);
        int imgStartY = (int) ((relStartY / viewHeight) * origHeight);

        double relEndX = (selectionBox.getX() + selectionBox.getWidth()) - offsetX;
        double relEndY = (selectionBox.getY() + selectionBox.getHeight()) - offsetY;

        int imgEndX = (int) ((relEndX / viewWidth) * origWidth);
        int imgEndY = (int) ((relEndY / viewHeight) * origHeight);

        imgStartX = Math.max(0, Math.min(imgStartX, (int) origWidth - 1));
        imgStartY = Math.max(0, Math.min(imgStartY, (int) origHeight - 1));
        imgEndX = Math.max(0, Math.min(imgEndX, (int) origWidth - 1));
        imgEndY = Math.max(0, Math.min(imgEndY, (int) origHeight - 1));

        int width = imgEndX - imgStartX;
        int height = imgEndY - imgStartY;

        if (width <= 0 || height <= 0) {
            log("Invalid region selected for OCR.");
            return;
        }

        log(String.format("Performing OCR on Region: TopLeft(%d, %d), BottomRight(%d, %d)", imgStartX, imgStartY,
                imgEndX, imgEndY));

        try {
            String result = OcrEngine.readFromFile(currentScreenshotFile, imgStartX, imgStartY,
                    width, height, "eng");
            log("OCR Output:\n" + result);
        } catch (Exception e) {
            log("OCR Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleCaptureAdb(ActionEvent event) {
        AccountDescriptor selected = profileComboBox.getValue();
        if (selected == null) {
            log("Error: Please select a profile first.");
            return;
        }

        Thread captureThread = new Thread(() -> {
            javafx.application.Platform.runLater(() -> {
                btnCaptureAdb.setDisable(true);
                log("Capturing screenshot from " + selected.getName() + " (Emulator " + selected.getEmulatorNumber()
                        + ")...");
            });

            try {
                // Initialize backend if this is run independently before the engine starts
                try {
                    dev.frostguard.engine.emulator.EmulatorController.getInstance().captureScreen(selected.getEmulatorNumber());
                } catch (IllegalStateException e) {
                    if (e.getMessage().contains("Backend not initialised")) {
                        dev.frostguard.engine.emulator.EmulatorController.getInstance().initialize();
                    }
                }

                // We use EmulatorController to get the raw image dynamically
                dev.frostguard.api.domain.RawImageData rawImage = dev.frostguard.engine.emulator.EmulatorController.getInstance()
                        .captureScreen(selected.getEmulatorNumber());

                if (rawImage != null) {
                    java.awt.image.BufferedImage bImg = dev.frostguard.vision.convert.ImageConverter.toBufferedImage(rawImage);

                    File tempFile = File.createTempFile("adb_screenshot", ".png");
                    javax.imageio.ImageIO.write(bImg, "png", tempFile);

                    javafx.application.Platform.runLater(() -> {
                        log("Success: Saved to temporary file " + tempFile.getAbsolutePath());
                        loadScreenshot(tempFile);
                    });
                } else {
                    javafx.application.Platform.runLater(() -> {
                        log("Error: Failed to capture ADB image.");
                    });
                }
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    log("Adb Capture Error: " + e.getMessage());
                    e.printStackTrace();
                });
            } finally {
                javafx.application.Platform.runLater(() -> {
                    btnCaptureAdb.setDisable(false);
                });
            }
        });
        captureThread.start();
    }

    private void log(String message) {
        logTextArea.appendText(message + "\n");
    }
}
