/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm;

import com.codeferm.alarmbian.service.EventService;
import com.codeferm.alarmbian.entity.Event;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Alarmbian play code. This is the non UI logic.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Slf4j
public class Play {

    /**
     * Device name.
     */
    @Value("${device.name}")
    @Getter
    private String deviceName;
    /**
     * Persist events.
     */
    @Autowired
    private EventService eventService;
    /**
     * Motion event sequence
     */
    final String[] motionEvents = new String[]{"MOTION_START", "HISTORY_STOP", "MOTION_STOP"};

    /**
     * Format timestamp to something human readable.
     *
     * @param timestamp Input timestamp.
     * 
     * @return Formatted String.
     */
    public String formatTimestamp(final Timestamp timestamp) {
        return String.format("%1$TD %1$Tr", timestamp);
    }

    /**
     * Return HH:MM:SS from two Timestamps.
     *
     * @param start Start timestamp.
     * @param end End timestamp.
     * 
     * @return String formatted to HH:MM:SS.
     */
    public String formatDuration(final Timestamp start, final Timestamp end) {
        final var seconds = Duration.between(start.toInstant(), end.toInstant()).toSeconds();
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, (seconds % 60));
    }

    /**
     * Get motion events for start/stop and composite image.
     *
     * @return List of Events.
     */
    public List<Event> findMotionEvents() {
        return eventService.findMotionEvents(deviceName);
    }

    /**
     * Load motion events and verify sequence. Warnings logged for out of sequence events.
     *
     * @return List of List of events.
     */
    public List<List<Event>> loadEvents() {
        final var list = findMotionEvents();
        final var images = new ArrayList<List<Event>>();
        var subList = new ArrayList<Event>();
        var subListSeq = 0;
        for (final var events : list) {
            // Make sure sequence matches
            if (events.getEventType().equals(motionEvents[subListSeq])) {
                subList.add(events);
                // Last item in list
                if (subListSeq == 2) {
                    images.add(subList);
                    subList = new ArrayList<>();
                    subListSeq = 0;
                } else {
                    subListSeq++;
                }
            } else {
                log.warn(String.format("Event %d %s out of sequence, expected %s, but got %s", events.getId(),
                        formatTimestamp(events.getEventTime()), motionEvents[subListSeq], events.getEventType()));
            }
        }
        return images;
    }

    /**
     * Find all buffer files by device name.
     *
     * @return List of Events.
     */
    public List<Event> findBuffers() {
        return eventService.findBuffers(deviceName);
    }

    /**
     * Load Map of buffers keyed by file name to match up to motion events event data.
     *
     * @return Map of events by file name.
     */
    public Map<String, Event> loadBuffers() {
        final var list = findBuffers();
        final var buffers = new HashMap<String, Event>();
        list.forEach(events -> {
            buffers.put(events.getEventData(), events);
        });
        return buffers;
    }

    /**
     * Return list of File recursive.
     *
     * @param path Path to start.
     * @param fromPath Replace from substring.
     * @param toPath Replace to substring.
     * 
     * @return List of File.
     */
    public Set<String> getFiles(final String path, final String fromPath, final String toPath) {
        Set<String> files = null;
        try (Stream<Path> walk = Files.walk(Paths.get(path))) {
            files = walk.filter(Files::isRegularFile).map(x -> x.toString().replace(fromPath, toPath)).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return files;
    }

    /**
     * Find motion records.
     *
     * @return List of motion records.
     */
    public List<Event> findMotionFiles() {
        return eventService.findMotionFiles(deviceName);
    }

    /**
     * Load pre-configured Mat from a file.
     *
     * @param mat Mat configured the same as the saved Mat. This Mat will be overwritten with the data in the file. This value is
     * modified by JNI code.
     *
     * @param fileName File to read.
     */
    public void loadDoubleMat(final Mat mat, final String fileName) {
        log.info(String.format("Loading double Mat %s", fileName));
        final var count = mat.total() * mat.channels();
        final List<Double> list = new ArrayList<>();
        Path path = Paths.get(fileName);
        InputStream inStream = null;
        // Load from file path
        if (Files.exists(path)) {
            try {
                inStream = new FileInputStream(fileName);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else {
            // Load from classpath
            inStream = Play.class.getClassLoader().getResourceAsStream(fileName);
        }
        try (final var dataStream = new DataInputStream(inStream)) {
            // Read all Doubles into List
            for (var i = 0; i < count; ++i) {
                log.debug(String.format("%d", i));
                list.add(dataStream.readDouble());
            }
        } catch (IOException e) {
            if (e.getMessage() == null) {
                log.info(String.format("EOF reached for %s", fileName));
            } else {
                log.error(String.format("Exception %s", e.getMessage()));
            }
        }
        // Set byte array to size of List
        final var buff = new double[list.size()];
        // Convert to primitive array
        for (var i = 0; i < buff.length; i++) {
            buff[i] = list.get(i);
        }
        mat.put(0, 0, buff);
    }

    /**
     * Load calibration Mats.
     *
     * @param camMtxFileName
     * Camera matrix file name.
     * @param distCoFileName
     * Distortion coefficients file name.
     * @return Mat array consisting of cameraMatrix and distCoeffs.
     */
    public Mat[] loadCalibrate(final String camMtxFileName, final String distCoFileName) {
        final var cameraMatrix = Mat.eye(3, 3, CvType.CV_64F);
        loadDoubleMat(cameraMatrix, camMtxFileName);
        final var distCoeffs = Mat.zeros(5, 1, CvType.CV_64F);
        loadDoubleMat(distCoeffs, distCoFileName);
        return new Mat[]{cameraMatrix, distCoeffs};
    }
    
    /**
     * Undistort image.
     *
     * @param image
     * Distorted image.
     * @param cameraMatrix
     * Camera matrix.
     * @param distCoeffs
     * Input vector of distortion coefficients.
     * @return Undistorted image.
     */
    public Mat undistort(final Mat image, final Mat cameraMatrix, final Mat distCoeffs) {
        final var newCameraMtx = Calib3d.getOptimalNewCameraMatrix(cameraMatrix, distCoeffs, image.size(), 0);
        final var mat = new Mat();
        Calib3d.undistort(image, mat, cameraMatrix, distCoeffs, newCameraMtx);
        return mat;
    }    
}
