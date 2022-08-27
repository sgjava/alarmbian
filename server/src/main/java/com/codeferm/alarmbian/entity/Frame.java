/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.entity;

import java.sql.Timestamp;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.util.Assert;

/**
 * Frame entity.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class Frame {

    /**
     * Database generates primary key.
     */
    @Id
    @Setter(AccessLevel.NONE)
    private Long id;
    @NotNull
    private Long eventId;
    @NotNull
    private Timestamp frameTime;
    /**
     * Detection table.
     */
    @Setter(AccessLevel.NONE)
    @MappedCollection(idColumn = "FRAME_ID", keyColumn = "FRAME_ID")
    private List<Detection> detections;    

    /**
     * Writable fields constructor.
     *
     * @param eventId Event ID.
     * @param frameTime Frame time.
     */
    @Builder
    public Frame(final Long eventId, final Timestamp frameTime) {
        this.eventId = eventId;
        this.frameTime = frameTime;
        detections = new ArrayList<>();
    }
    
    /**
     * Add detection.
     *
     * @param detection Detection entity.
     */
    public void addDetection(final Detection detection) {
        Assert.notNull(id, "Frame ID cannot be null");
        detections.add(detection);
    }    
}
