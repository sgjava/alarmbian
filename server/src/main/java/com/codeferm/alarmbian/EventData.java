/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.type.EventType;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

/**
 * Typed event used by publisher/listener. This should be considered thread safe and immutable. However the constructing code should
 * take care because classes like Mat can mutated using something like getData() unless you always make a copy.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 * @param <T> Event data type.
 */
@Getter
@ToString
public class EventData<T> implements ResolvableTypeProvider {

    /**
     * Type.
     */
    private final EventType eventType;
    /**
     * Timestamp.
     */
    private final Instant timestamp;
    /**
     * Data.
     */
    private final T data;

    /**
     * Writable fields constructor.
     *
     * @param eventType Event type.
     * @param timestamp Event timestamp.
     * @param data Event data.
     */
    @Builder    
    public EventData(final EventType eventType, final Instant timestamp, final T data) {
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.data = data;
    }

    @Override
    public ResolvableType getResolvableType() {
        ResolvableType type = null;
        if (data != null) {
            type = ResolvableType.forClassWithGenerics(getClass(), data.getClass());
        }
        return type;
    }
}
