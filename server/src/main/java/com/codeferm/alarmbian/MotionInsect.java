/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.service.EventService;
import com.codeferm.alarmbian.entity.Event;
import static com.codeferm.alarmbian.type.EventType.MOTION_INSECT;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Consumes asynchronous MOTION_INSECT event payloads to execute trajectory verification. Evaluates geometric contour shapes to
 * isolate close-proximity insect streaks from vehicle passes. Gated globally to execute exclusively during dusk-to-dawn hours to
 * conserve edge compute.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.2
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(
        prefix = "alarmbian.insect",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Slf4j
public class MotionInsect {

    /**
     * Service used to mutate metadata configurations inside our database layer.
     */
    @Autowired
    private EventService eventService;

    /**
     * Receives Event data entity payload to perform structural contour metrics validation. Gated globally via SpEL condition to run
     * exclusively between 19:00 (7 PM) and 06:00 (6 AM).
     *
     * @final @param event EventData payload wrapping our target Event entity record.
     */
    @EventListener(condition = "#event.eventType.name == 'MOTION_INSECT' && "
            + "(T(java.time.LocalTime).now().isAfter(T(java.time.LocalTime).of(19, 0)) || "
            + " T(java.time.LocalTime).now().isBefore(T(java.time.LocalTime).of(6, 0)))")
    public void onMotionInsect(final EventData<Event> event) {
        final var dbEvent = event.getData();
        if (dbEvent == null) {
            log.error("Received MOTION_INSECT event containing a null database entity reference.");
            return;
        }

        final var filePath = dbEvent.getEventData();
        if (filePath == null || filePath.isBlank()) {
            log.error(String.format("Event row ID %d contains an empty or unassigned eventData target path.", dbEvent.getId()));
            return;
        }

        log.debug(String.format("Asynchronous contour analysis verification initiated for path: %s", filePath));

        final var historyMat = Imgcodecs.imread(filePath, Imgcodecs.IMREAD_GRAYSCALE);
        if (historyMat.empty()) {
            log.error(String.format("Failed to process history snapshot matrix boundary from target: %s", filePath));
            return;
        }

        // Invert to white vectors on a black backdrop for contour parsing
        Core.bitwise_not(historyMat, historyMat);

        final var contours = new ArrayList<MatOfPoint>();
        final var hierarchy = new Mat();

        Imgproc.findContours(historyMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        var humanOrVehicleDetected = false;
        var insectDetected = false;

        for (final var contour : contours) {
            final var area = Imgproc.contourArea(contour);

            // Ignore minor structural compression jitter or pixel noise floor artifacts
            if (area < 20.0) {
                continue;
            }

            final var boundingRect = Imgproc.boundingRect(contour);
            final var width = (double) boundingRect.width;
            final var height = (double) boundingRect.height;

            final var maxDim = Math.max(width, height);
            final var minDim = Math.min(width, height);
            final var aspectRatio = maxDim / minDim;

            if (log.isDebugEnabled()) {
                log.debug(String.format("Contour Node -> Mass Area: %.2f, W: %.2f, H: %.2f, Ratio: %.2f",
                        area, width, height, aspectRatio));
            }

            // 1. Genuine Target Profile A: Compact physical objects (Humans, Animals close up)
            final var isCompactTarget = area > 180.0 && aspectRatio < 4.0;

            // 2. Genuine Target Profile B: Massive multi-segment objects (Vehicles traversing background)
            // Approved because background cars yield large, broken tracking blocks with high aspect ratios
            final var isVehicleTarget = area > 4500.0 && aspectRatio >= 4.0;

            if (isCompactTarget || isVehicleTarget) {
                humanOrVehicleDetected = true;
                break;
            }

            // 3. Insect Indicator Signature A: Blurry lens blobs (Small bounding layouts)
            final var isSmallBlob = width < 35.0 && height < 35.0;

            // 4. Insect Indicator Signature B: Rapid close-up IR linear flight tracks
            // Lifted area threshold to 3500.0 to safely capture massive solid bright bug smears near the lens
            final var isBugStreak = aspectRatio > 4.5 && area < 3500.0;

            if (isSmallBlob || isBugStreak) {
                insectDetected = true;
                continue;
            }
        }

        // Execute conditional reclassification step
        if (humanOrVehicleDetected) {
            log.info(String.format("Event %d verified: Target profile confirmed authentic. Retaining history event log.", dbEvent.getId()));
        } else if (insectDetected) {
            log.info(String.format("Event %d verified: Classified as insect trail. Altering database classification taxonomy...", dbEvent.getId()));

            dbEvent.setEventType(MOTION_INSECT.name());
            eventService.update(dbEvent);
            log.info(String.format("Database event row ID %d updated successfully to type: MOTION_INSECT", dbEvent.getId()));
        } else {
            log.info(String.format("Event %d verified: Classified as static noise floor or micro environmental movement.", dbEvent.getId()));
        }

        // Native Environment Memory Safety: Release C++ descriptors explicitly
        hierarchy.release();
        for (final var contour : contours) {
            contour.release();
        }
        historyMat.release();
    }
}
