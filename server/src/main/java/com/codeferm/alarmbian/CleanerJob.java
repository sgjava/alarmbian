/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.service.EventService;
import com.codeferm.alarmbian.entity.Event;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Clean up files, directories and database records based on age.
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
public class CleanerJob {

    /**
     * Device name.
     */
    @Value("${device.name}")
    private String deviceName;
    /**
     * Maximum file age in milliseconds.
     */
    @Value("${device.clean.age}")
    private Long age;
    /**
     * Persist events.
     */
    @Autowired
    private EventService eventService;

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
     * Delete files using event data and return set of dirs.
     *
     * @param list Event list.
     * @return Unique dirs.
     */
    public Set<String> deleteFiles(final List<Event> list) {
        log.info(String.format("Deleting %d files", list.size()));
        final var set = new HashSet<String>();
        list.forEach(event -> {
            final var fileName = event.getEventData();
            try {
                final var result = Files.deleteIfExists(Paths.get(fileName));
                if (!result) {
                    log.error(String.format("Error deleting %s", fileName));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // Add dir to set
            final var dir = fileName.substring(0, fileName.lastIndexOf(File.separator));
            set.add(dir);
        });
        return set;
    }

    /**
     * Delete empty dirs.
     *
     * @param set Unique dirs.
     */
    public void deleteEmptyDirs(final Set<String> set) {
        set.forEach(string -> {
            File file = new File(string);
            if (file.delete()) {
                log.info(String.format("%s deleted", string));
            } else {
                log.info(String.format("%s not deleted", string));
            }
        });
    }

    /**
     * Clean files, dirs and DB records.
     */
    @Scheduled(fixedDelayString = "${device.clean.runtime}", initialDelayString = "${device.clean.runtime}")
    public void clean() {
        final var timestamp = new Timestamp(System.currentTimeMillis() - age);
        final var list = eventService.findByTime(deviceName, timestamp);
        if (!list.isEmpty()) {
            deleteEmptyDirs(deleteFiles(list));
            log.info(String.format("%d records deleted", eventService.deleteByTime(deviceName, timestamp)));
        } else {
            log.info("No files to delete");
        }
    }
}
