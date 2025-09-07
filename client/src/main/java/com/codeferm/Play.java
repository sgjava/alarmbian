/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm;

import com.codeferm.alarmbian.service.EventService;
import com.codeferm.alarmbian.entity.Event;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
     * Format timestamp to something human readable.
     *
     * @param timestamp Input timestamp.
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
        return eventService.findBuffers(deviceName);
    }
}
