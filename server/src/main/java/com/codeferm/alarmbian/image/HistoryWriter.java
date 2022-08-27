/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.image;

import com.codeferm.alarmbian.type.Convert;
import com.codeferm.alarmbian.EventData;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * Write off motion history image. This can be used to generate ignore area masks.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class HistoryWriter {

    /**
     * History image.
     */
    private Mat mat;
    /**
     * Path to save file to.
     */
    private String path;
    /**
     * Image converter.
     */
    private Convert<Mat, byte[]> convert;
    /**
     * DateTimeFormatter pattern used as part of file name.
     */
    private String dirPattern;
    /**
     * DateTimeFormatter pattern used as part of file name.
     */
    private String filePattern;
    /**
     * Directory name formatter.
     */
    private DateTimeFormatter dirFormatter;
    /**
     * File name formatter.
     */
    private DateTimeFormatter fileFormatter;
    /**
     * Timestamp to use in file name.
     */
    private Instant timestamp;

    public Mat getMat() {
        return mat;
    }

    public HistoryWriter setMat(final Mat mat) {
        this.mat = mat;
        return this;
    }

    public String getPath() {
        return path;
    }

    public HistoryWriter setPath(final String path) {
        this.path = path;
        return this;
    }

    public Convert<Mat, byte[]> getConvert() {
        return convert;
    }

    public HistoryWriter setConvert(final Convert<Mat, byte[]> convert) {
        this.convert = convert;
        return this;
    }

    public String getDirPattern() {
        return dirPattern;
    }

    public HistoryWriter setDirPattern(final String dirPattern) {
        this.dirPattern = dirPattern;
        dirFormatter = DateTimeFormatter.ofPattern(dirPattern).withZone(ZoneId.systemDefault());
        return this;
    }

    public String getFilePattern() {
        return filePattern;
    }

    public HistoryWriter setFilePattern(final String filePattern) {
        this.filePattern = filePattern;
        fileFormatter = DateTimeFormatter.ofPattern(filePattern).withZone(ZoneId.systemDefault());
        return this;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public HistoryWriter setTimestamp(final Instant timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public void init(final Mat source) {
        log.debug("init");
        mat = Mat.zeros(source.height(), source.width(), CvType.CV_8UC1);
    }

    /**
     * Release Mat memory.
     */
    public void done() {
        log.debug("done");
        ((MatToImage) convert).done();
        mat.release();
    }

    /**
     * Save history image to file.
     *
     * @param event Mat event.
     * @return File name.
     */
    public String saveHistoryImage(final EventData<Mat> event) {
        // Construct directory name
        final var dirName = String.format("%s%s%s", path, File.separator, dirFormatter.format(Instant.now()));
        // Create dir
        try {
            Files.createDirectories(Paths.get(dirName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Construct file name 
        final var fileName = String.format("%s%s%s-motion%s", dirName, File.separator, fileFormatter.format(timestamp),
                ((MatToImage) convert).getExtension());
        log.info(String.format("Saving %s", fileName));
        // Flip bits to make image sutable for ignore mask
        Core.bitwise_not(mat, mat);
        final var jpeg = convert.execute(mat);
        try {
            Files.write(Paths.get(fileName), jpeg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fileName;
    }
}
