/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.service.EventService;
import com.codeferm.alarmbian.entity.Event;
import com.codeferm.alarmbian.image.BufImgToMat;
import static com.codeferm.alarmbian.type.EventType.FRAME_ERROR;
import static com.codeferm.alarmbian.type.EventType.MAT_FRAME;
import static com.codeferm.alarmbian.type.EventType.SHUT_DOWN;
import java.awt.image.BufferedImage;
import java.sql.Timestamp;
import java.time.Instant;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * App bean publishes off BufferedImage event that drives the detection logic.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Slf4j
public class App {

    /**
     * Spring environment.
     */
    @Autowired
    private Environment env;
    /**
     * Event publisher.
     */
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    /**
     * Persist events.
     */
    @Autowired
    private EventService eventService;
    /**
     * Substream acquires frames to analyze for motion or AI.
     */
    @Autowired
    private Substream substream;
    /**
     * Device name.
     */
    @Value("${device.name}")
    private String deviceName;
    /**
     * Mat used to initialize other components.
     */
    private Mat mat;
    /**
     * BufferedImage to Mat converter.
     */
    private BufImgToMat bufImgToMat;
    /**
     * Default to not shutting down.
     */
    private boolean shutDown = false;

    /**
     * Return Mat used to initialize other components.
     *
     * @return Mat.
     */
    @Bean
    public Mat getMat() {
        return mat;
    }

    /**
     * BufferedImage to Mat converter.
     *
     * @return Converter.
     */
    @Bean
    public BufImgToMat getBufImgToMat() {
        return bufImgToMat;
    }

    /**
     * Open substream, get frame and initialize BufferedImage to Mat converter if needed.
     */
    @PostConstruct
    public void init() {
        log.debug("init");
        substream.open();
        final var frame = substream.getFrame();
        // We only need converter if substream returns BufferedImage frame
        if (frame instanceof BufferedImage bufferedImage) {
            bufImgToMat = new BufImgToMat();
            bufImgToMat.init(bufferedImage);
            mat = bufImgToMat.execute(bufferedImage);
        } else {
            mat = (Mat) frame;
        }
    }

    /**
     * Release mat.
     */
    @PreDestroy
    public void done() {
        log.debug("done");
        substream.close();
        if (bufImgToMat != null) {
            bufImgToMat.done();
        }
    }

    /**
     * Persist EventData.
     *
     * @param event Event.
     */
    public void saveEvent(final EventData event) {
        eventService.create(new Event(deviceName, event.getEventType().name(), (String) event.getData(), Timestamp.from(event.
                getTimestamp())));
    }

    /**
     * Event loop publishes BufferedImage frames captured from substream.
     */
    public void run() {
        log.debug("Event loop running");
        while (!shutDown) {
            final var frame = substream.getFrame();
            // getFrame should return null frame on error
            if (frame != null) {
                // Convert BufferedImage to Mat if needed
                if (frame instanceof BufferedImage bufferedImage) {
                    applicationEventPublisher.publishEvent(new EventData<>(MAT_FRAME, Instant.now(), bufImgToMat.execute(
                            bufferedImage)));
                } else {
                    applicationEventPublisher.publishEvent(new EventData<>(MAT_FRAME, Instant.now(), frame));
                }
            } else {
                // This usually happens when substream stops responding, so we exit.
                log.error("Null frame");
                // Let listeners know we have to reset stuff
                applicationEventPublisher.publishEvent(new EventData<>(FRAME_ERROR, Instant.now(), "Null frame"));
                // Let listeners know we are shutting down
                applicationEventPublisher.publishEvent(new EventData<>(SHUT_DOWN, Instant.now(), "Null frame"));
            }
        }
    }

    /**
     * Handle start up event.
     *
     * @param event Event.
     */
    @EventListener(condition = "#event.eventType.name == 'START_UP'")
    public void startUp(final EventData<String> event) {
        saveEvent(event);
    }

    /**
     * Handle shut down event.
     *
     * @param event Event.
     */
    @EventListener(condition = "#event.eventType.name == 'SHUT_DOWN'")
    public void shutDown(final EventData<String> event) {
        shutDown = true;
        saveEvent(event);
    }
}
