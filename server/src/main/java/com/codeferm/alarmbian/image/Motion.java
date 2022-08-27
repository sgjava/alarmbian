/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.image;

import com.codeferm.alarmbian.type.Detect;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Motion detector.
 *
 * Uses moving average to determine change percent.
 *
 * @author sgoldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class Motion extends Detect {

    /**
     * Blurring kernel size for blur operation.
     */
    private Size kSize;
    /**
     * Weight of the input image for accumulateWeighted operation.
     */
    private double alpha;
    /**
     * Black threshold for Threshold Binary operation.
     */
    private double blackThreshold;
    /**
     * Maximum threshold for Threshold Binary operation.
     */
    private double maxThreshold;
    /**
     * Percentage of change required to reset reference image.
     */
    private double maxChange;
    /**
     * Perecnt must be >= to trigger motion.
     */
    private double startThreshold;
    /**
     * Percent must be <= to stop motion detect.
     */
    private double stopThreshold;
    /**
     * Work image.
     */
    private final Mat workImg;
    /**
     * Moving average image.
     */
    private Mat movingAvgImg;
    /**
     * Black and white motion image.
     */
    private final Mat bwImg;
    /**
     * Difference image.
     */
    private final Mat diffImg;
    /**
     * Scale image.
     */
    private final Mat scaleImg;
    /**
     * Total pixels in image.
     */
    private double totalPixels;
    /**
     * Motion percent.
     */
    private double motionPercent;
    /**
     * Ignore mask.
     */
    private Mat ignoreMask;

    public Mat getWorkImg() {
        return workImg;
    }

    public Mat getBwImg() {
        return bwImg;
    }

    public Mat getDiffImg() {
        return diffImg;
    }

    public Mat getScaleImg() {
        return scaleImg;
    }

    public Size getkSize() {
        return kSize;
    }

    public Motion setkSize(final Size kSize) {
        this.kSize = kSize;
        return this;
    }

    public double getAlpha() {
        return alpha;
    }

    public Motion setAlpha(final double alpha) {
        this.alpha = alpha;
        return this;
    }

    public double getBlackThreshold() {
        return blackThreshold;
    }

    public Motion setBlackThreshold(final double blackThreshold) {
        this.blackThreshold = blackThreshold;
        return this;
    }

    public double getMaxThreshold() {
        return maxThreshold;
    }

    public Motion setMaxThreshold(final double maxThreshold) {
        this.maxThreshold = maxThreshold;
        return this;
    }

    public double getMaxChange() {
        return maxChange;
    }

    public Motion setMaxChange(final double maxChange) {
        this.maxChange = maxChange;
        return this;
    }

    public double getStartThreshold() {
        return startThreshold;
    }

    public Motion setStartThreshold(final double startThreshold) {
        this.startThreshold = startThreshold;
        return this;
    }

    public double getStopThreshold() {
        return stopThreshold;
    }

    public Motion setStopThreshold(final double stopThreshold) {
        this.stopThreshold = stopThreshold;
        return this;
    }

    public Mat getMovingAvgImg() {
        return movingAvgImg;
    }

    public Motion setMovingAvgImg(final Mat movingAvgImg) {
        this.movingAvgImg = movingAvgImg;
        return this;
    }

    public double getTotalPixels() {
        return totalPixels;
    }

    public Motion setTotalPixels(final double totalPixels) {
        this.totalPixels = totalPixels;
        return this;
    }

    public double getMotionPercent() {
        return motionPercent;
    }

    public Motion setMotionPercent(final double motionPercent) {
        this.motionPercent = motionPercent;
        return this;
    }

    public Mat getIgnoreMask() {
        return ignoreMask;
    }

    public Motion setIgnoreMask(Mat ignoreMask) {
        this.ignoreMask = ignoreMask;
        return this;
    }

    public Motion() {
        bwImg = new Mat();
        workImg = new Mat();
        diffImg = new Mat();
        scaleImg = new Mat();
        motionPercent = 0.0;
    }

    @Override
    public void init(final Mat mat) {
        log.debug("init");
        super.init(mat);
        totalPixels = new Size(getWidth(), getHeight()).area();
    }

    @Override
    public void detect(final Mat mat) {
        // Generate work image by blurring
        Imgproc.blur(mat, workImg, kSize);
        // Generate moving average image if needed
        if (movingAvgImg == null) {
            movingAvgImg = new Mat();
            workImg.convertTo(movingAvgImg, CvType.CV_32F);

        }
        // Generate moving average image
        Imgproc.accumulateWeighted(workImg, movingAvgImg, alpha);
        // Convert the scale of the moving average
        Core.convertScaleAbs(movingAvgImg, scaleImg);
        // Subtract the work image frame from the scaled image average
        Core.absdiff(workImg, scaleImg, diffImg);
        // Convert the image to grayscale
        Imgproc.cvtColor(diffImg, bwImg, Imgproc.COLOR_BGR2GRAY);
        // Convert grayscale to BW
        Imgproc.threshold(bwImg, bwImg, blackThreshold, maxThreshold, Imgproc.THRESH_BINARY);
        // Apply ignore mask
        if (ignoreMask != null) {
            Core.bitwise_and(bwImg, ignoreMask, bwImg);
        }
        // Total number of changed motion pixels
        motionPercent = 100.0 * Core.countNonZero(bwImg) / totalPixels;
        // Detect if camera is adjusting and reset reference if more than maxChange
        if (motionPercent > maxChange) {
            workImg.convertTo(movingAvgImg, CvType.CV_32F);
            log.info(String.format("Motion reset %.2f%%", motionPercent));
        }
    }

    /**
     * Clean up.
     */
    @Override
    public void done() {
        log.debug("done");
        if (ignoreMask != null) {
            ignoreMask.release();
        }
        workImg.release();
        movingAvgImg.release();
        bwImg.release();
        diffImg.release();
        scaleImg.release();
    }
}
