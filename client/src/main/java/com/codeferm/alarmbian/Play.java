/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.service.EventService;
import com.codeferm.alarmbian.entity.Event;
import com.codeferm.alarmbian.type.EventType;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Play before event in seconds.
     */
    @Value("${playBefore}")
    private Integer playBefore;
    /**
     * Play after event in seconds.
     */
    @Value("${playAfter}")
    private Integer playAfter;

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
    public List<List<Event>> loadMotionEvents() {
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
    public Map<String, Event> loadMotionBuffers() {
        final var list = findBuffers();
        final var buffers = new HashMap<String, Event>();
        list.forEach(events -> {
            buffers.put(events.getEventData(), events);
        });
        return buffers;
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
     * Create event list like built in motion detection for SMTP events.
     * 
     * @param images List of lists.
     * @param start Video event.
     * @param smtpEvent SMTP event.
     */
    public void createAndAddEvents(List<List<Event>> images, Event start, Event smtpEvent) {
        var subList = new ArrayList<Event>();
        // MOTION_START
        subList.add(new Event(start.getDeviceName(), EventType.MOTION_START.name(), start.getEventData(),
                Timestamp.from(smtpEvent.getEventTime().toInstant().minusSeconds(playBefore))));
        // HISTORY_STOP
        subList.add(new Event(start.getDeviceName(), EventType.HISTORY_STOP.name(), smtpEvent.getEventData(),
                Timestamp.from(smtpEvent.getEventTime().toInstant().plusSeconds(playAfter))));
        // MOTION_STOP
        subList.add(new Event(start.getDeviceName(), EventType.MOTION_STOP.name(), start.getEventData(),
                Timestamp.from(smtpEvent.getEventTime().toInstant().plusSeconds(playAfter))));
        images.add(subList);
    }

    /**
     * Load Map of buffers keyed by file name to match up to motion events event data.
     *
     * @return Map of events by file name.
     */
    public List<List<Event>> loadSmtpMotionEvents() {
        final var videos = findVideos();
        final var events = findSmtpMotionEvents();
        final var images = new ArrayList<List<Event>>();
        var videosIndex = 0;
        var eventsIndex = 0;
        while (eventsIndex < events.size() && videosIndex < videos.size()) {
            // Find the next RECORD_START video event
            while (videosIndex < videos.size() && !videos.get(videosIndex).getEventType().equals("RECORD_START")) {
                log.warn("Expected RECORD_START, but found {}", videos.get(videosIndex).getEventType());
                videosIndex++;
            }
            if (videosIndex >= videos.size()) {
                // No more RECORD_START events to process
                break;
            }
            var startVideo = videos.get(videosIndex);
            // Advance SMTP events past video start time
            while (eventsIndex < events.size() && events.get(eventsIndex).getEventTime().before(startVideo.getEventTime())) {
                eventsIndex++;
            }
            if (eventsIndex >= events.size()) {
                // No more SMTP events to process
                break;
            }
            // Find corresponding RECORD_STOP and process associated SMTP events
            videosIndex++; // Check the event immediately following the RECORD_START

            if (videosIndex < videos.size() && startVideo.getEventData().equals(videos.get(videosIndex).getEventData())) {
                // CFound matching RECORD_STOP
                var stopVideo = videos.get(videosIndex);
                // Process all SMTP events before the video stop time
                while (eventsIndex < events.size() && events.get(eventsIndex).getEventTime().before(stopVideo.getEventTime())) {
                    createAndAddEvents(images, startVideo, events.get(eventsIndex));
                    eventsIndex++;
                }
                // videosIndex will be incremented at the end of the loop
            } else {
                // No matching RECORD_STOP found (or videosIndex is out of bounds/mismatch)
                if (videosIndex == videos.size()) {
                    // EOF reached, process remaining SMTP events
                    while (eventsIndex < events.size()) {
                        log.info("No RECORD_STOP found for {}", events.get(eventsIndex));
                        createAndAddEvents(images, startVideo, events.get(eventsIndex));
                        eventsIndex++;
                    }
                }
                // If videosIndex is valid but the events don't match, we fall through and increment videosIndex at the loop end,
                // effectively skipping the current `startVideo` and the non-matching event at `videosIndex`.
            }
            videosIndex++; // Move to the next event in the videos list.
        }
        return images;
    }

    /**
     * Get videos with start/stop time.
     *
     * @return List of Events.
     */
    public List<Event> findVideos() {
        return eventService.findVideos(deviceName);
    }

    /**
     * Get SMTP motion events for start/stop and composite image.
     *
     * @return List of Events.
     */
    public List<Event> findSmtpMotionEvents() {
        return eventService.findSmtpMotionEvents(deviceName);
    }
}
