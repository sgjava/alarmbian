/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.image;

import com.codeferm.alarmbian.type.Convert;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;

/**
 * Convert Mat to supported image format. We reuse the same Mat every time, so as not to leak heap and native memory because the
 * crappy OpenCV bindings rely on Finalizer to clean things up. Therefore, this this class is not thread safe.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
public class MatToImage extends Convert<Mat, byte[]> {

    /**
     * Reuse mat to prevent heap and native memory leaks.
     */
    private MatOfByte mat;
    private MatOfInt params;

    /**
     * Determines image format.
     */
    private String extension;

    public String getExtension() {
        return extension;
    }

    public MatToImage setExtension(final String extension) {
        this.extension = extension;
        return this;
    }

    /**
     * Initialize return Mat.
     */
    public void init() {
        mat = new MatOfByte();
        params = new MatOfInt();
    }

    /**
     * This may be slower than using new MatOfByte, but this will not leak heap
     * and native memory.
     *
     * @param source Raw JPEG format image.
     * @return Mat format image.
     */
    @Override
    public byte[] execute(final Mat source) {
        Imgcodecs.imencode(".jpg", source, mat, params);
        return mat.toArray();
    }

    /**
     * Release Mat memory.
     */
    public void done() {
        mat.release();
        params.release();
    }
}
