/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.type;

import java.time.Instant;

/**
 * Handle various recording.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class Record {

    /**
     * Initialize and start recording.
     *
     * @param timestamp Timestamp of start.
     */
    public abstract void start(final Instant timestamp);

    /**
     * Stop recording and handle clean up.
     *
     * @param timestamp Timestamp of stop.
     */
    public abstract void stop(final Instant timestamp);
}
