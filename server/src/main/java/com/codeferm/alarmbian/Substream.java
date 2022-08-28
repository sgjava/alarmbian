/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.image.FfmpegIn;
import com.codeferm.alarmbian.image.VideoIn;
import com.codeferm.alarmbian.type.VideoSource;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Use camera substream is used for detection and AI. FPS should be around 3 or 4 and resolution 640x480.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Slf4j
public class Substream {

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
     * Device name.
     */
    @Value("${device.name}")
    private String deviceName;
    /**
     * Video source.
     */
    private VideoSource videoSource;
    /**
     * Open/read timeout in milliseconds.
     */
    @Value("${substream.timeout}")
    private int timeout;

    /**
     * Initialize.
     */
    @PostConstruct
    public void init() {
        log.debug("init");
    }

    /**
     * Clean up.
     */
    @PreDestroy
    public void done() {
        log.debug("done");
    }

    /**
     * Open video source.
     */
    public void open() {
        // Setup video source for substream
        log.info(String.format("Starting substream %s", env.getProperty("substream.name")));
        try {
            videoSource = (VideoSource) Class.forName(env.getProperty("substream.class")).getDeclaredConstructor().
                    newInstance();
            videoSource.setTimeout(timeout);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException
                | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        // If using FfmpegIn class add necessary settings
        if (videoSource instanceof FfmpegIn ffmpegIn) {
            // Convert ffmpeg arguments into Map
            final var inArgMap = new LinkedHashMap<String, String>();
            config.getProperties("substream.input.arg", inArgMap);
            ffmpegIn.setBin(env.getProperty("ffmpeg.bin")).setInputArgs(inArgMap);
        } else if (videoSource instanceof VideoIn videoIn) {
            // Set FPS if VideoIn used
            videoIn.setFps(Integer.parseInt(env.getProperty("substream.fps")));
        }
        videoSource.open(env.getProperty("substream.device"));
    }

    /**
     * Close video source.
     */
    public void close() {
        videoSource.close();
    }

    /**
     * Return image as a BufferedImage.
     *
     * @param <T> Type of frame.
     * @return Frame..
     */
    public <T> T getFrame() {
        return videoSource.getFrame();
    }
}
