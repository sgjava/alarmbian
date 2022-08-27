/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.type;

/**
 * Video source controls opening, closing, getting frames, etc.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class VideoSource {

    /**
     * Image height.
     */
    private int height;

    /**
     * Image width.
     */
    private int width;
    
    /**
     * Open/read timeout in milliseconds.
     */
    private int timeout;    

    public int getHeight() {
        return height;
    }

    public VideoSource setHeight(final int height) {
        this.height = height;
        return this;
    }

    public int getWidth() {
        return width;
    }

    public VideoSource setWidth(final int width) {
        this.width = width;
        return this;
    }
    
    public int getTimeout() {
        return timeout;
    }

    public VideoSource setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }    

    /**
     * Device can be a camera URL, file name, V4L device number, etc. Implementation should handle any String validation and
     * conversion as needed.
     *
     * @param device String representation of device.
     * @return True on success and false on failure.
     */
    public abstract boolean open(final String device);

    /**
     * Close device and do any required clean up.
     */
    public abstract void close();

    /**
     * Return typed frame.
     *
     * @param <T> Type of frame.
     * @return Frame.
     */
    public abstract <T> T getFrame();
}
