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
import de.milchreis.uibooster.model.formelements.SelectionFormElement;
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
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

/**
 * Alarmbian player UI based on UI Booster.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Slf4j
@Command(name = "playUI")
public class PlayUI implements Callable<Integer>, FormElementChangeListener {

    /**
     * Play logic.
     */
    @Autowired
    private Play play;
    /**
     * UI Booster.
     */
    private final UiBooster booster;
    /**
     * Image index.
     */
    private int index = 0;
    /**
     * List of motion start/stop and history stop.
     */
    private List<List<Event>> images;
    /**
     * Map of start buffer events by file name.
     */
    private Map<String, Event> buffers;
    /**
     * Map of timestamps to index.
     */
    private Map<String, Integer> timestamps;
    /**
     * Human readable timestamps.
     */
    private List<String> elements;
    /**
     * Remote from path.
     */
    @Value("${remoteFromPath}")
    private String remoteFromPath;
    /**
     * Remote to path.
     */
    @Value("${remoteToPath}")
    private String remoteToPath;
    /**
     * Local path.
     */
    @Value("${localPath}")
    private String localPath;
    /**
     * Play before event in seconds.
     */
    @Value("${playBefore}")
    private Integer playBefore;
    /**
     * Play after event in seconds.
     */
    @Value("${playAfter}")
    private Integer playAfter;
    /**
     * Preview X maximum.
     */
    @Value("${xMax}")
    private Integer xMax;
    /**
     * Preview Y maximum.
     */
    @Value("${yMax}")
    private Integer yMax;
    /**
     * Convert Mat to BufferedImage.
     */
    private final MatToBufImg matToBufImg;
    /**
     * Source Mat.
     */
    private Mat source;
    /**
     * Destination Mat.
     */
    private final Mat dest;
    /**
     * Target SQL relation field constraint variable state tracking selection parameters.
     */
    private String currentEventType;

    /**
     * Set font globally.
     */
    public PlayUI() {
        final FontUIResource exampleFontSettings = new FontUIResource(new Font("MS Mincho", Font.PLAIN, 20));
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
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
     * Refresh from database using modern parameterized set-algebra rules.
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
        // Build Map of timestamps to image index.
        for (final var image : images) {
            timestamps.put(play.formatTimestamp(image.get(0).getEventTime()), i++);
        }
        // Build list of timestamps
        for (i = images.size(); i-- > 0;) {
            elements.add(play.formatTimestamp(images.get(i).get(0).getEventTime()));
        }
        index = images.isEmpty() ? 0 : images.size() - 1;
    }

    /**
     * Blocking call until OK button pressed.
     *
     * @return Execution confirmation code.
     * @throws Exception Possible exception.
     */
    @Override
    public Integer call() throws Exception {
        final var dialog = booster.showWaitingDialog("Operation", play.getDeviceName());
        dialog.addToLargeMessage("Refresh data");
        refresh();
        dialog.close();

        final var initialIndex = images.isEmpty() ? 0 : images.size() - 1;
        final var initialPreviewFile = images.isEmpty() ? "" : images.get(initialIndex).get(1).getEventData().replace(remoteFromPath, remoteToPath);

        booster.createForm(play.getDeviceName()).
                addCustomElement(new IconFormElement(getImageIcon(null, initialPreviewFile))).
                setID("image").
                startRow().
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

        // Clean up
        matToBufImg.done();
        return 0;
    }

    /**
     * Return image icon from image file.
     *
     * @param form Form to update.
     * @param fileName Image file name.
     * @return Image icon.
     */
    public ImageIcon getImageIcon(final Form form, final String fileName) {
        if (fileName == null || fileName.isBlank() || !(new File(fileName).exists())) {
            return new ImageIcon(new BufferedImage(xMax, yMax, BufferedImage.TYPE_3BYTE_BGR));
        }
        ImageIcon imageIcon = null;
        // Get preview image
        source = Imgcodecs.imread(fileName);
        var type = BufferedImage.TYPE_BYTE_GRAY;
        if (source.channels() > 1) {
            // If it's a color image (3 channels), assume BGR and use TYPE_3BYTE_BGR
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        // Resize if needed
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
     * Update form elements.
     *
     * @param form Form to update.
     */
    public void update(final Form form) {
        if (!images.isEmpty()) {
            var duration = (TextFormElement) form.getById("duration");
            duration.setValue(play.formatDuration(images.get(index).get(0).getEventTime(), images.get(index).get(2).getEventTime()));
        }
    }

    /**
     * Use ffmpeg to save file clip.
     *
     * * @param fileName Video buffer file.
     * @param start Start offset.
     * @param duration Duration in seconds.
     * @param outputFileName Output file name.
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
                    // Consume FFmpeg output/status (optional)
                }
            }
            try {
                pc.waitFor();
                pc.destroy();
                log.debug("File saved successfully to {}", outputFileName);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Best practice for interrupted exception
                throw new RuntimeException("Process interrupted", e);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to execute FFmpeg command", e);
        }
    }

    /**
     * Use ffplay to play motion from buffer using start and duration.
     *
     * @param fileName Video buffer file.
     * @param start Start offset.
     * @param duration Duration in seconds.
     */
    public void playFile(final String fileName, final long start, final long duration) {
        final var command = new ArrayList<String>();
        command.add("ffplay");
        command.add("-autoexit");
        command.add("-fflags");
        command.add("nobuffer");
        command.add("-analyzeduration");
        command.add("0");
        command.add("-probesize");
        command.add("32");
        command.add(fileName);
        command.add("-ss");
        command.add(String.valueOf(start));
        command.add("-t");
        command.add(String.valueOf(duration));
        final var pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        try {
            final var pc = pb.start();
            try (final var inputStatus = pc.getInputStream(); final var readStatus = new BufferedReader(new InputStreamReader(inputStatus))) {
                while (readStatus.readLine() != null) {
                }
            }
            try {
                pc.waitFor();
                pc.destroy();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Poor man's Integer validator.
     *
     * @param str String value.
     * @return true if integer.
     */
    public boolean isInteger(String str) {
        try {
            Integer.valueOf(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * onChange listener for form elements.
     *
     * @param fe Form element.
     * @param o Object.
     * @param form Form.
     */
    @Override
    public void onChange(final FormElement fe, final Object o, final Form form) {
        final var label = (JLabel) form.getById("image").getValue();
        switch (fe.getId()) {
            case "play" -> {
                if (!images.isEmpty()) {
                    final var bufferStart = buffers.get(images.get(index).get(0).getEventData()).getEventTime().toInstant();
                    final var motionStart = images.get(index).get(0).getEventTime().toInstant();
                    final var motionStop = images.get(index).get(2).getEventTime().toInstant();
                    final var start = Duration.between(bufferStart, motionStart).minusSeconds(playBefore);
                    final var duration = Duration.between(motionStart, motionStop).plusSeconds(playAfter);
                    final var fileName = images.get(index).get(0).getEventData().replace(remoteFromPath, remoteToPath);
                    playFile(fileName, start.getSeconds(), duration.getSeconds());
                }
            }
            case "save" -> {
                if (!images.isEmpty()) {
                    final var bufferStart = buffers.get(images.get(index).get(0).getEventData()).getEventTime().toInstant();
                    final var motionStart = images.get(index).get(0).getEventTime().toInstant();
                    final var motionStop = images.get(index).get(2).getEventTime().toInstant();
                    final var start = Duration.between(bufferStart, motionStart).minusSeconds(playBefore);
                    final var duration = Duration.between(motionStart, motionStop).plusSeconds(playAfter);
                    final var fileName = images.get(index).get(0).getEventData().replace(remoteFromPath, remoteToPath);
                    var file = new File(images.get(index).get(1).getEventData().replace("jpg", "mkv"));
                    var saveFileName = file.getName();
                    saveFile(fileName, start.getSeconds(), duration.getSeconds(), String.format("%s%s%s", localPath,
                            File.separator, saveFileName));
                }
            }
            case "events" -> {
                var value = (String) form.getById("events").getValue();
                if (!StringUtils.isEmpty(value) && timestamps.containsKey(value)) {
                    index = timestamps.get(value);
                    label.setIcon(getImageIcon(form, images.get(index).get(1).getEventData().replace(remoteFromPath, remoteToPath)));
                }
            }
            case "eventType" -> {
                var selectedType = (String) o;
                if (selectedType != null && !selectedType.isBlank()) {
                    currentEventType = selectedType.trim();
                    refresh();
                    var selection = form.getById("events").toSelection();
                    selection.setPossibilities(elements);

                    if (!images.isEmpty()) {
                        index = images.size() - 1;
                        label.setIcon(getImageIcon(form, images.get(index).get(1).getEventData().replace(remoteFromPath, remoteToPath)));
                    } else {
                        label.setIcon(getImageIcon(form, null));
                    }
                }
            }
            case "refresh" -> {
                refresh();
                SelectionFormElement selection = form.getById("events").toSelection();
                selection.setPossibilities(elements);
            }
            case "duration" -> {
            }
            case "before" -> {
                var before = form.getById("before").asString();
                if (before != null && !before.isEmpty()) {
                    if (isInteger(before)) {
                        playBefore = Integer.valueOf(before);
                    } else {
                        new UiBooster().showErrorDialog("Only integer values allowed.", "ERROR");
                    }
                }
            }
            case "after" -> {
                var after = form.getById("after").asString();
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
