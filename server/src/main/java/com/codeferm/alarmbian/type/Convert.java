/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.type;

/**
 * Convert image interface.
 *
 * @author Steven P. Goldsmith
 * @param <S> Source type.
 * @param <D> Destination type.
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class Convert<S, D> {
    
    /**
     * Convert image from source type to destination type.
     *
     * @param source Source image.
     * @return Destination image.
     */
    public abstract D execute(final S source);
}
