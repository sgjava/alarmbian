/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.image;

import com.codeferm.alarmbian.type.VideoSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

/**
 * Read frames from OpenCV VideoCapture source safely using allocation-free buffer reuse. Handles numeric device IDs (including
 * negative wildcards like -1) and RTSP URL strings.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.1
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class VideoIn extends VideoSource {

    /**
     * Regex pattern matching integers, including optional leading negative signs for default device.
     */
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+$");

    /**
     * Reusable pre-allocated Mat for frame ingestion to prevent native memory leaks.
     */
    private Mat mat;
    /**
     * Class for video capturing from video files or cameras.
     */
    private VideoCapture videoCapture;
    /**
     * Use FPS delay for video file input.
     */
    private double fps = 0.0;
    /**
     * MS delay if FPS > 0.
     */
    private long delay = 0;
    /**
     * Next frame instant, so FPS delay can be calculated.
     */
    private Instant nextFrame = Instant.now();

    /**
     * Open OpenCV VideoCapture by parsing the device parameter as an index or a URL. Pre-allocates native frame buffer safely.
     *
     * @param device String representation of device (URL or numeric index/wildcard).
     * @return True if opened successfully.
     */
    public boolean open(final String device) {
        var opened = false;
        // Prevent leaks if open is called sequentially on a single instance lifecycle
        if (mat != null) {
            mat.release();
            mat = null;
        }
        try {
            // Check if the device parameter string matches your integer pattern
            if (NUMERIC_PATTERN.matcher(device).matches()) {
                final var deviceIndex = Integer.parseInt(device);
                log.info("Initializing VideoCapture using native device index/wildcard: {}", deviceIndex);
                videoCapture = new VideoCapture(deviceIndex);
            } else {
                log.info("Initializing VideoCapture using native network stream URL: {}", device);
                videoCapture = new VideoCapture(device);
            }
            opened = videoCapture.isOpened();
            if (opened) {
                // Pre-allocate the reusable frame buffer once upon stream initialization
                mat = new Mat();
                log.info("Opened video device: {}", device);
            } else {
                log.error("Failed to open video device: {}", device);
            }
        } catch (final Exception ex) {
            log.error("Exception encountered initializing native VideoCapture handle", ex);
        }
        return opened;
    }

    /**
     * Return image as a safely validated Mat pointer or null if no frame read. Reuses the internal pre-allocated Mat structure to
     * avoid native frame-by-frame leaks.
     *
     * @return Image as a Mat pointer or null.
     */
    @Override
    public Mat getFrame() {
        if (videoCapture == null || !videoCapture.isOpened() || mat == null) {
            log.error("VideoCapture interface or buffer context is uninitialized.");
            return null;
        }
        final var check = Instant.now().plusMillis(getTimeout());
        var read = false;
        try {
            while (!read && check.compareTo(Instant.now()) > 0) {
                // Reuse the same block-allocated native buffer memory address
                read = videoCapture.read(mat);

                // CRITICAL CORRUPTION GUARD:
                // Intercept truncated network packets that corrupt inner C++ layout pointers
                if (read && (mat.empty() || mat.rows() == 0 || mat.cols() == 0)) {
                    log.warn("Corrupt or empty frame boundary intercepted inside JNI layer.");
                    read = false;
                }
                if (!read) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(getTimeout() / 10);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (final Exception ex) {
            log.error("Intercepted native memory exception during stream ingestion", ex);
            read = false;
        }
        var frame = mat;
        if (!read) {
            frame = null;
        }

        // Apply processing frame-rate pacing for file simulation mode
        if (fps > 0.0) {
            final var sleepTime = delay - ChronoUnit.MILLIS.between(nextFrame, Instant.now());
            nextFrame = Instant.now();
            if (sleepTime > 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(sleepTime);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return frame;
    }

    /**
     * Release VideoCapture and explicitly de-allocate native Mat memory structures.
     */
    @Override
    public void close() {
        try {
            if (mat != null) {
                // Explicitly invoke native C++ de-allocator to avoid memory leaks
                mat.release();
                mat = null;
                log.info("Native frame buffer memory context de-allocated.");
            }
            if (videoCapture != null) {
                videoCapture.release();
                log.info("Native VideoCapture hardware channel context closed.");
            }
        } catch (final Exception ex) {
            log.error("Error encountered executing native wrapper teardown lifecycle", ex);
        } finally {
            videoCapture = null;
        }
    }
}
