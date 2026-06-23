/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.entity.Event;
import com.codeferm.alarmbian.image.MatToBufImg;
import com.formdev.flatlaf.util.StringUtils;
import de.milchreis.uibooster.UiBooster;
import de.milchreis.uibooster.model.Form;
import de.milchreis.uibooster.model.FormElement;
import de.milchreis.uibooster.model.FormElementChangeListener;
import de.milchreis.uibooster.model.UiBoosterOptions;
import de.milchreis.uibooster.model.formelements.TextFormElement;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

/**
 * Consolidated multi-camera Alarmbian player UI based on UI Booster. Handles path normalization patterns to robustly translate
 * hardware and SMTP records, backed by defensive index boundaries for multi-day soak tests.
 *
 * @author Steven P. Goldsmith
 * @version 1.1.5
 * @since 1.0.0
 */
@Component
@Slf4j
@Command(name = "playUI")
public class PlayUI implements Callable<Integer>, FormElementChangeListener {

    /**
     * Play logic instance orchestration service.
     */
    @Autowired
    private Play play;

    /**
     * Spring environment resource context for dynamic workspace property resolution.
     */
    @Autowired
    private Environment env;

    /**
     * UI Booster frame controller rendering engine instance.
     */
    private final UiBooster booster;

    /**
     * Image timeline list index tracking reference identifier.
     */
    private int index = 0;

    /**
     * List of collated motion start/stop and history stop sequence arrays.
     */
    private List<List<Event>> images;

    /**
     * Map of start buffer events keyed by source absolute file name tokens.
     */
    private Map<String, Event> buffers;

    /**
     * Map of human-readable timestamps mapping cleanly to event index positions.
     */
    private Map<String, Integer> timestamps;

    /**
     * List of human readable timestamps utilized for select drop-down choices.
     */
    private List<String> elements;

    /**
     * Ordered list holding the names of all active configured system hardware devices.
     */
    @Value("#{'${devices.list}'.split(',')}")
    private List<String> devicesList;

    /**
     * Currently active target camera configuration block name identifier.
     */
    private String currentDeviceName;

    /**
     * Database origin remote directory path token prefix matches.
     */
    private String remoteFromPath;

    /**
     * System local target absolute directory mount map workspace coordinates.
     */
    private String remoteToPath;

    /**
     * Local extraction save workspace path directory destination pointer.
     */
    @Value("${localPath}")
    private String localPath;

    /**
     * Play before event margin padding values represented in seconds.
     */
    @Value("${playBefore}")
    private Integer playBefore;

    /**
     * Play after event margin trailing padding values represented in seconds.
     */
    @Value("${playAfter}")
    private Integer playAfter;

    /**
     * Preview image frame X coordinate limit resolution aspect boundary.
     */
    @Value("${xMax}")
    private Integer xMax;

    /**
     * Preview image frame Y coordinate limit resolution aspect boundary.
     */
    @Value("${yMax}")
    private Integer yMax;

    /**
     * Native utility wrapper used to convert openCV Mat matrix files into buffered images.
     */
    private final MatToBufImg matToBufImg;

    /**
     * Source matrix image reference placeholder frame structure for OpenCV.
     */
    private Mat source;

    /**
     * Target matrix scaled destination frame placeholder layout for OpenCV image transformation.
     */
    private final Mat dest;

    /**
     * Target event type constraint state tracking flag identifier.
     */
    private String currentEventType;

    /**
     * Main UI display frame constructor setting standardized fonts and graphics flags.
     */
    public PlayUI() {
        final var exampleFontSettings = new FontUIResource(new Font("MS Mincho", Font.PLAIN, 20));
        final var keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            final var key = keys.nextElement();
            final var value = UIManager.get(key);
            if (value instanceof javax.swing.plaf.FontUIResource) {
                UIManager.put(key, exampleFontSettings);
            }
        }
        booster = new UiBooster(UiBoosterOptions.Theme.SWING);
        matToBufImg = new MatToBufImg();
        matToBufImg.init();
        source = null;
        dest = new Mat();
    }

    /**
     * Dynamically loads environmental path parameters for the chosen active device camera workspace.
     *
     * @param targetCamera The system reference key string matching target configurations.
     */
    private void activateCameraWorkspace(final String targetCamera) {
        this.currentDeviceName = targetCamera;
        this.remoteFromPath = env.getProperty(targetCamera + ".remoteFromPath");
        this.remoteToPath = env.getProperty(targetCamera + ".remoteToPath");

        log.info("Workspace activated: device={}, paths={} -> {}", currentDeviceName, remoteFromPath, remoteToPath);
        play.setDeviceName(targetCamera);
    }

    /**
     * Translates and normalizes file paths using regex routines to clean double-slashes common inside raw IP camera logging
     * outputs.
     *
     * @param rawPath The original unmapped asset string location from storage metadata.
     * @return Normalized and fully qualified target location matching system disk paths.
     */
    private String translatePath(final String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "";
        }
        var sanitized = rawPath.replaceAll("/{2,}", "/");

        if (remoteFromPath != null && !remoteFromPath.isBlank() && remoteToPath != null) {
            sanitized = sanitized.replace(remoteFromPath, remoteToPath);
        }

        return sanitized.replaceAll("/{2,}", "/");
    }

    /**
     * Sync data model maps and state metrics with target database storage configurations.
     */
    public void refresh() {
        buffers = play.loadMotionBuffers();

        if (currentEventType == null && play.getSmtpUiTypes() != null && !play.getSmtpUiTypes().isEmpty()) {
            currentEventType = play.getSmtpUiTypes().get(0);
        }

        if ("Motion".equalsIgnoreCase(currentEventType)) {
            images = play.loadMotionEvents();
        } else {
            images = play.loadSmtpMotionEvents(currentEventType);
        }

        elements = new ArrayList<>();
        timestamps = new HashMap<>();
        var i = 0;
        for (final var image : images) {
            timestamps.put(play.formatTimestamp(image.get(0).getEventTime()), i++);
        }
        for (i = images.size(); i-- > 0;) {
            elements.add(play.formatTimestamp(images.get(i).get(0).getEventTime()));
        }

        // Safety Guard: Force index inside the ceiling boundary when structural list sizes mutate
        index = images.isEmpty() ? 0 : images.size() - 1;
    }

    /**
     * Initial startup controller managing graphical window rendering contexts.
     *
     * @return Execution result tracking confirmation code.
     * @throws Exception Mapping execution handling channel issues.
     */
    @Override
    public Integer call() throws Exception {
        if (devicesList == null || devicesList.isEmpty()) {
            throw new IllegalStateException("The devices.list configuration entry cannot be blank or missing.");
        }

        activateCameraWorkspace(devicesList.get(0));

        final var dialog = booster.showWaitingDialog("Operation", currentDeviceName);
        dialog.addToLargeMessage("Refresh data");
        refresh();
        dialog.close();

        final var initialIndex = images.isEmpty() ? 0 : images.size() - 1;
        final var initialPreviewFile = images.isEmpty() ? "" : translatePath(images.get(initialIndex).get(1).getEventData());

        booster.createForm("Alarmbian Multi-Cam Console").
                addCustomElement(new IconFormElement(getImageIcon(null, initialPreviewFile))).
                setID("image").
                startRow().
                addSelection("Active Camera", devicesList).setID("activeCamera").
                addSelection("Events", elements).setID("events").
                addText("Duration", images.isEmpty() ? "00:00:00" : play.formatDuration(images.get(initialIndex).get(0).getEventTime(), images.get(initialIndex).get(2).getEventTime()), true).setID("duration").
                addText("Before", String.valueOf(playBefore)).setID("before").
                addText("After", String.valueOf(playAfter)).setID("after").
                addSelection("Event Type", play.getSmtpUiTypes()).setID("eventType").
                endRow().
                startRow().
                addButton("Play", () -> {
                }).setID("play").
                addButton("Save", () -> {
                }).setID("save").
                addButton("Refresh", () -> {
                }).setID("refresh").
                endRow().
                andWindow().setSize(xMax + 40, yMax + 310).save().
                setChangeListener(this).show();

        matToBufImg.done();
        return 0;
    }

    /**
     * Map target video files or image frames into valid Swing Icon components.
     *
     * @param form The active parent UI booster layout context reference.
     * @param fileName The absolute path pointing to targeted disk image assets.
     * @return Formatted Icon asset ready for layout rendering panels.
     */
    public ImageIcon getImageIcon(final Form form, final String fileName) {
        if (fileName == null || fileName.isBlank() || !(new File(fileName).exists())) {
            log.warn("Target preview layout asset missing on disk: {}", fileName);
            return new ImageIcon(new BufferedImage(xMax, yMax, BufferedImage.TYPE_3BYTE_BGR));
        }
        var imageIcon = (ImageIcon) null;
        source = Imgcodecs.imread(fileName);
        var type = BufferedImage.TYPE_BYTE_GRAY;
        if (source.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        if (source.cols() > xMax) {
            Imgproc.resize(source, dest, new Size(xMax, yMax), 0, 0, Imgproc.INTER_LINEAR);
            source.release();
            imageIcon = new ImageIcon(matToBufImg.execute(dest));
            dest.release();
        } else {
            imageIcon = new ImageIcon(matToBufImg.execute(source));
            source.release();
        }
        return imageIcon;
    }

    /**
     * Refresh text layout parameter components tracking tracking metrics.
     *
     * @param form Base container tracking structural frame layouts.
     */
    public void update(final Form form) {
        if (!images.isEmpty() && index < images.size()) {
            final var duration = (TextFormElement) form.getById("duration");
            duration.setValue(play.formatDuration(images.get(index).get(0).getEventTime(), images.get(index).get(2).getEventTime()));
        }
    }

    /**
     * Process chunk extractions through an external localized FFmpeg pipeline context execution loop.
     *
     * @param fileName Source video file location.
     * @param start Execution timing offset baseline.
     * @param duration Interval length segment tracking variable.
     * @param outputFileName Target destination coordinate assignment mapping on system storage.
     */
    public void saveFile(final String fileName, final long start, final long duration, final String outputFileName) {
        final var command = new ArrayList<String>();
        command.add("ffmpeg");
        command.add("-ss");
        command.add(String.valueOf(start));
        command.add("-i");
        command.add(fileName);
        command.add("-t");
        command.add(String.valueOf(duration));
        command.add("-c");
        command.add("copy");
        command.add(outputFileName);
        final var pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        try {
            final var pc = pb.start();
            try (final var inputStatus = pc.getInputStream(); final var readStatus = new BufferedReader(new InputStreamReader(inputStatus))) {
                while (readStatus.readLine() != null) {
                    // Drain native buffers completely
                }
            }
            try {
                pc.waitFor();
                pc.destroy();
                log.debug("File saved successfully to {}", outputFileName);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Process tracking context execution failure interrupted", e);
            }
        } catch (final IOException e) {
            throw new RuntimeException("Failed to run local structural process engine clip commands", e);
        }
    }

    /**
     * Helper formatting metric tracking temporal values inside playback parameter strings.
     *
     * @param totalSeconds Raw baseline timestamp value metrics.
     * @return Normalized string representation tracking element parameters.
     */
    private String formatAbsoluteTime(final long totalSeconds) {
        final var positiveSeconds = Math.max(0, totalSeconds);
        final var hours = positiveSeconds / 3600;
        final var minutes = (positiveSeconds % 3600) / 60;
        final var seconds = positiveSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Triggers active FFplay rendering environments utilizing prioritized real-time performance constraints.
     *
     * @param fileName Targeted raw track layout file asset destination location.
     * @param start Numeric timestamp offset metric track index entry.
     * @param duration Sequence length scope evaluation window bounds.
     */
    public void playFile(final String fileName, final long start, final long duration) {
        final var seekStr = formatAbsoluteTime(start);
        final var durationStr = formatAbsoluteTime(duration);

        if (log.isDebugEnabled()) {
            log.debug("""
                     PlayUI Diagnostics Execution Context:
                       Target File: {}
                       Raw Seconds: Start={}, Duration={}
                       Seek String: {} (-ss)
                       Clip Length: {} (-t)
                       File Exists: {}""",
                    fileName, start, duration, seekStr, durationStr, new File(fileName).exists());
        }

        final var command = new ArrayList<String>();
        command.add("ffplay");
        command.add("-autoexit");
        command.add("-window_title");
        command.add("Alarmbian Event Playback View: " + currentDeviceName);

        command.add("-fflags");
        command.add("+nobuffer+fastseek");
        command.add("-probesize");
        command.add("32");
        command.add("-analyzeduration");
        command.add("0");

        command.add("-bytes");
        command.add("1");

        command.add("-ss");
        command.add(seekStr);

        command.add("-i");
        command.add(fileName);

        command.add("-t");
        command.add(durationStr);

        command.add("-sn");
        command.add("-sync");
        command.add("video");
        command.add("-framedrop");
        command.add("-infbuf");

        final var pb = new ProcessBuilder(command);
        pb.inheritIO();

        try {
            log.info("Spawning native ffplay instance for file: {}", fileName);
            final var pc = pb.start();
            try {
                final var exitCode = pc.waitFor();
                log.debug("Native ffplay execution terminated with exit code: {}", exitCode);
                pc.destroy();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Playback tracking thread was interrupted natively", e);
                throw new RuntimeException("Playback tracking thread was interrupted natively", e);
            }
        } catch (final IOException e) {
            log.error("Failed to initialize system execution pipeline for ffplay target: {}", fileName, e);
            throw new RuntimeException("Failed to initialize system execution pipeline for ffplay", e);
        }
    }

    /**
     * Standard string digit parsing structural validation verification method.
     *
     * @param str Raw string characters.
     * @return Validation status confirmation true if formatting matches valid numerical ranges.
     */
    public boolean isInteger(final String str) {
        try {
            Integer.valueOf(str);
            return true;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    /**
     * Value mutation trigger handling routing managing form state alterations defensively.
     *
     * @param fe Active target interface component reference token tracker.
     * @param o Evaluated value transformation wrapper mapping parameters.
     * @param form Current parent display container structural element reference tracker.
     */
    @Override
    public void onChange(final FormElement fe, final Object o, final Form form) {
        final var label = (JLabel) form.getById("image").getValue();
        switch (fe.getId()) {
            case "activeCamera" -> {
                final var selectedCam = (String) o;
                if (selectedCam != null && !selectedCam.isBlank()) {
                    activateCameraWorkspace(selectedCam.trim());
                    refresh();

                    final var selection = form.getById("events").toSelection();
                    selection.setPossibilities(elements);

                    if (!images.isEmpty()) {
                        index = images.size() - 1;
                        label.setIcon(getImageIcon(form, translatePath(images.get(index).get(1).getEventData())));
                    } else {
                        label.setIcon(getImageIcon(form, null));
                        final var duration = (TextFormElement) form.getById("duration");
                        duration.setValue("00:00:00");
                    }
                }
            }
            case "play" -> {
                if (images.isEmpty() || index >= images.size()) {
                    break;
                }
                final var bufferStart = buffers.get(images.get(index).get(0).getEventData()).getEventTime().toInstant();
                final var motionStart = images.get(index).get(0).getEventTime().toInstant();
                final var motionStop = images.get(index).get(2).getEventTime().toInstant();

                var startSeconds = Duration.between(bufferStart, motionStart).minusSeconds(playBefore).getSeconds();
                if (startSeconds < 0) {
                    startSeconds = 0;
                }

                final var duration = Duration.between(motionStart, motionStop).plusSeconds(playAfter);
                final var fileName = translatePath(images.get(index).get(0).getEventData());

                playFile(fileName, startSeconds, duration.getSeconds());
            }
            case "save" -> {
                if (images.isEmpty() || index >= images.size()) {
                    break;
                }
                final var bufferStart = buffers.get(images.get(index).get(0).getEventData()).getEventTime().toInstant();
                final var motionStart = images.get(index).get(0).getEventTime().toInstant();
                final var motionStop = images.get(index).get(2).getEventTime().toInstant();

                var startSeconds = Duration.between(bufferStart, motionStart).minusSeconds(playBefore).getSeconds();
                if (startSeconds < 0) {
                    startSeconds = 0;
                }

                final var duration = Duration.between(motionStart, motionStop).plusSeconds(playAfter);
                final var fileName = translatePath(images.get(index).get(0).getEventData());
                final var file = new File(images.get(index).get(1).getEventData().replace("jpg", "mkv"));
                final var saveFileName = file.getName();

                saveFile(fileName, startSeconds, duration.getSeconds(), String.format("%s%s%s", localPath,
                        File.separator, saveFileName));
            }
            case "events" -> {
                final var value = (String) form.getById("events").getValue();
                if (!StringUtils.isEmpty(value) && timestamps.containsKey(value)) {
                    final var targetIdx = timestamps.get(value);
                    if (targetIdx < images.size()) {
                        index = targetIdx;
                        label.setIcon(getImageIcon(form, translatePath(images.get(index).get(1).getEventData())));
                    }
                }
            }
            case "eventType" -> {
                final var selectedType = (String) o;
                if (selectedType != null && !selectedType.isBlank()) {
                    currentEventType = selectedType.trim();
                    refresh();
                    final var selection = form.getById("events").toSelection();
                    selection.setPossibilities(elements);

                    if (!images.isEmpty()) {
                        index = images.size() - 1;
                        label.setIcon(getImageIcon(form, translatePath(images.get(index).get(1).getEventData())));
                    } else {
                        label.setIcon(getImageIcon(form, null));
                    }
                }
            }
            case "refresh" -> {
                refresh();
                final var selection = form.getById("events").toSelection();
                selection.setPossibilities(elements);
            }
            case "duration" -> {
            }
            case "before" -> {
                final var before = form.getById("before").asString();
                if (before != null && !before.isEmpty()) {
                    if (isInteger(before)) {
                        playBefore = Integer.valueOf(before);
                    } else {
                        new UiBooster().showErrorDialog("Only integer values allowed.", "ERROR");
                    }
                }
            }
            case "after" -> {
                final var after = form.getById("after").asString();
                if (after != null && !after.isEmpty()) {
                    if (isInteger(after)) {
                        playAfter = Integer.valueOf(after);
                    } else {
                        new UiBooster().showErrorDialog("Only integer values allowed.", "ERROR");
                    }
                }
            }
            default ->
                booster.showErrorDialog(String.format("%s onChange not handled", fe.getId()), "Error");
        }
        update(form);
    }
}
