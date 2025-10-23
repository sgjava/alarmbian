/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.image.MatToImage;
import com.codeferm.alarmbian.type.Convert;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
@Data
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

    @Builder
    public HistoryWriter(final Convert<Mat, byte[]> convert, final String path, final String dirPattern, final String filePattern) {
        this.convert = convert;
        this.path = path;
        this.dirPattern = dirPattern;
        this.filePattern = filePattern;
        dirFormatter = DateTimeFormatter.ofPattern(dirPattern).withZone(ZoneId.systemDefault());
        fileFormatter = DateTimeFormatter.ofPattern(filePattern).withZone(ZoneId.systemDefault());
    }

    /**
     * Initialize class.
     *
     * @param source Source Mat.
     */
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
