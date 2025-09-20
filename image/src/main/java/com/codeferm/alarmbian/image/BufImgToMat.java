/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.image;

import com.codeferm.alarmbian.type.Convert;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * Convert BufferedImage to Mat. We reuse the same Mat every time, so as not to leak heap and native memory because the crappy
 * OpenCV bindings rely on Finalizer to clean things up. Therefore, this class is not thread safe.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class BufImgToMat extends Convert<BufferedImage, Mat> {

    /**
     * Reuse mat to prevent heap and native memory leaks.
     */
    private Mat mat;

    /**
     * Initialize Mat to color or gray scale depending on BufferedImage source.
     *
     * @param source BufferedImage image.
     */
    public void init(final BufferedImage source) {
        log.debug("init");
        // Grayscale?
        if (source.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            mat = new Mat(source.getHeight(), source.getWidth(), CvType.CV_8UC1);
            // Color
        } else {
            mat = new Mat(source.getHeight(), source.getWidth(), CvType.CV_8UC3);
        }
    }

    /**
     * This may be slower than using new MatOfByte, but this will not leak heap
     * and native memory.
     *
     * @param source Raw JPEG format image.
     * @return Mat format image.
     */
    @Override
    public Mat execute(final BufferedImage source) {
        // Convert buffered image to byte array
        final var data = ((DataBufferByte) source.getRaster().getDataBuffer()).getData();
        // Copy byte array to Mat
        mat.put(0, 0, data);
        return mat;
    }

    /**
     * Release Mat memory.
     */
    public void done() {
        log.debug("done");
        mat.release();
    }
}
