/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import static com.codeferm.alarmbian.type.EventType.SHUT_DOWN;
import java.time.Instant;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Allow App to exit after a time period. Useful for profiling, testing clean up, etc. To not shut down just set device.runtime to
 * something like P1000D (1000 days).
 *
 * See https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html?is-external=true#parse-java.lang.CharSequence-
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
@EnableScheduling
@Slf4j
public class ShutDownJob {

    /**
     * Event publisher.
     */
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    /**
     * Initialize bean.
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
     * Send SHUT_DOWN event.
     */
    @Scheduled(fixedDelayString = "${device.runtime}", initialDelayString = "${device.runtime}")
    public void shutDown() {
        log.info("Sending shutdown event");
        applicationEventPublisher.publishEvent(new EventData<>(SHUT_DOWN, Instant.now(), this.getClass().getCanonicalName()));
    }

}
