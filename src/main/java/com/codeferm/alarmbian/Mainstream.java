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
import org.springframework.core.env.Environment;
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
     * Spring environment.
     */
    @Autowired
    private Environment env;
    /**
     * Config bean.
     */
    @Autowired
    private Config config;
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
        log.info(String.format("Starting mainstream %s", env.getProperty("mainstream.name")));
        try {
            recordStream = ((Record) Class.forName(env.getProperty("mainstream.class")).getDeclaredConstructor().newInstance());
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException
                | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        // If using FfmpegOut class add necessary settings
        if (recordStream instanceof FfmpegOut ffmpegOut) {
            // Convert ffmpeg arguments into Map
            final var inArgMap = new LinkedHashMap<String, String>();
            config.getProperties("mainstream.input.arg", inArgMap);
            // Convert ffmpeg arguments into Map
            final var outArgMap = new LinkedHashMap<String, String>();
            config.getProperties("mainstream.output.arg", outArgMap);
            // Configure mainstream for recording
            ffmpegOut.setDevice(env.getProperty("mainstream.device")).setBin(env.getProperty("ffmpeg.bin")).setInputArgs(inArgMap).
                    setOutputArgs(outArgMap).setPath(String.format("%s%s%s", env.getProperty("ffmpeg.output.path"),
                    FileSystems.getDefault().getSeparator(), deviceName)).setContainer(env.getProperty("ffmpeg.container")).
                    setDirPattern(env.getProperty("ffmpeg.dir.pattern")).setFileSuffix(env.getProperty("mainstream.file.suffix")).
                    setFilePattern(env.getProperty("ffmpeg.file.pattern"));
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
