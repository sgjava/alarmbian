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
 * Alarmbian playback management logic. Handles chronological timeline
 * collation, video file mapping, and SMTP sequence conversion boundaries.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Slf4j
public class Play {

    /**
     * Device name configuration asset.
     */
    @Value("${device.name}")
    @Getter
    private String deviceName;

    /**
     * Data access service layer for database persistence operations.
     */
    @Autowired
    private EventService eventService;

    /**
     * Lead time padding before an event window in seconds.
     */
    @Value("${playBefore}")
    private Integer playBefore;

    /**
     * Trailing time padding after an event window in seconds.
     */
    @Value("${playAfter}")
    private Integer playAfter;

    /**
     * Standard hardware motion sequence tokens.
     */
    private final String[] motionEvents = new String[]{"MOTION_START", "HISTORY_STOP", "MOTION_STOP"};

    /**
     * Format timestamp to a standardized human readable format string.
     *
     * @param timestamp System source timestamp record.
     * @return Formatted localized representation.
     */
    public String formatTimestamp(final Timestamp timestamp) {
        return String.format("%1$TD %1$Tr", timestamp);
    }

    /**
     * Calculate and display chronological window delta using HH:MM:SS format
     * notation.
     *
     * @param start Lower bound timestamp mark.
     * @param end Upper bound timestamp mark.
     * @return Formatted duration interval metric string.
     */
    public String formatDuration(final Timestamp start, final Timestamp end) {
        final var seconds = Duration.between(start.toInstant(), end.toInstant()).toSeconds();
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, (seconds % 60));
    }

    /**
     * Retrieve hardware motion sequence logs bound by target device
     * configurations.
     *
     * @return Unfiltered source database entity list records.
     */
    public List<Event> findMotionEvents() {
        return eventService.findMotionEvents(deviceName);
    }

    /**
     * Map sequence tracking blocks confirming alignment integrity across
     * metrics.
     *
     * @return Structured lists containing standardized multi-event frame
     * arrays.
     */
    public List<List<Event>> loadMotionEvents() {
        final var list = findMotionEvents();
        final var images = new ArrayList<List<Event>>();
        var subList = new ArrayList<Event>();
        var subListSeq = 0;

        for (final var events : list) {
            if (events.getEventType().equals(motionEvents[subListSeq])) {
                subList.add(events);
                if (subListSeq == 2) {
                    images.add(subList);
                    subList = new ArrayList<Event>();
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
     * Extract active background video storage frames associated with target
     * operations.
     *
     * @return Array collection of tracking entries.
     */
    public List<Event> findBuffers() {
        return eventService.findBuffers(deviceName);
    }

    /**
     * Construct lookup mappings referencing tracking files linked directly to
     * storage assets.
     *
     * @return Unique filename-to-entity reference dictionary object.
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
     * Pull database list markers for targeted system files.
     *
     * @return Target file logs array map.
     */
    public List<Event> findMotionFiles() {
        return eventService.findMotionFiles(deviceName);
    }

    /**
     * Fabricate simulated three-part tracking frameworks for raw standalone
     * SMTP elements.
     *
     * @param images Output multi-tier event configuration collection.
     * @param start Base video sequence marker context.
     * @param smtpEvent Raw source image transmission log token.
     */
    public void createAndAddEvents(final List<List<Event>> images, final Event start, final Event smtpEvent) {
        final var subList = new ArrayList<Event>();
        subList.add(new Event(start.getDeviceName(), EventType.MOTION_START.name(), start.getEventData(),
                Timestamp.from(smtpEvent.getEventTime().toInstant().minusSeconds(playBefore))));
        subList.add(new Event(start.getDeviceName(), EventType.HISTORY_STOP.name(), smtpEvent.getEventData(),
                Timestamp.from(smtpEvent.getEventTime().toInstant().plusSeconds(playAfter))));
        subList.add(new Event(start.getDeviceName(), EventType.MOTION_STOP.name(), start.getEventData(),
                Timestamp.from(smtpEvent.getEventTime().toInstant().plusSeconds(playAfter))));
        images.add(subList);
    }

    /**
     * Map SMTP tracking frames across multi-file recording intervals. Resolves
     * timeline processing issues when hardware files miss explicit stop tokens.
     *
     * @return Normalized tracking records arranged chronologically.
     */
    public List<List<Event>> loadSmtpMotionEvents() {
        final var videos = findVideos();
        final var events = findSmtpMotionEvents();
        final var images = new ArrayList<List<Event>>();

        if (videos.isEmpty() || events.isEmpty()) {
            return images;
        }

        var eventsIndex = 0;

        // Linear state process through sequential video timeline tracking rows
        for (var i = 0; i < videos.size(); i++) {
            final var currentVideo = videos.get(i);

            if (!currentVideo.getEventType().equals("RECORD_START")) {
                continue;
            }

            // Map upper temporal limits for tracking boundaries based on next entry data
            var boundaryTime = (Timestamp) null;
            if (i + 1 < videos.size()) {
                final var nextTrack = videos.get(i + 1);
                boundaryTime = nextTrack.getEventTime();

                // If it is an explicit stop event matching this video container context, consume it
                if (nextTrack.getEventType().equals("RECORD_STOP") && currentVideo.getEventData().equals(nextTrack.getEventData())) {
                    i++;
                }
            }

            // Advance over lingering SMTP records logged prior to the current capture timeline window
            while (eventsIndex < events.size() && events.get(eventsIndex).getEventTime().before(currentVideo.getEventTime())) {
                eventsIndex++;
            }

            // Collate all raw SMTP items falling cleanly inside this recording container bracket
            while (eventsIndex < events.size()) {
                final var currentSmtp = events.get(eventsIndex);

                // Stop evaluation if matching entry breaches active timeline limits
                if (boundaryTime != null && !currentSmtp.getEventTime().before(boundaryTime)) {
                    break;
                }

                createAndAddEvents(images, currentVideo, currentSmtp);
                eventsIndex++;
            }
        }
        return images;
    }

    /**
     * Extract full video configuration arrays containing recording boundaries.
     *
     * @return Collection array list.
     */
    public List<Event> findVideos() {
        return eventService.findVideos(deviceName);
    }

    /**
     * Extract raw standalone tracking transmissions arriving from external
     * hardware.
     *
     * @return SMTP operational dataset list.
     */
    public List<Event> findSmtpMotionEvents() {
        return eventService.findSmtpMotionEvents(deviceName);
    }
}
