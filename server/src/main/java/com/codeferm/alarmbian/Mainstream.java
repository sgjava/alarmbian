/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.image.FfmpegOut;
import com.codeferm.alarmbian.type.Record;
import static com.codeferm.alarmbian.type.EventType.RECORD_START;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileSystems;
import java.time.Instant;
import java.util.LinkedHashMap;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import static com.codeferm.alarmbian.type.EventType.RECORD_STOP;
import org.opencv.core.Mat;
import org.springframework.context.event.EventListener;

/**
 * This is used to save off mainstream for 24/7 recording. Files need to be removed by another process like a cron job.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Slf4j
public class Mainstream {

    /**
     * Event publisher.
     */
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    /**
     * Device name.
     */
    @Value("${device.name}")
    private String deviceName;
    /**
     * Recording length in milliseconds.
     */
    @Value("${mainstream.length}")
    private Long length;
    /**
     * Main stream name.
     */
    @Value("${mainstream.name}")
    private String name;
    /**
     * Main stream class.
     */
    @Value("${mainstream.class}")
    private String className;
    /**
     * Main stream device.
     */
    @Value("${mainstream.device}")
    private String device;
    /**
     * Main stream file suffix.
     */
    @Value("${mainstream.file.suffix}")
    private String fileSuffix;
    /**
     * FFMPEG bin.
     */
    @Value("${ffmpeg.bin}")
    private String bin;
    /**
     * FFMPEG output path.
     */
    @Value("${ffmpeg.output.path}")
    private String outputPath;
    /**
     * FFMPEG container.
     */
    @Value("${ffmpeg.container}")
    private String container;
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
     * FFMPEG input arguments.
     */
    @Value("#{${mainstream.input.args}}")
    private LinkedHashMap<String, String> inArgMap;
    /**
     * FFMPEG output arguments.
     */
    @Value("#{${mainstream.output.args}}")
    private LinkedHashMap<String, String> outArgMap;
    /**
     * Video stream used to record.
     */
    private Record recordStream;
    /**
     * Recording in progress.
     */
    private boolean recording = false;
    /**
     * Graceful stop in progress in progress.
     */
    private boolean stopping = false;
    /**
     * Time record buffer started.
     */
    private Instant startRecord;
    /**
     * Duration of recording.
     */
    private Instant duration;
    /**
     * Motion in progress.
     */
    private boolean motion = false;

    /**
     * Initialize motion detection.
     */
    @PostConstruct
    public void init() {
        log.debug("init");
    }

    /**
     * Stop recording.
     */
    @PreDestroy
    public void done() {
        log.debug("done");
        recordStream.stop(Instant.now());
    }

    /**
     * Start recording.
     *
     * @param timestamp Timestamp to use in file name.
     */
    public void start(final Instant timestamp) {
        log.info(String.format("Starting mainstream %s", name));
        try {
            recordStream = ((Record) Class.forName(className).getDeclaredConstructor().newInstance());
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException
                | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        // If using FfmpegOut class add necessary settings
        if (recordStream instanceof FfmpegOut ffmpegOut) {
            // Configure mainstream for recording
            ffmpegOut.setDevice(device).setBin(bin).setInputArgs(inArgMap).setOutputArgs(outArgMap).setPath(String.format("%s%s%s",
                    outputPath, FileSystems.getDefault().getSeparator(), deviceName)).setContainer(container).setDirPattern(
                    dirPattern).setFileSuffix(fileSuffix).setFilePattern(filePattern);
            recordStream.start(timestamp);
            applicationEventPublisher.publishEvent(new EventData<>(RECORD_START, timestamp, ffmpegOut.getFileName()));
        } else {
            applicationEventPublisher.publishEvent(new EventData<>(RECORD_START, timestamp, "No file"));
        }
    }

    /**
     * Receives Mat event and starts recording if not already started.
     *
     * @param event Mat data.
     */
    @EventListener(condition = "#event.eventType.name == 'MAT_FRAME'")
    public void onMatFrame(final EventData<Mat> event) {
        if (!recording) {
            recording = true;
            start(event.getTimestamp());
            startRecord = Instant.now();
            duration = startRecord.plusMillis(length);
            // See if ffmpeg process ended
        } else if (recordStream instanceof FfmpegOut ffmpegOut && ffmpegOut.getFuture().isDone()) {
            recording = false;
            stopping = false;
            applicationEventPublisher.publishEvent(new EventData<>(RECORD_STOP, event.getTimestamp(), ffmpegOut.getFileName()));
            // See if we need to stop recording buffer
        } else if (!stopping && !motion && recordStream instanceof FfmpegOut ffmpegOut && !ffmpegOut.getFuture().isDone()
                && Instant.now().isAfter(duration)) {
            recordStream.stop(event.getTimestamp());
            stopping = true;
        }
    }

    /**
     * Stop stream if frame error received.
     *
     * @param event Event.
     */
    @EventListener(condition = "#event.eventType.name == 'FRAME_ERROR'")
    public void onFrameError(final EventData<String> event) {
        recordStream.stop(event.getTimestamp());
        stopping = true;
    }

    /**
     * Motion start.
     *
     * @param event Event.
     */
    @EventListener(condition = "#event.eventType.name == 'MOTION_START'")
    public void onMotionStart(final EventData<Mat> event) {
        motion = true;
    }

    /**
     * Motion stop.
     *
     * @param event Event.
     */
    @EventListener(condition = "#event.eventType.name == 'MOTION_STOP'")
    public void onMotionStop(EventData<Mat> event) {
        motion = false;
    }
}
