/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.type;

/**
 * Type of event emitted by various classes.
 * <p>
 * @author sgoldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
public enum EventType {

    /**
     * Start up.
     */
    START_UP,
    /**
     * Shut down.
     */
    SHUT_DOWN,
    /**
     * BufferedImage frame.
     */
    BUF_IMG_FRAME,
    /**
     * Mat frame.
     */
    MAT_FRAME,
    /**
     * Frame error.
     */
    FRAME_ERROR,
    /**
     * Start recording.
     */
    RECORD_START,
    /**
     * Stop recording.
     */
    RECORD_STOP,
    /**
     * Start motion.
     */
    MOTION_START,
    /**
     * Start motion entity.
     */
    MOTION_START_ENTITY,
    /**
     * Stop motion.
     */
    MOTION_STOP,
    /**
     * Motion in effect.
     */
    MOTION_FRAME,
    /**
     * When motion percent resets due to maximum percent change.
     */
    MOTION_RESET,
    /**
     * Start of motion history.
     */
    HISTORY_START,
    /**
     * End of motion history.
     */
    HISTORY_STOP,
    /**
     * Motion in effect history.
     */
    HISTORY_FRAME,
    /**
     * When motion percent resets due to maximum percent change.
     */
    HISTORY_RESET,
    /**
     * SMTP motion event.
     */
    SMTP_MOTION,
    /**
     * Smart camera edge-side AI classification tracking a person or human
     * profile.
     */
    SMTP_PEOPLE,
    /**
     * Smart camera edge-side AI classification tracking a vehicle, car, or
     * automobile.
     */
    SMTP_VEHICLE,
    /**
     * Smart camera edge-side AI classification tracking an animal, domestic
     * pet, or wildlife.
     */
    SMTP_ANIMAL
}
