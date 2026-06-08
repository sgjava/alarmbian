/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.service.EventService;
import com.codeferm.alarmbian.entity.Event;
import com.codeferm.alarmbian.image.MatToImage;
import java.nio.file.FileSystems;
import java.sql.Timestamp;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Write off motion history image. This can be used to generate ignore area masks.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Slf4j
public class MotionHistory {

    /**
     * Used to persist event.
     */
    @Autowired
    private EventService eventService;
    /**
     * History writer.
     */
    private HistoryWriter historyWriter;
    /**
     * Device name.
     */
    @Value("${device.name}")
    private String deviceName;
    /**
     * History output path.
     */
    @Value("${history.output.path}")
    private String outputPath;
    /**
     * FFMPEG dir pattern.
     */
    @Value("${ffmpeg.dir.pattern}")
    private String dirPattern;
    /**
     * FFMPEG file pattern.
     */
    @Value("${ffmpeg.file.pattern}")
    private String filePattern;
    /**
     * History writer file extension.
     */
    @Value("${history.writer.extension}")
    private String extension;
    /**
     * Mat used for configuration.
     */
    @Autowired
    private Mat mat;

    /**
     * Initialize motion detection.
     */
    @PostConstruct
    public void init() {
        log.debug("init");
        // Configure history image writer
        final var historyConvert = new MatToImage().setExtension(extension);
        historyConvert.init();
        historyWriter = new HistoryWriter(historyConvert, String.format("%s%s%s", outputPath, FileSystems.getDefault().
                getSeparator(), deviceName), dirPattern, filePattern);
        historyWriter.init(mat);
    }

    /**
     * Clean up.
     */
    @PreDestroy
    public void done() {
        log.debug("done");
        historyWriter.done();
    }

    /**
     * Receives Mat event of type HISTORY_START.
     *
     * @param event Mat data.
     */
    @EventListener(condition = "#event.eventType.name == 'HISTORY_START'")
    public void onHistoryStart(final EventData<Mat> event) {
        // Save timestamp of start motion for file name
        historyWriter.setTimestamp(event.getTimestamp());
        // Clear image
        historyWriter.getMat().setTo(new Scalar(0));
        // Bitwise OR with black and white motion image
        Core.bitwise_or(historyWriter.getMat(), event.getData(), historyWriter.getMat());
    }

    /**
     * Receives Mat event of type HISTORY_FRAME.
     *
     * @param event Mat data.
     */
    @EventListener(condition = "#event.eventType.name == 'HISTORY_FRAME'")
    public void onHistoryFrame(final EventData<Mat> event) {
        // Bitwise OR with black and white motion image
        Core.bitwise_or(historyWriter.getMat(), event.getData(), historyWriter.getMat());
    }

    /**
     * Receives Mat event of type HISTORY_FRAME.
     *
     * @param event Mat data.
     */
    @EventListener(condition = "#event.eventType.name == 'HISTORY_STOP'")
    public void onHistoryStop(final EventData<Mat> event) {
        // Bitwise OR with black and white motion image
        Core.bitwise_or(historyWriter.getMat(), event.getData(), historyWriter.getMat());
        final var fileName = historyWriter.saveHistoryImage(event);
        // Save off event
        eventService.create(new Event(deviceName, event.getEventType().name(), fileName, Timestamp.from(event.getTimestamp())));
    }
}
