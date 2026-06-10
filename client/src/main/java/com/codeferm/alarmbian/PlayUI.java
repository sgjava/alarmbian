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
import java.time.format.DateTimeFormatter;
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
     * Play logic reference context.
     */
    @Autowired
    private Play play;
    /**
     * UI Booster window orchestrator.
     */
    private final UiBooster booster;
    /**
     * Image index offset.
     */
    private int index = 0;
    /**
     * Collation collection mapping sequential events.
     */
    private List<List<Event>> images = new ArrayList<>();
    /**
     * Map of timestamps tracking index alignment pointers.
     */
    private Map<String, Integer> timestamps = new HashMap<>();
    /**
     * Human readable selection labels.
     */
    private List<String> elements = new ArrayList<>();
    /**
     * Target source substitution directory pattern matching rule.
     */
    @Value("${remoteFromPath}")
    private String remoteFromPath;
    /**
     * Target destination substitution directory pattern matching rule.
     */
    @Value("${remoteToPath}")
    private String remoteToPath;
    /**
     * Local storage output path directory constraint.
     */
    @Value("${localPath}")
    private String localPath;
    /**
     * Lead padding window constraint boundary.
     */
    @Value("${playBefore}")
    private Integer playBefore;
    /**
     * Trailing padding window constraint boundary.
     */
    @Value("${playAfter}")
    private Integer playAfter;
    /**
     * UI frame dimension width constraint.
     */
    @Value("${xMax}")
    private Integer xMax;
    /**
     * UI frame dimension height constraint.
     */
    @Value("${yMax}")
    private Integer yMax;
    /**
     * Native translation helper mapping matrix states to buffered assets.
     */
    private final MatToBufImg matToBufImg;
    /**
     * Active matrix container.
     */
    private Mat source;
    /**
     * Destination layout conversion matrix.
     */
    private final Mat dest;
    /**
     * Filtering configuration type indicator state parameter.
     */
    private String currentEventType = "SMTP_%";
    /**
     * Pre-compiled standard formatter to resolve clean string representation fields.
     */
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Set font globally and build native buffer spaces.
     */
    public PlayUI() {
        final var exampleFontSettings = new FontUIResource(new Font("MS Mincho", Font.PLAIN, 20));
        final var keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            final var key = keys.nextElement();
            final var value = UIManager.get(key);
            if (value instanceof FontUIResource) {
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
     * Clean string formatter to build timestamps locally without missing method definitions.
     */
    private String localFormatTimestamp(final java.sql.Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toLocalDateTime().format(timeFormatter);
    }

    /**
     * Formats duration metrics internally between execution points.
     */
    private String localFormatDuration(final java.sql.Timestamp start, final java.sql.Timestamp end) {
        if (start == null || end == null) {
            return "00:00:00";
        }
        final var totalSec = Duration.between(start.toInstant(), end.toInstant()).abs().toSeconds();
        final var hours = totalSec / 3600;
        final var minutes = (totalSec % 3600) / 60;
        final var seconds = totalSec % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Refresh from database using true methods mapped inside Play.java context.
     */
    public void refresh() {
        // Corrected: Removed the string argument since findSmtpMotionEvents takes no arguments
        final var rawEvents = play.findSmtpMotionEvents();
        final var videoMatches = play.findVideos();

        final var allocatedMap = new HashMap<Long, List<Event>>();
        if (!videoMatches.isEmpty()) {
            final var leadVideo = videoMatches.get(0);
            final var chunkStart = play.extractStartTimeFromFilename(leadVideo.getEventData());
            final var chunkDur = play.extractDurationFromFilename(leadVideo.getEventData());
            if (chunkStart != null && chunkDur != null) {
                final var edgeBoundaryTime = new java.sql.Timestamp(chunkStart.getTime() + chunkDur.toMillis());
                play.processEvents(allocatedMap, videoMatches, rawEvents, edgeBoundaryTime);
            }
        }

        images = new ArrayList<>(allocatedMap.values());
        elements = new ArrayList<>();
        timestamps = new HashMap<>();

        var i = 0;
        for (final var imageList : images) {
            if (!imageList.isEmpty()) {
                final var formattedString = localFormatTimestamp(imageList.get(0).getEventTime());
                timestamps.put(formattedString, i++);
            }
        }

        for (i = images.size(); i-- > 0;) {
            if (!images.get(i).isEmpty()) {
                elements.add(localFormatTimestamp(images.get(i).get(0).getEventTime()));
            }
        }
        index = images.isEmpty() ? 0 : images.size() - 1;
    }

    /**
     * Blocking execution hook mapping layout configurations.
     *
     * @return Confirmation tracking parameter identifier.
     * @throws Exception Core validation loop interrupt triggers.
     */
    @Override
    public Integer call() throws Exception {
        final var dialog = booster.showWaitingDialog("Operation", play.getDeviceName());
        dialog.addToLargeMessage("Refresh data");
        refresh();
        dialog.close();

        final var initialIndex = images.isEmpty() ? 0 : images.size() - 1;
        var initialPreviewFile = "";
        if (!images.isEmpty() && images.get(initialIndex).size() > 1) {
            initialPreviewFile = images.get(initialIndex).get(1).getEventData().replace(remoteFromPath, remoteToPath);
        }

        final var defaultEventTypes = List.of("SMTP_%", "SMTP_PERSON", "SMTP_VEHICLE", "Motion");

        booster.createForm(play.getDeviceName()).
                addCustomElement(new IconFormElement(getImageIcon(null, initialPreviewFile))).
                setID("image").
                startRow().
                addSelection("Events", elements).setID("events").
                addText("Duration", (images.isEmpty() || images.get(initialIndex).size() < 3) ? "00:00:00" : localFormatDuration(images.get(initialIndex).get(0).getEventTime(), images.get(initialIndex).get(2).getEventTime()), true).setID("duration").
                addText("Before", String.valueOf(playBefore)).setID("before").
                addText("After", String.valueOf(playAfter)).setID("after").
                addSelection("Event Type", defaultEventTypes).setID("eventType").
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
     * Return structural image wrapper container.
     *
     * @param form Target window framework element link.
     * @param fileName Target path verification file label.
     * @return Generated ImageIcon reference tool.
     */
    public ImageIcon getImageIcon(final Form form, final String fileName) {
        if (fileName == null || fileName.isBlank() || !(new File(fileName).exists())) {
            return new ImageIcon(new BufferedImage(xMax, yMax, BufferedImage.TYPE_3BYTE_BGR));
        }

        source = Imgcodecs.imread(fileName);
        var type = BufferedImage.TYPE_BYTE_GRAY;
        if (source.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }

        ImageIcon imageIcon = null;
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
     * Propagate runtime metric variations to visual elements.
     *
     * @param form Master form component tracking target.
     */
    public void update(final Form form) {
        if (!images.isEmpty() && images.get(index).size() >= 3) {
            final var duration = (TextFormElement) form.getById("duration");
            duration.setValue(localFormatDuration(images.get(index).get(0).getEventTime(), images.get(index).get(2).getEventTime()));
        }
    }

    /**
     * Native FFmpeg storage segment compilation pipeline.
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
                    // Suppressed text drain block
                }
            }
            try {
                pc.waitFor();
                pc.destroy();
                log.debug("File saved successfully to {}", outputFileName);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Process tracking execution sequence split", e);
            }
        } catch (final IOException e) {
            throw new RuntimeException("Failed to execute FFmpeg command context execution rule", e);
        }
    }

    private String formatAbsoluteTime(final long totalSeconds) {
        final var positiveSeconds = Math.max(0, totalSeconds);
        final var hours = positiveSeconds / 3600;
        final var minutes = (positiveSeconds % 3600) / 60;
        final var seconds = positiveSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Native video rendering playback deployment container.
     */
    public void playFile(final String fileName, final long start, final long duration) {
        final var seekStr = formatAbsoluteTime(start);
        final var durationStr = formatAbsoluteTime(duration);

        if (log.isDebugEnabled()) {
            log.debug("Target File Playback Configuration Context: File={}, Start={}, TargetLen={}", fileName, seekStr, durationStr);
        }

        final var command = new ArrayList<String>();
        command.add("ffplay");
        command.add("-autoexit");
        command.add("-window_title");
        command.add("Alarmbian Event Playback View");
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
        command.add("-an");
        command.add("-sn");
        command.add("-sync");
        command.add("video");
        command.add("-framedrop");
        command.add("-infbuf");

        final var pb = new ProcessBuilder(command);
        pb.inheritIO();

        try {
            final var pc = pb.start();
            try {
                pc.waitFor();
                pc.destroy();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interactive process synchronization trace split", e);
            }
        } catch (final IOException e) {
            throw new RuntimeException("Failed to launch sub-process framework context tracker", e);
        }
    }

    public boolean isInteger(final String str) {
        try {
            Integer.valueOf(str);
            return true;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void onChange(final FormElement fe, final Object o, final Form form) {
        final var label = (JLabel) form.getById("image").getValue();
        switch (fe.getId()) {
            case "play" -> {
                if (images.isEmpty() || images.get(index).isEmpty()) {
                    return;
                }
                final var motionStart = images.get(index).get(0).getEventTime().toInstant();
                final var motionStop = (images.get(index).size() >= 3) ? images.get(index).get(2).getEventTime().toInstant() : motionStart.plusSeconds(10);

                var startSeconds = Duration.between(motionStart, motionStart).minusSeconds(playBefore).getSeconds();
                if (startSeconds < 0) {
                    startSeconds = 0;
                }

                final var duration = Duration.between(motionStart, motionStop).plusSeconds(playAfter);
                final var fileName = images.get(index).get(0).getEventData().replace(remoteFromPath, remoteToPath);

                playFile(fileName, startSeconds, duration.getSeconds());
            }
            case "save" -> {
                if (images.isEmpty() || images.get(index).isEmpty()) {
                    return;
                }
                final var motionStart = images.get(index).get(0).getEventTime().toInstant();
                final var motionStop = (images.get(index).size() >= 3) ? images.get(index).get(2).getEventTime().toInstant() : motionStart.plusSeconds(10);

                var startSeconds = Duration.between(motionStart, motionStart).minusSeconds(playBefore).getSeconds();
                if (startSeconds < 0) {
                    startSeconds = 0;
                }

                final var duration = Duration.between(motionStart, motionStop).plusSeconds(playAfter);
                final var fileName = images.get(index).get(0).getEventData().replace(remoteFromPath, remoteToPath);

                var outName = "event.mkv";
                if (images.get(index).size() > 1) {
                    outName = new File(images.get(index).get(1).getEventData().replace("jpg", "mkv")).getName();
                }

                saveFile(fileName, startSeconds, duration.getSeconds(), String.format("%s%s%s", localPath, File.separator, outName));
            }
            case "events" -> {
                final var value = (String) form.getById("events").getValue();
                if (!StringUtils.isEmpty(value) && timestamps.containsKey(value)) {
                    index = timestamps.get(value);
                    if (images.get(index).size() > 1) {
                        label.setIcon(getImageIcon(form, images.get(index).get(1).getEventData().replace(remoteFromPath, remoteToPath)));
                    }
                }
            }
            case "eventType" -> {
                final var selectedType = (String) o;
                if (selectedType != null && !selectedType.isBlank()) {
                    currentEventType = "Motion".equalsIgnoreCase(selectedType.trim()) ? "Motion" : selectedType.trim();
                    refresh();
                    final var selection = form.getById("events").toSelection();
                    selection.setPossibilities(elements);

                    if (!images.isEmpty()) {
                        index = images.size() - 1;
                        if (images.get(index).size() > 1) {
                            label.setIcon(getImageIcon(form, images.get(index).get(1).getEventData().replace(remoteFromPath, remoteToPath)));
                        }
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
                if (before != null && !before.isEmpty() && isInteger(before)) {
                    playBefore = Integer.valueOf(before);
                }
            }
            case "after" -> {
                final var after = form.getById("after").asString();
                if (after != null && !after.isEmpty() && isInteger(after)) {
                    playAfter = Integer.valueOf(after);
                }
            }
            default ->
                booster.showErrorDialog(String.format("%s onChange not handled", fe.getId()), "Error");
        }
        update(form);
    }
}
