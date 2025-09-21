/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm;

import com.codeferm.alarmbian.entity.Event;
import com.codeferm.alarmbian.image.MatToBufImg;
import com.formdev.flatlaf.util.StringUtils;
import de.milchreis.uibooster.UiBooster;
import de.milchreis.uibooster.model.Form;
import de.milchreis.uibooster.model.FormElement;
import de.milchreis.uibooster.model.FormElementChangeListener;
import de.milchreis.uibooster.model.UiBoosterOptions;
import de.milchreis.uibooster.model.formelements.CheckboxFormElement;
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
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 *
 * @author servadmin
 */
@Component
@Slf4j
@Command(name = "playUI")
public class PlayUI implements Callable<Integer>, FormElementChangeListener {
    /**
     * Camera matrix.
     */
    @Option(names = {"-m", "--matrix"}, description = "Camera matrix")
    private String matrix = "camera-matrix.bin";
    /**
     * Distortion coefficients.
     */
    @Option(names = {"-c", "--coefs"}, description = "Distortion coefficients")
    private String coefs = "dist-coefs.bin";

    @Autowired
    private ApplicationContext context;
    /**
     * Play logic.
     */
    @Autowired
    private Play play;
    /**
     * UI Booster.
     */
    private UiBooster booster;
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
     * Remote from path.
     */
    @Value("${remoteToPath}")
    private String remoteToPath;
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
     * Calibration array.
     */
    private Mat[] calibrateArr;
    /**
     * Convert Mat to BufferedImage.
     */
    private MatToBufImg matToBufImg;

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
    }
    
    /**
     * Refresh from database.
     */
    public void refresh() {
        buffers = play.loadBuffers();
        images = play.loadEvents();
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
        index = images.size() - 1;
    }    

    public Integer call() throws Exception {
        calibrateArr = play.loadCalibrate(matrix, coefs);
        final var dialog = booster.showWaitingDialog("Operation", play.getDeviceName());
        dialog.addToLargeMessage("Refresh data");
        refresh();
        index = images.size() - 1;
        BufferedImage image;
        try {
            image = ImageIO.read(new File(images.get(index).get(1).getEventData().replace(remoteFromPath, remoteToPath)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dialog.close();
        var form = booster.createForm(play.getDeviceName()).
                addCustomElement(new IconFormElement(images.get(index).get(1).getEventData().replace(remoteFromPath, remoteToPath))).
                setID("image").
                startRow().
                addSelection("Events", elements).setID("events").
                addText("Duration", play.formatDuration(images.get(index).get(0).getEventTime(), images.get(index).get(2).
                        getEventTime())).setID("duration").setDisabled().
                addCheckbox(null).setID("undistort").
                endRow().
                startRow().
                addButton("Play", () -> {
                }).setID("play").
                addButton("Refresh", () -> {
                }).setID("refresh").
                endRow().
                andWindow().setSize(image.getWidth() + 40, image.getHeight() + 310).save().
                setChangeListener(this).show();
        // Clean up
        matToBufImg.done();
        if (calibrateArr != null) {
            calibrateArr[0].release();
            calibrateArr[1].release();
        }
        return 0;
    }

    /**
     * Update form elements.
     *
     * @param form Form to update.
     * @param fileName Image file name.
     * @return Image icon.
     */
    public ImageIcon getImageIcon(final Form form, final String fileName) {
        final boolean checked = ((CheckboxFormElement) form.getById("undistort")).getValue();
        ImageIcon imageIcon;
        if (checked) {
            final var mat = Imgcodecs.imread(fileName, Imgcodecs.IMREAD_UNCHANGED);
            final var undistort = play.undistort(mat, calibrateArr[0], calibrateArr[1]);
            mat.release();
            imageIcon = new ImageIcon(matToBufImg.execute(undistort));
            undistort.release();
        } else {
            imageIcon = new ImageIcon(fileName);
        }
        return imageIcon;
    }

    /**
     * Update form elements.
     *
     * @param form Form to update.
     */
    public void update(final Form form) {
        var duration = (TextFormElement) form.getById("duration");
        duration.setValue(play.formatDuration(images.get(index).get(0).getEventTime(), images.get(index).get(2).getEventTime()));
    }

    /**
     * Use ffplay to play motion from buffer using start and duration.
     *
     * @param fileName Video buffer file.
     * @param start Start offset.
     * @param duration Duration in seconds.
     */
    public void play(final String fileName, final long start, final long duration) {
        final var command = new ArrayList<String>();
        command.add("ffplay");
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
            case "play":
                final var bufferStart = buffers.get(images.get(index).get(0).getEventData()).getEventTime().toInstant();
                final var motionStart = images.get(index).get(0).getEventTime().toInstant();
                final var motionStop = images.get(index).get(2).getEventTime().toInstant();
                final var start = Duration.between(bufferStart, motionStart).minusSeconds(playBefore);
                final var duration = Duration.between(motionStart, motionStop).plusSeconds(playAfter);
                final var fileName = images.get(index).get(0).getEventData().replace(remoteFromPath, remoteToPath);
                play(fileName, start.getSeconds(), duration.getSeconds());
                break;
            case "events":
            case "undistort":
                var value = (String) form.getById("events").getValue();
                // This will happen when Refresh pressed bacause selection list is updated
                if (!StringUtils.isEmpty(value)) {
                    index = timestamps.get(value);
                    label.setIcon(getImageIcon(form, images.get(index).get(1).getEventData().replace(remoteFromPath, remoteToPath)));
                }
                break;
            case "refresh":
                refresh();
                final var selection = form.getById("events").toSelection();
                selection.setPossibilities(elements);
                break;
            default:
                booster.showErrorDialog(String.format("%s onChange not handled", fe.getId()), "Error");
        }
        update(form);
    }
}
