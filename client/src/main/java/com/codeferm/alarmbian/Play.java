/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.service.EventService;
import com.codeferm.alarmbian.entity.Event;
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
 * Alarmbian playback management logic. Handles chronological timeline collation, video file mapping, and cross-boundary video asset
 * tracking by examining filename payloads instead of relying on a non-existent video enum.
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
     * Explicit start timestamp override string (format: yyyy-MM-dd HH:mm:ss).
     */
    @Getter
    @Setter
    private String manualStartTimestamp;

    /**
     * Explicit playback duration length in seconds.
     */
    @Getter
    @Setter
    private Integer manualLength;

    /**
     * Find all video files that overlap with a requested absolute timestamp window. This serves as the data availability validation
     * layer.
     *
     * @param targetStart Target start timestamp.
     * @param durationSec Requested video duration in seconds.
     * @return Chronologically sorted list of matching video files intersecting the window.
     */
    public List<Event> findOverlappingVideos(final Timestamp targetStart, final int durationSec) {
        final var targetEnd = new Timestamp(targetStart.getTime() + (durationSec * 1000L));
        final var allVideos = findVideos();
        final var matchingVideos = new ArrayList<Event>();

        for (final var video : allVideos) {
            final var videoStart = extractStartTimeFromFilename(video.getEventData());
            final var videoDuration = extractDurationFromFilename(video.getEventData());

            if (videoStart != null && videoDuration != null) {
                final var videoEnd = new Timestamp(videoStart.getTime() + videoDuration.toMillis());

                // Evaluate explicit intersecting temporal boundaries
                if (!videoStart.after(targetEnd) && !videoEnd.before(targetStart)) {
                    matchingVideos.add(video);
                }
            }
        }
        // Enforce chronological sorting sequence
        matchingVideos.sort((v1, v2) -> v1.getEventTime().compareTo(v2.getEventTime()));
        return matchingVideos;
    }

    /**
     * Create composite image mappings based on relative overlap tracking thresholds. Identifies video source assets by validating
     * file paths rather than an explicit enum state.
     *
     * @param events Chronological collection list.
     * @return Transformed lookup collection mapping.
     */
    public Map<String, List<Event>> createImagesMap(final List<Event> events) {
        final var map = new HashMap<String, List<Event>>();
        for (final var event : events) {
            // Identify physical recording rows by scanning data payloads for standard video extensions
            if (event.getEventData() != null && event.getEventData().endsWith(".mkv")) {
                map.put(event.getEventData(), new ArrayList<>());
            }
        }
        for (final var event : events) {
            if (event.getEventData() == null || !event.getEventData().endsWith(".mkv")) {
                for (final var videoFile : map.keySet()) {
                    if (isWithinVideo(event, videoFile)) {
                        map.get(videoFile).add(event);
                    }
                }
            }
        }
        return map;
    }

    /**
     * Evaluate if a tracking transmission overlaps an existing video segment.
     *
     * @param smtp Event tracking metadata block.
     * @param videoFile Physical recording identity link.
     * @return True if collision threshold is confirmed.
     */
    public boolean isWithinVideo(final Event smtp, final String videoFile) {
        final var videoStartTime = extractStartTimeFromFilename(videoFile);
        final var videoDuration = extractDurationFromFilename(videoFile);
        if (videoStartTime == null || videoDuration == null) {
            return false;
        }
        final var videoEndTime = new Timestamp(videoStartTime.getTime() + videoDuration.toMillis());
        final var smtpTime = smtp.getEventTime();
        final var startBoundary = new Timestamp(videoStartTime.getTime() - playBefore * 1000);
        final var endBoundary = new Timestamp(videoEndTime.getTime() + playAfter * 1000);
        return smtpTime.after(startBoundary) && smtpTime.before(endBoundary);
    }

    /**
     * Convert filesystem notation sequences to clean timestamp representations.
     *
     * @param filename Target operational layout tracking string.
     * @return Parsed timestamp instance.
     */
    public Timestamp extractStartTimeFromFilename(final String filename) {
        try {
            final var baseName = filename.substring(filename.lastIndexOf('/') + 1);
            final var parts = baseName.split("_");
            if (parts.length >= 2) {
                final var timestampStr = parts[0] + " " + parts[1].replace('-', ':');
                return Timestamp.valueOf(timestampStr);
            }
        } catch (final Exception e) {
            log.error("Failed to extract start time from filename: {}", filename, e);
        }
        return null;
    }

    /**
     * Parse system filename intervals to generate accurate length intervals.
     *
     * @param filename Target operational layout tracking string.
     * @return Length duration asset.
     */
    public Duration extractDurationFromFilename(final String filename) {
        try {
            final var baseName = filename.substring(filename.lastIndexOf('/') + 1);
            final var parts = baseName.split("_");
            if (parts.length >= 3) {
                final var secondsStr = parts[2].split("\\.")[0];
                return Duration.ofSeconds(Long.parseLong(secondsStr));
            }
        } catch (final Exception e) {
            log.error("Failed to extract duration from filename: {}", filename, e);
        }
        return null;
    }

    /**
     * Execute localized matrix assembly loops to convert recording chunks into visible image arrays.
     *
     * @param images Output accumulator target map.
     * @param video Core event parent block.
     * @param smtp Subordinate signal packet.
     */
    public void createAndAddEvents(final Map<Long, List<Event>> images, final Event video, final Event smtp) {
        final var videoStart = extractStartTimeFromFilename(video.getEventData());
        if (videoStart != null) {
            final var offsetMillis = smtp.getEventTime().getTime() - videoStart.getTime();
            final var frameNumber = (offsetMillis / 1000) * 10;
            images.computeIfAbsent(frameNumber, k -> new ArrayList<>()).add(smtp);
        }
    }

    /**
     * Collate parallel active timeline tracks into unified, indexed execution blocks.
     *
     * @param images Output frame reference target map.
     * @param videos File system descriptor tracks.
     * @param smtps Network alert tracking frames.
     * @param boundaryTime Upper threshold limits.
     * @return Populated operational index map.
     */
    public Map<Long, List<Event>> processEvents(final Map<Long, List<Event>> images, final List<Event> videos, final List<Event> smtps, final Timestamp boundaryTime) {
        final var imagesMap = createImagesMap(combineAndSort(videos, smtps));
        for (final var entry : imagesMap.entrySet()) {
            final var currentVideo = findVideoEvent(videos, entry.getKey());
            if (currentVideo == null) {
                continue;
            }
            final var events = entry.getValue();
            var eventsIndex = 0;
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
     * Combine two lists of events and sort them chronologically.
     *
     * @param list1 First event list.
     * @param list2 Second event list.
     * @return Chronologically sorted master list.
     */
    private List<Event> combineAndSort(final List<Event> list1, final List<Event> list2) {
        final var combined = new ArrayList<Event>(list1.size() + list2.size());
        combined.addAll(list1);
        combined.addAll(list2);
        combined.sort((e1, e2) -> e1.getEventTime().compareTo(e2.getEventTime()));
        return combined;
    }

    /**
     * Find a video event matching a specific filename data payload token.
     *
     * @param videos Video track candidate items.
     * @param filename Target matching string token.
     * @return Matching event block, or null if missing.
     */
    private Event findVideoEvent(final List<Event> videos, final String filename) {
        for (final var video : videos) {
            if (video.getEventData().equals(filename)) {
                return video;
            }
        }
        return null;
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
     * Extract raw standalone tracking transmissions arriving from external hardware using default wildcard matching.
     *
     * @return SMTP operational dataset list.
     */
    public List<Event> findSmtpMotionEvents() {
        return eventService.findSmtpMotionEvents(deviceName, "SMTP_%");
    }
}
