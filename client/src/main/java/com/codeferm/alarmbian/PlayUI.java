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
import java.util.Collections;
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
 * Alarmbian player UI.
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
     * SMTP images?
     */
    private boolean smtpImages = false;

    /**
     * Set font globally.
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
     * Refresh from database.
     */
    public void refresh() {
        buffers = play.loadMotionBuffers();
        if (smtpImages) {
            images = play.loadSmtpMotionEvents();
        } else {
            images = play.loadMotionEvents();
        }
        elements = new ArrayList<>();
        timestamps = new HashMap<>();
        var i = 0;
        for (final var image : images) {
            final var type = image.get(0).getEventType();
            final var time = play.formatTimestamp(image.get(0).getEventTime());
            final var display = String.format("%s - %s", time, type);
            elements.add(display);
            timestamps.put(display, i++);
        }
        Collections.reverse(elements);
        index = images.size() - 1;
    }

    /**
     * Map index to file path.
     *
     * @param idx Index.
     * @return Path.
     */
    private String getPathForIndex(final int idx) {
        final var eventList = images.get(idx);
        final var target = smtpImages ? 0 : 1;
        return eventList.get(target).getEventData().replace(remoteFromPath, remoteToPath);
    }

    /**
     * Blocking call until OK button pressed.
     *
     * @return Execution confirmation.
     * @throws Exception Possible exception.
     */
    @Override
    public Integer call() throws Exception {
        final var dialog = booster.showWaitingDialog("Operation", play.getDeviceName());
        dialog.addToLargeMessage("Refresh data");
        refresh();
        dialog.close();

        final var initialPath = (index >= 0) ? getPathForIndex(index) : "";
        booster.createForm(play.getDeviceName()).
                addCustomElement(new IconFormElement(getImageIcon(null, initialPath))).
                setID("image").
                startRow().
                addSelection("Events", elements).setID("events").
                addText("Duration", (index >= 0 && !smtpImages) ? play.formatDuration(images.get(index).get(0).getEventTime(),
                        images.get(index).get(2).getEventTime()) : "N/A", true).setID("duration").
                addText("Before", String.valueOf(playBefore)).setID("before").
                addText("After", String.valueOf(playAfter)).setID("after").
                addSelection("Event Type", "Motion", "SMTP").setID("eventType").
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
     * Return image icon from image file.
     *
     * @param form Form.
     * @param fileName File name.
     * @return Image icon.
     */
    public ImageIcon getImageIcon(final Form form, final String fileName) {
        ImageIcon imageIcon = null;
        if (fileName != null && fileName.endsWith(".jpg") && new File(fileName).exists()) {
            source = Imgcodecs.imread(fileName);
            if (!source.empty()) {
                if (source.cols() > xMax) {
                    Imgproc.resize(source, dest, new Size(xMax, yMax), 0, 0, Imgproc.INTER_LINEAR);
                    imageIcon = new ImageIcon(matToBufImg.execute(dest));
                } else {
                    imageIcon = new ImageIcon(matToBufImg.execute(source));
                }
            }
            source.release();
        }
        return (imageIcon != null) ? imageIcon : new ImageIcon(new BufferedImage(xMax, yMax, BufferedImage.TYPE_INT_RGB));
    }

    /**
     * Save video clip.
     *
     * @param fileName File name.
     * @param start Start.
     * @param duration Duration.
     * @param outputFileName Output file name.
     */
    public void saveFile(final String fileName, final long start, final long duration, final String outputFileName) {
        final var command = List.of("ffmpeg", "-ss", String.valueOf(start), "-i", fileName, "-t", String.valueOf(duration), "-c", "copy", outputFileName);
        final var pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        try {
            final var pc = pb.start();
            try (final var is = pc.getInputStream(); final var reader = new BufferedReader(new InputStreamReader(is))) {
                while (reader.readLine() != null) {
                }
            }
            pc.waitFor();
            pc.destroy();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("FFmpeg failed", e);
        }
    }

    /**
     * Play video clip.
     *
     * @param fileName File name.
     * @param start Start.
     * @param duration Duration.
     */
    public void playFile(final String fileName, final long start, final long duration) {
        final var command = List.of("ffplay", "-autoexit", "-fflags", "nobuffer", "-analyzeduration", "0", "-probesize", "32", fileName, "-ss", String.valueOf(start), "-t", String.valueOf(duration));
        final var pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        try {
            final var pc = pb.start();
            try (final var is = pc.getInputStream(); final var reader = new BufferedReader(new InputStreamReader(is))) {
                while (reader.readLine() != null) {
                }
            }
            pc.waitFor();
            pc.destroy();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("FFplay failed", e);
        }
    }

    /**
     * Check if string is integer.
     *
     * @param str String.
     * @return True if integer.
     */
    public boolean isInteger(final String str) {
        try {
            Integer.valueOf(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * On change listener.
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
                if (index >= 0 && !smtpImages) {
                    final var eventList = images.get(index);
                    final var start = Duration.between(buffers.get(eventList.get(0).getEventData()).getEventTime().toInstant(), eventList.get(0).getEventTime().toInstant()).minusSeconds(playBefore);
                    final var duration = Duration.between(eventList.get(0).getEventTime().toInstant(), eventList.get(2).getEventTime().toInstant()).plusSeconds(playAfter);
                    playFile(eventList.get(1).getEventData().replace(remoteFromPath, remoteToPath).replace("jpg", "mkv"), start.getSeconds(), duration.getSeconds());
                }
            }
            case "save" -> {
                if (index >= 0 && !smtpImages) {
                    final var eventList = images.get(index);
                    final var start = Duration.between(buffers.get(eventList.get(0).getEventData()).getEventTime().toInstant(), eventList.get(0).getEventTime().toInstant()).minusSeconds(playBefore);
                    final var duration = Duration.between(eventList.get(0).getEventTime().toInstant(), eventList.get(2).getEventTime().toInstant()).plusSeconds(playAfter);
                    saveFile(eventList.get(1).getEventData().replace(remoteFromPath, remoteToPath).replace("jpg", "mkv"), start.getSeconds(), duration.getSeconds(), localPath + File.separator + new File(eventList.get(1).getEventData().replace("jpg", "mkv")).getName());
                }
            }
            case "events" -> {
                final var value = (String) form.getById("events").getValue();
                if (!StringUtils.isEmpty(value)) {
                    index = timestamps.get(value);
                    label.setIcon(getImageIcon(form, getPathForIndex(index)));
                }
            }
            case "eventType" -> {
                smtpImages = "SMTP".equals(o);
                refresh();
                ((SelectionFormElement) form.getById("events")).setPossibilities(elements);
                if (index >= 0) {
                    label.setIcon(getImageIcon(form, getPathForIndex(index)));
                }
            }
            case "refresh" -> {
                refresh();
                ((SelectionFormElement) form.getById("events")).setPossibilities(elements);
            }
            case "before" -> {
                if (isInteger(form.getById("before").asString())) {
                    playBefore = Integer.valueOf(form.getById("before").asString());
                }
            }
            case "after" -> {
                if (isInteger(form.getById("after").asString())) {
                    playAfter = Integer.valueOf(form.getById("after").asString());
                }
            }
        }
        if (index >= 0 && !smtpImages) {
            ((TextFormElement) form.getById("duration")).setValue(play.formatDuration(images.get(index).get(0).getEventTime(), images.get(index).get(2).getEventTime()));
        }
    }
}
