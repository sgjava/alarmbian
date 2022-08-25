/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.type;

import org.opencv.core.Mat;

/**
 * Handle various detection.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class Detect {

    /**
     * Image width.
     */
    private int width = 0;
    /**
     * Image height.
     */
    private int height = 0;

    /**
     * Get image width.
     *
     * @return Image width.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Set image width.
     *
     * @param width Image width.
     */
    public void setWidth(final int width) {
        this.width = width;
    }

    /**
     * Set image height.
     *
     * @return Image height.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Set image height.
     *
     * @param height Image height.
     */
    public void setHeight(final int height) {
        this.height = height;
    }

    /**
     * Initialize detection.
     *
     * @param mat Mat used for initialization.
     */
    public void init(final Mat mat) {
        width = mat.width();
        height = mat.height();
    }

    /**
     * Run detection related code on frame.
     *
     * @param mat Mat to do motion detect on.
     */
    public abstract void detect(final Mat mat);

    /**
     * Handle clean up.
     */
    public abstract void done();
}
