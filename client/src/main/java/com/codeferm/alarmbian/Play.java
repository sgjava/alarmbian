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
 * Alarmbian play business logic for managing composite video playback
 * timelines.
 * <p>
 * This component correlates internal recording blocks with external smart
 * hardware ingress alerts to supply organized chronological datasets to the
 * user interface layers.
 * </p>
 *
 * @author Steven P. Goldsmith
 * @version 1.1.0
 * @since 1.0.0
 */
@Component
@Slf4j
public class Play {

    /**
     * Unique identification tag associated with the targeted source hardware
     * node.
     */
    @Value("${device.name}")
    @Getter
    private String deviceName;

    /**
     * Data access service layer running localized relational persistence
     * queries.
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
     * Complete collection of supported smart hardware event types processed by
     * the UI client.
     */
    private final String[] smartSmtpEvents = new String[]{
        EventType.SMTP_PEOPLE.name(),
        EventType.SMTP_VEHICLE.name(),
        EventType.SMTP_ANIMAL.name()
    };

    /**
     * Maps an independent historical SMTP attachment record directly onto a
     * localized video streaming slice.
     *
     * @param map Target reference collection index holding processed composite
     * files.
     * @param startVideo Root local video recording execution entry marker.
     * @param smtpEvent Targeted hardware classification notification record.
     */
    public void createAndAddEvents(final Map<String, List<Event>> map, final Event startVideo, final Event smtpEvent) {
        final var videoIn = startVideo.getEventTime().toInstant().minus(Duration.ofSeconds(playBefore));
        final var videoOut = startVideo.getEventTime().toInstant().plus(Duration.ofSeconds(playAfter));
        final var smtpTime = smtpEvent.getEventTime().toInstant();

        if (smtpTime.isAfter(videoIn) && smtpTime.isBefore(videoOut)) {
            final var key = startVideo.getEventData();
            var list = map.get(key);
            if (list == null) {
                list = new ArrayList<>();
                map.put(key, list);
            }
            list.add(smtpEvent);
        }
    }

    /**
     * Evaluates database result collections to construct chronological playback
     * timelines.
     *
     * @return Multi-mapped structural payload associating raw video path keys
     * with localized alerts.
     */
    public Map<String, List<Event>> playList() {
        final var images = new HashMap<String, List<Event>>();
        final var videos = findVideos();
        final var events = findSmtpMotionEvents();

        var videosIndex = 0;
        var eventsIndex = 0;

        while (videosIndex < videos.size()) {
            final var startVideo = videos.get(videosIndex);
            if (startVideo.getEventType().equals(EventType.RECORD_START.name())) {
                var stopVideo = (Event) null;
                if (videosIndex + 1 < videos.size()) {
                    final var possibleStop = videos.get(videosIndex + 1);
                    if (possibleStop.getEventType().equals(EventType.RECORD_STOP.name())) {
                        stopVideo = possibleStop;
                        videosIndex++;
                    }
                }

                if (stopVideo != null) {
                    final var videoIn = startVideo.getEventTime().toInstant().minus(Duration.ofSeconds(playBefore));
                    final var videoOut = stopVideo.getEventTime().toInstant().plus(Duration.ofSeconds(playAfter));

                    while (eventsIndex < events.size()) {
                        final var smtpEvent = events.get(eventsIndex);
                        final var smtpTime = smtpEvent.getEventTime().toInstant();

                        if (smtpTime.isBefore(videoIn)) {
                            eventsIndex++;
                            continue;
                        }
                        if (smtpTime.isAfter(videoOut)) {
                            break;
                        }

                        createAndAddEvents(images, startVideo, smtpEvent);
                        eventsIndex++;
                    }
                } else if (videosIndex == videos.size() - 1) {
                    while (eventsIndex < events.size()) {
                        createAndAddEvents(images, startVideo, events.get(eventsIndex));
                        eventsIndex++;
                    }
                }
            } else {
                if (videosIndex == videos.size()) {
                    while (eventsIndex < events.size()) {
                        log.info("No RECORD_STOP found for {}", events.get(eventsIndex));
                        createAndAddEvents(images, startVideo, events.get(eventsIndex));
                        eventsIndex++;
                    }
                }
            }
            videosIndex++;
        }
        return images;
    }

    /**
     * Queries the persistence layer to fetch local video recording boundary
     * events.
     *
     * @return List of Event records.
     */
    public List<Event> findVideos() {
        return eventService.findVideos(deviceName);
    }

    /**
     * Aggregates smart camera notifications across all target classifications.
     * <p>
     * Merges native SMTP records with metadata-driven edge events sourced via
     * timestamp markers to preserve code constraints on low-resource execution
     * runtimes.
     * </p>
     *
     * @return Chronologically sorted List of Event records.
     */
    public List<Event> findSmtpMotionEvents() {
        final var aggregatedEvents = new ArrayList<Event>();
        final var baseEvents = eventService.findSmtpMotionEvents(deviceName);

        if (baseEvents != null) {
            aggregatedEvents.addAll(baseEvents);
        }

        // Use findByTime with an epoch boundary to pull the remaining edge AI events natively
        final var extraEvents = eventService.findByTime(deviceName, Timestamp.valueOf("1970-01-01 00:00:00"));
        if (extraEvents != null) {
            for (final var event : extraEvents) {
                final var type = event.getEventType();
                for (final var smartType : smartSmtpEvents) {
                    if (smartType.equals(type)) {
                        aggregatedEvents.add(event);
                        break;
                    }
                }
            }
        }

        aggregatedEvents.sort((e1, e2) -> e1.getEventTime().compareTo(e2.getEventTime()));
        return aggregatedEvents;
    }

    /**
     * Loads motion buffer descriptors mapped by file name string identifiers.
     *
     * @return Map of file path keys to target Event records.
     */
    public Map<String, Event> loadMotionBuffers() {
        final var bufferMap = new HashMap<String, Event>();
        final var bufferList = eventService.findMotionFiles(deviceName);
        if (bufferList != null) {
            for (final var event : bufferList) {
                bufferMap.put(event.getEventData(), event);
            }
        }
        return bufferMap;
    }

    /**
     * Groups raw timeline records into structural triplets matching UI
     * components.
     *
     * @return Structured list elements grouped by event context.
     */
    public List<List<Event>> loadSmtpMotionEvents() {
        final var uiGroupedList = new ArrayList<List<Event>>();
        final var rawEvents = findSmtpMotionEvents();

        for (final var event : rawEvents) {
            final var group = new ArrayList<Event>();
            group.add(event); // Index 0: Row descriptor display string hook
            group.add(event); // Index 1: Target image payload resolution asset
            group.add(event); // Index 2: End duration processing boundary
            uiGroupedList.add(group);
        }
        return uiGroupedList;
    }

    /**
     * Exposes native standard core camera motion data sequences back out to UI
     * clients.
     *
     * @return Structured timeline dataset layout elements.
     */
    public List<List<Event>> loadMotionEvents() {
        final var uiGroupedList = new ArrayList<List<Event>>();
        final var motionList = eventService.findMotionEvents(deviceName);

        if (motionList != null) {
            var i = 0;
            while (i < motionList.size()) {
                final var startEvent = motionList.get(i);
                if (startEvent.getEventType().equals("MOTION_START")) {
                    var stopEvent = startEvent;
                    if (i + 1 < motionList.size() && motionList.get(i + 1).getEventType().equals("MOTION_STOP")) {
                        stopEvent = motionList.get(i + 1);
                        i++;
                    }
                    final var group = new ArrayList<Event>();
                    group.add(startEvent); // Index 0: Dropdown row selection anchor text
                    group.add(startEvent); // Index 1: Media file resource directory mapping
                    group.add(stopEvent);  // Index 2: Terminal context block bounding range
                    uiGroupedList.add(group);
                }
                i++;
            }
        }
        return uiGroupedList;
    }

    /**
     * Turns a native timestamp structure into a readable string representation.
     *
     * @param timestamp The SQL timestamp data frame.
     * @return Formatted string representation.
     */
    public String formatTimestamp(final Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toString();
    }

    /**
     * Calculates duration differentials between timestamp elements for UI
     * visualization.
     *
     * @param start The initial execution timestamp boundary.
     * @param end The concluding execution timestamp boundary.
     * @return String descriptive frame layout tracking elapsed seconds.
     */
    public String formatDuration(final Timestamp start, final Timestamp end) {
        if (start == null || end == null) {
            return "00:00:00";
        }
        final var duration = Duration.between(start.toInstant(), end.toInstant());
        final var seconds = duration.getSeconds();
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }
}
