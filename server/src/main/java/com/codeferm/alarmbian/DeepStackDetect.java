/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.entity.Detection;
import com.codeferm.alarmbian.entity.Event;
import com.codeferm.alarmbian.entity.Frame;
import com.codeferm.alarmbian.image.MatToImage;
import com.codeferm.alarmbian.service.FrameService;
import com.codeferm.alarmbian.type.Convert;
import com.codeferm.deepstack.Base64EncodedMultipartFile;
import com.codeferm.deepstack.Client;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

/**
 * Use Deepstack object detection with async event. Only allow one detection at a time to not overload CPU.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@EnableAsync
@Slf4j
public class DeepStackDetect {

    /**
     * True while thread is running.
     */
    AtomicBoolean running;
    /**
     * Deepstack client.
     */
    @Autowired
    Client client;
    /**
     * Persist frames.
     */
    @Autowired
    private FrameService frameService;
    /**
     * Enabled flag.
     */
    @Value("${deepstack.enabled}")
    private boolean enabled;
    /**
     * Extension to convert image to.
     */
    @Value("${deepstack.image.extension}")
    private String extension;
    /**
     * Image converter.
     */
    private Convert<Mat, byte[]> convert;
    /**
     * Event entity ID.
     */
    private Long eventId;

    /**
     * Initialize bean.
     */
    @PostConstruct
    public void init() {
        log.debug("init");
        running = new AtomicBoolean(false);
        convert = (Convert) new MatToImage().setExtension(extension);
        ((MatToImage) convert).init();
    }

    /**
     * Clean up.
     */
    @PreDestroy
    public void done() {
        log.debug("done");
        ((MatToImage) convert).done();
    }

    /**
     * Save Event entity ID in case there are any detect records.
     *
     * @param event Event entity.
     */
    @EventListener(condition = "#event.eventType.name == 'MOTION_START_ENTITY'")
    public void setEntityId(final EventData<Event> event) {
        eventId = event.getData().getId();
    }

    /**
     * Call Deepstack on motion frames.
     *
     * @param event Mat event.
     */
    @Async
    @EventListener(condition = "#event.eventType.name == 'MOTION_FRAME'")
    public void objectDetection(final EventData<Mat> event) {
        // Allow only one async instance of Deepstack detection runing if enabled.
        if (enabled && !running.get()) {
            running.set(true);
            final var response = client.objectDetection(new Base64EncodedMultipartFile(convert.execute(event.getData()), String.format(
                    "mat%s", extension)));
            final var predictions = response.getPredictions();
            // Persist detection info
            if (!predictions.isEmpty()) {
                log.debug(predictions.toString());
                final var frame = frameService.create(new Frame(eventId, Timestamp.from(Instant.now())));
                for (final var prediction : predictions) {
                    frame.addDetection(new Detection(frame.getId(), prediction.getLabel(), prediction.getConfidence(), prediction.
                            getYMax(), prediction.getXMax(), prediction.getYMin(), prediction.getXMin()));
                }
                frameService.update(frame);
            }
            running.set(false);
        }
    }
}
