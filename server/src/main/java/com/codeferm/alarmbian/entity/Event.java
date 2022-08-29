/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.entity;

import java.sql.Timestamp;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;

/**
 * Event entity.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class Event {

    /**
     * Database generates primary key.
     */
    @Id
    @Setter(AccessLevel.NONE)
    private Long id;

    @Size(min = 1, max = 50)
    @NotBlank(message = "deviceName is required")
    private String deviceName;

    @Size(min = 1, max = 50)
    @NotBlank(message = "eventType is required")
    private String eventType;

    @Size(min = 0, max = 255)
    private String eventData;

    @NotNull
    private Timestamp eventTime;
     
    /**
     * Writable fields constructor.
     *
     * @param deviceName Device name.
     * @param eventType Event type.
     * @param eventData Event data.
     * @param eventTime Event time.
     */
    @Builder
    public Event(final String deviceName, final String eventType, final String eventData, final Timestamp eventTime) {
        this.deviceName = deviceName;
        this.eventType = eventType;
        this.eventData = eventData;
        this.eventTime = eventTime;
    }
}
