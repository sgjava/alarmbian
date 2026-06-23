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
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Alarmbian playback management logic. Handles chronological timeline collation, video file mapping, and SMTP sequence conversion
 * boundaries.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Slf4j
public class Play {

    /**
     * Target active device name workspace identifier configuration asset.
     */
    @Getter
    @Setter
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
     * Managed SMTP classification types populated straight from system properties.
     */
    @Value("#{'${smtp.ui.types}'.split(',')}")
    @Getter
    private List<String> smtpUiTypes;

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
        if (timestamp == null) {
            return "";
        }
        return String.format("%1$TD %1$Tr", timestamp);
    }

    /**
     * Calculate and display chronological window delta using HH:MM:SS format notation.
     *
     * @param start Lower bound timestamp mark.
     * @param end Upper bound timestamp mark.
     * @return Formatted duration interval metric string.
     */
    public String formatDuration(final Timestamp start, final Timestamp end) {
        if (start == null || end == null) {
            return "00:00:00";
        }
        final var seconds = Duration.between(start.toInstant(), end.toInstant()).toSeconds();
        final var positiveSeconds = Math.max(0, seconds);
        return String.format("%02d:%02d:%02d", positiveSeconds / 3600, (positiveSeconds % 3600) / 60, (positiveSeconds % 60));
    }

    /**
     * Retrieve hardware motion sequence logs bound by target device configurations.
     *
     * @return Unfiltered source database entity list records.
     */
    public List<Event> findMotionEvents() {
        if (deviceName == null || deviceName.isBlank()) {
            return new ArrayList<>();
        }
        return eventService.findMotionEvents(deviceName);
    }

    /**
     * Map sequence tracking blocks confirming alignment integrity across metrics.
     *
     * @return Structured lists containing standardized multi-event frame arrays.
     */
    public List<List<Event>> loadMotionEvents() {
        final var list = findMotionEvents();
        final var images = new ArrayList<List<Event>>();
        if (list == null || list.isEmpty()) {
            return images;
        }

        var subList = new ArrayList<Event>();
        var subListSeq = 0;

        for (final var events : list) {
            if (events.getEventType().equals(motionEvents[subListSeq])) {
                subList.add(events);
                if (subListSeq == 2) {
                    images.add(subList);
                    subList = new ArrayList<>();
                    subListSeq = 0;
                } else {
                    subListSeq++;
                }
            } else {
                log.warn("Event {} {} out of sequence, expected {}, but got {}", events.getId(),
                        formatTimestamp(events.getEventTime()), motionEvents[subListSeq], events.getEventType());
            }
        }
        return images;
    }

    /**
     * Extract active background video storage frames associated with target operations.
     *
     * @return Array collection of tracking entries.
     */
    public List<Event> findBuffers() {
        if (deviceName == null || deviceName.isBlank()) {
            return new ArrayList<>();
        }
        return eventService.findBuffers(deviceName);
    }

    /**
     * Construct lookup mappings referencing tracking files linked directly to storage assets.
     *
     * @return Unique filename-to-entity reference dictionary object.
     */
    public Map<String, Event> loadMotionBuffers() {
        final var list = findBuffers();
        final var buffers = new HashMap<String, Event>();
        if (list != null) {
            list.forEach(events -> {
                if (events.getEventData() != null) {
                    buffers.put(events.getEventData(), events);
                }
            });
        }
        return buffers;
    }

    /**
     * Pull database list markers for targeted system files.
     *
     * @return Target file logs array map.
     */
    public List<Event> findMotionFiles() {
        if (deviceName == null || deviceName.isBlank()) {
            return new ArrayList<>();
        }
        return eventService.findMotionFiles(deviceName);
    }

    /**
     * Fabricate simulated three-part tracking frameworks for raw standalone SMTP elements.
     *
     * @param images Output multi-tier event configuration collection.
     * @param start Base video sequence marker context.
     * @param smtpEvent Raw source image transmission log token.
     */
    public void createAndAddEvents(final List<List<Event>> images, final Event start, final Event smtpEvent) {
        if (start == null || smtpEvent == null) {
            return;
        }
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
     * Map SMTP tracking frames across multi-file recording intervals using the default configuration type.
     *
     * @return Normalized tracking records arranged chronologically.
     */
    public List<List<Event>> loadSmtpMotionEvents() {
        final var targetType = (smtpUiTypes == null || smtpUiTypes.isEmpty()) ? "SMTP_%" : smtpUiTypes.get(0);
        return loadSmtpMotionEvents(targetType);
    }

    /**
     * Map SMTP tracking frames across multi-file recording intervals using a dynamic relational type constraint.
     *
     * @param eventType The specific enum classification type token or wildcard pattern to restrict rows by.
     * @return Normalized tracking records arranged chronologically.
     */
    public List<List<Event>> loadSmtpMotionEvents(final String eventType) {
        final var videos = findVideos();
        final var events = findSmtpMotionEvents(eventType);
        final var images = new ArrayList<List<Event>>();

        if (videos == null || events == null || videos.isEmpty() || events.isEmpty()) {
            return images;
        }

        var eventsIndex = 0;

        for (var i = 0; i < videos.size(); i++) {
            final var currentVideo = videos.get(i);

            if (!"RECORD_START".equals(currentVideo.getEventType())) {
                continue;
            }

            var boundaryTime = (Timestamp) null;
            if (i + 1 < videos.size()) {
                final var nextTrack = videos.get(i + 1);
                boundaryTime = nextTrack.getEventTime();

                if ("RECORD_STOP".equals(nextTrack.getEventType()) && currentVideo.getEventData() != null && currentVideo.getEventData().equals(nextTrack.getEventData())) {
                    i++;
                }
            }

            while (eventsIndex < events.size() && events.get(eventsIndex).getEventTime().before(currentVideo.getEventTime())) {
                eventsIndex++;
            }

            while (eventsIndex < events.size()) {
                final var currentSmtp = events.get(eventsIndex);

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
        if (deviceName == null || deviceName.isBlank()) {
            return new ArrayList<>();
        }
        return eventService.findVideos(deviceName);
    }

    /**
     * Extract raw standalone tracking transmissions arriving from external hardware using default wildcard matching.
     *
     * @return SMTP operational dataset list.
     */
    public List<Event> findSmtpMotionEvents() {
        if (deviceName == null || deviceName.isBlank()) {
            return new ArrayList<>();
        }
        return eventService.findSmtpMotionEvents(deviceName, "SMTP_%");
    }

    /**
     * Extract raw standalone tracking transmissions arriving from external hardware using an exact query restriction token.
     *
     * @param eventType The dynamic SQL restriction token (e.g., "SMTP_VEHICLE", "SMTP_%").
     * @return SMTP operational dataset list matching the specified constraint boundary.
     */
    public List<Event> findSmtpMotionEvents(final String eventType) {
        if (deviceName == null || deviceName.isBlank()) {
            return new ArrayList<>();
        }
        return eventService.findSmtpMotionEvents(deviceName, eventType);
    }
}
