/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm;

import com.codeferm.alarmbian.EventData;
import com.codeferm.alarmbian.App;
import static com.codeferm.alarmbian.type.EventType.START_UP;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

/**
 * This boots container and starts App event loop.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@SpringBootApplication
@Slf4j
public class Boot implements CommandLineRunner {

    @Autowired
    private ApplicationContext context;
    /**
     * Event publisher.
     */
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    /**
     * App bean.
     */
    @Autowired
    private App app;

    /**
     * Run app.
     *
     * @param args Arguments from main.
     */
    @Override
    public void run(final String... args) {
        // Notify listeners we are starting up
        applicationEventPublisher.publishEvent(new EventData<>(START_UP, Instant.now(), this.getClass().getCanonicalName()));
        // Event loop
        app.run();
        // Clean shutdown.
        SpringApplication.exit(context);
    }

    /**
     * Main.
     *
     * @param args Arguments from from command line.
     */
    public static void main(final String[] args) {
        // Load the OpenCV system library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        SpringApplication.run(Boot.class, args);
    }
}
