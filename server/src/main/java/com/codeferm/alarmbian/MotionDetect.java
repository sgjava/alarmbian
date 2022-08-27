/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.service.EventService;
import com.codeferm.alarmbian.entity.Event;
import com.codeferm.alarmbian.image.Motion;
import static com.codeferm.alarmbian.type.EventType.HISTORY_FRAME;
import static com.codeferm.alarmbian.type.EventType.HISTORY_RESET;
import static com.codeferm.alarmbian.type.EventType.HISTORY_START;
import static com.codeferm.alarmbian.type.EventType.HISTORY_STOP;
import static com.codeferm.alarmbian.type.EventType.MOTION_FRAME;
import static com.codeferm.alarmbian.type.EventType.MOTION_RESET;
import static com.codeferm.alarmbian.type.EventType.MOTION_START;
import static com.codeferm.alarmbian.type.EventType.MOTION_START_ENTITY;
import static com.codeferm.alarmbian.type.EventType.MOTION_STOP;
import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 *
 * Motion detector.
 *
 * Uses moving average to determine change percent.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Slf4j
public class MotionDetect {

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
     * Used to presist event.
     */
    @Autowired
    private EventService eventService;
    /**
     * Motion detection.
     */
    private Motion motion;
    /**
     * Device name.
     */
    @Value("${device.name}")
    private String deviceName;
    /**
     * Motion start flag.
     */
    private boolean motionStart;
    /**
     * Video file name.
     */
    private String fileName;
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
        motionStart = false;
        // Configure motion detecion
        var kSize = config.getList("motion.ksize");
        final var ignoreMaskName = env.getProperty("motion.ignore.mask");
        Mat ignoreMask = null;
        if (ignoreMaskName != null && !ignoreMaskName.isBlank()) {
            log.info(String.format("Using ignore mask %s", ignoreMaskName));
            ignoreMask = Imgcodecs.imread(ignoreMaskName);
            Imgproc.cvtColor(ignoreMask, ignoreMask, Imgproc.COLOR_BGR2GRAY);
        }
        try {
            motion = ((Motion) Class.forName(env.getProperty("motion.class")).getDeclaredConstructor().newInstance()).
                    setkSize(new Size(kSize.get(0), kSize.get(1))).setAlpha(config.getDouble("motion.alpha")).setBlackThreshold(
                    config.getDouble("motion.black.threshold")).
                    setMaxThreshold(config.getDouble("motion.max.threshold")).setMaxChange(config.getDouble("motion.max.change")).
                    setStartThreshold(config.getDouble("motion.start.threshold")).
                    setStopThreshold(config.getDouble("motion.stop.threshold")).setIgnoreMask(ignoreMask);
            motion.init(mat);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException
                | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Clean up.
     */
    @PreDestroy
    public void done() {
        log.debug("done");
        motion.done();
    }

    /**
     * Publish and persist events.
     *
     * @param motionEventData Motion event.
     * @param historyEventData History event.
     */
    public void publishEvents(final EventData<Mat> motionEventData, final EventData<Mat> historyEventData) {
        applicationEventPublisher.publishEvent(motionEventData);
        applicationEventPublisher.publishEvent(historyEventData);
        // Persist all motion events except MOTION_FRAME
        if (!motionEventData.getEventType().equals(MOTION_FRAME)) {
            final var event = eventService.create(new Event(deviceName, motionEventData.getEventType().name(), fileName, Timestamp.from(
                    motionEventData.getTimestamp())));
            // Publish event entity handy if something needs this data for PK, etc.
            if (motionEventData.getEventType().equals(MOTION_START)) {
                applicationEventPublisher.publishEvent(new EventData<>(MOTION_START_ENTITY, motionEventData.getTimestamp(), event));
            }
        }
    }

    /**
     * Receives Mat frame and piblish Mat motionEvent and Mat historyEvent.
     *
     * @param event Mat data.
     */
    @EventListener(condition = "#event.eventType.name == 'MAT_FRAME'")
    public void onMatFrame(final EventData<Mat> event) {
        motion.detect(event.getData());
        // Detect if camera is adjusting and reset reference if more than maxChange
        if (motion.getMotionPercent() > motion.getMaxChange()) {
            publishEvents(new EventData<>(MOTION_RESET, event.getTimestamp(), event.getData()), new EventData<>(HISTORY_RESET,
                    event.getTimestamp(), motion.getBwImg()));
            // Threshold trigger motion
        } else if (motion.getMotionPercent() > motion.getStartThreshold() && !motionStart) {
            log.info(String.format("Motion start %.2f%%", motion.getMotionPercent()));
            motionStart = true;
            publishEvents(new EventData<>(MOTION_START, event.getTimestamp(), event.getData()), new EventData<>(HISTORY_START,
                    event.getTimestamp(), motion.getBwImg()));
        } else if (motion.getMotionPercent() <= motion.getStopThreshold() && motionStart) {
            log.info(String.format("Motion stop %.2f%%", motion.getMotionPercent()));
            motionStart = false;
            publishEvents(new EventData<>(MOTION_STOP, event.getTimestamp(), event.getData()), new EventData<>(HISTORY_STOP, event.
                    getTimestamp(), motion.getBwImg()));
        } else if (motionStart) {
            // Do not persist motion events
            publishEvents(new EventData<>(MOTION_FRAME, event.getTimestamp(), event.getData()), new EventData<>(HISTORY_FRAME,
                    event.getTimestamp(), motion.getBwImg()));
        }
    }

    /**
     * Stop stream if frame error received.
     *
     * @param event Event.
     */
    @EventListener(condition = "#event.eventType.name == 'FRAME_ERROR'")
    public void onFrameError(final EventData<String> event) {
        motionStart = false;
        publishEvents(new EventData<>(MOTION_STOP, event.getTimestamp(), null), new EventData<>(HISTORY_STOP, event.
                getTimestamp(), motion.getBwImg()));
    }

    /**
     * Recording start.
     *
     * @param event Event
     */
    @EventListener(condition = "#event.eventType.name == 'RECORD_START'")
    public void onRecordStart(final EventData<String> event) {
        fileName = event.getData();
        eventService.create(new Event(deviceName, event.getEventType().name(), fileName, Timestamp.from(event.getTimestamp())));
    }

    /**
     * Recording stop.
     *
     * @param event Event
     */
    @EventListener(condition = "#event.eventType.name == 'RECORD_STOP'")
    public void onRecordStop(final EventData<String> event) {
        eventService.create(new Event(deviceName, event.getEventType().name(), fileName, Timestamp.from(event.getTimestamp())));
    }
}
