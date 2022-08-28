/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.image;

import com.codeferm.alarmbian.type.VideoSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

/**
 * Read frames from OpenCV VideoCapture source.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class VideoIn extends VideoSource {

    /**
     * Mat for image capture.
     */
    private Mat mat;
    /**
     * Class for video capturing from video files or cameras.
     */
    private VideoCapture videoCapture;
    /**
     * FPS delay for video file input otherwise it reads as fast as system will let VideoCapture.
     */
    private int fps;
    /**
     * MS delay if FPS > 0.
     */
    private int delay = 0;
    private Instant start;

    public Mat getMat() {
        return mat;
    }

    public VideoIn setMat(Mat mat) {
        this.mat = mat;
        return this;
    }

    public VideoCapture getVideoCapture() {
        return videoCapture;
    }

    public VideoIn setVideoCapture(VideoCapture videoCapture) {
        this.videoCapture = videoCapture;
        return this;
    }

    public int getFps() {
        return fps;
    }

    public VideoIn setFps(int fps) {
        this.fps = fps;
        // MS to delay if FPX > 0
        if (fps > 0) {
            delay = 1000 / fps;
        }
        return this;
    }

    /**
     * Open OpenCV VideoCapture.
     *
     * @param device String representation of device.
     * @return True on success and false on failure.
     */
    @Override
    public boolean open(String device) {
        // See if device is an integer: -? = negative sign, could have none or one,
        // \\d+ = one or more digits
        if (device.matches("-?\\d+")) {
            videoCapture = new VideoCapture();
            videoCapture.open(Integer.parseInt(device));
        } else {
            videoCapture = new VideoCapture();
            videoCapture.open(device);
        }
        // Create Mat if needed
        if (mat == null) {
            mat = new Mat();

        }
        return getFrame() != null;
    }

    /**
     * Return image as a Mat or null if no frame read.
     *
     * @return Image as a Mat or null.
     */
    @Override
    public Mat getFrame() {
        final var now = Instant.now();
        final var check = now.plusMillis(getTimeout());
        var read = false;
        while (!read && check.compareTo(Instant.now()) > 0) {
            read = videoCapture.read(mat);
            // If read failed sleep 1/10th of the timeout so as not to kill CPU by looping rapidly
            if (!read) {
                try {
                    TimeUnit.MILLISECONDS.sleep(getTimeout() / 10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        var frame = mat;
        if (!read) {
            frame = null;
        }
        // This delay is useful for video file input where you want the FPS simmulated
        if (fps > 0) {
            final var sleepTime = delay - ChronoUnit.MILLIS.between(now, Instant.now());
            if (sleepTime > 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(sleepTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }            }
        }
        return frame;
    }

    /**
     * Release VideoCapture.
     */
    @Override
    public void close() {
        mat.release();
        mat = null;
        videoCapture.release();
    }
}
