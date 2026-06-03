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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Consumes asynchronous MOTION_INSECT event payloads to execute trajectory verification. Decodes history maps back into native
 * matrices, calculates geometric tracking paths, and modifies persisted Event taxonomy records if insect activity signatures are
 * validated.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Slf4j
public class MotionInsect {

    /**
     * Service used to mutate metadata configurations inside our database layer.
     */
    @Autowired
    private EventService eventService;

    /**
     * Receives Event data entity payload to perform structural contour metrics validation.
     *
     * @final @param event EventData payload wrapping our target Event entity record.
     */
    @EventListener(condition = "#event.eventType.name == 'MOTION_INSECT'")
    public void onMotionInsect(final EventData<Event> event) {
        final var dbEvent = event.getData();
        if (dbEvent == null) {
            log.error("Received MOTION_INSECT event containing a null database entity reference.");
            return;
        }

        // Extracted file path from the generic eventData column property
        final var filePath = dbEvent.getEventData();
        if (filePath == null || filePath.isBlank()) {
            log.error(String.format("Event row ID %d contains an empty or unassigned eventData target path.", dbEvent.getId()));
            return;
        }

        log.debug(String.format("Asynchronous contour analysis verification initiated for path: %s", filePath));

        // Read saved history tracking snapshot back out of our local disk loop in grayscale
        final var historyMat = Imgcodecs.imread(filePath, Imgcodecs.IMREAD_GRAYSCALE);
        if (historyMat.empty()) {
            log.error(String.format("Failed to process history snapshot matrix boundary from target: %s", filePath));
            return;
        }

        // HistoryWriter executes Core.bitwise_not prior to writing a file out to make it look like an ignore mask canvas.
        // We invert it back to standard white vectors on a black backdrop for contour parsing.
        Core.bitwise_not(historyMat, historyMat);

        final var contours = new ArrayList<MatOfPoint>();
        final var hierarchy = new Mat();

        // Query external tracking paths out of our working buffer
        Imgproc.findContours(historyMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        var humanOrVehicleDetected = false;
        var insectDetected = false;

        for (final var contour : contours) {
            final var area = Imgproc.contourArea(contour);

            // 1. Immediately ignore structural compression jitter or pixel noise floor artifacts
            if (area < 20.0) {
                continue;
            }

            final var boundingRect = Imgproc.boundingRect(contour);
            final var width = (double) boundingRect.width;
            final var height = (double) boundingRect.height;

            // Calculate elongation factor bounds
            final var maxDim = Math.max(width, height);
            final var minDim = Math.min(width, height);
            final var aspectRatio = maxDim / minDim;

            if (log.isDebugEnabled()) {
                log.debug(String.format("Contour Node -> Mass Area: %.2f, W: %.2f, H: %.2f, Aspect Ratio: %.2f",
                        area, width, height, aspectRatio));
            }

            // 2. Insect Indicator Signature A: Blurry lens blobs (Small bounding layout metrics)
            final var isSmallBlob = width < 35.0 && height < 35.0;

            // 3. Insect Indicator Signature B: Rapid IR linear flight tracks (Elongated paths with small mass areas)
            final var isBugStreak = aspectRatio > 4.5 && area < 1000.0;

            if (isSmallBlob || isBugStreak) {
                insectDetected = true;
                continue;
            }

            // 4. Genuine Target Profile: Large unified object footprint within lower aspect ratios
            if (area > 180.0 && aspectRatio < 4.0) {
                humanOrVehicleDetected = true;
                break; // A single validated human/vehicle trajectory approves the entire sequence
            }
        }

        // Execute conditional reclassification step
        if (humanOrVehicleDetected) {
            log.info(String.format("Event %d verified: Target profile confirmed authentic. Retaining history event log.", dbEvent.getId()));
        } else if (insectDetected) {
            log.info(String.format("Event %d verified: Classified as insect trail. Altering database classification taxonomy...", dbEvent.getId()));

            // Reclassify event type token to MOTION_INSECT and update database
            dbEvent.setEventType(MOTION_INSECT.name());
            eventService.update(dbEvent);
            log.info(String.format("Database event row ID %d updated successfully to type: MOTION_INSECT", dbEvent.getId()));
        } else {
            log.info(String.format("Event %d verified: Classified as static noise floor or micro environmental movement.", dbEvent.getId()));
        }

        // Strict Native Environment Memory Safety: Release C++ descriptors explicitly
        hierarchy.release();
        for (final var contour : contours) {
            contour.release();
        }
        historyMat.release();
    }
}
