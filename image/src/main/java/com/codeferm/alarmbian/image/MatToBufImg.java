/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.image;

import com.codeferm.alarmbian.type.Convert;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

/**
 * Convert Mat to BufferedImage format. We reuse the same Mat every time, so as not to leak heap and native memory because the
 * crappy OpenCV bindings rely on Finalizer to clean things up. Therefore, this this class is not thread safe.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
public class MatToBufImg extends Convert<Mat, BufferedImage> {

    /**
     * Reuse mat to prevent heap and native memory leaks.
     */
    private MatOfByte mat;

    /**
     * Initialize return Mat.
     */
    public void init() {
        mat = new MatOfByte();
    }

    /**
     * Convert Mat to JPEG image then BufferedImage.
     *
     * @param source Mat format image.
     * @return BufferedImage format image.
     */
    @Override
    public BufferedImage execute(final Mat source) {
        Imgcodecs.imencode(".jpg", source, mat);
        BufferedImage bufferedImage;
        try {
            bufferedImage = ImageIO.read(new ByteArrayInputStream(mat.toArray()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bufferedImage;
    }

    /**
     * Release Mat memory.
     */
    public void done() {
        mat.release();
    }
}
