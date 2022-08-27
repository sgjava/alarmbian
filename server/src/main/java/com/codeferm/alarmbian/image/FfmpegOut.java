/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.image;

import com.codeferm.alarmbian.type.Record;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.FFmpegProgress;
import com.github.kokorin.jaffree.ffmpeg.FFmpegResult;
import com.github.kokorin.jaffree.ffmpeg.FFmpegResultFuture;
import com.github.kokorin.jaffree.ffmpeg.ProgressListener;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * Use ffmpeg to record stream to file. You should copy all codecs since this is
 * the least CPU intensive as no transcoding occurs.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class FfmpegOut extends Record implements ProgressListener {

    /**
     * FFmpegResultFuture result.
     */
    private AtomicReference<FFmpegResult> result = new AtomicReference<>();
    /**
     * Future to run ffmpeg in background.
     */
    private FFmpegResultFuture future;
    /**
     * URL to connect to camera or file name.
     */
    private String device;
    /**
     * Path to save recordings to.
     */
    private String path;
    /**
     * Video container like "mkv".
     */
    private String container;
    /**
     * File name constructed by start method.
     */
    private String fileName;
    /**
     * DateTimeFormatter pattern used as part of file name.
     */
    private String dirPattern;
    /**
     * Suffix used as part of file name.
     */
    private String fileSuffix;
    /**
     * DateTimeFormatter pattern used as part of file name.
     */
    private String filePattern;
    /**
     * Path to ffmpeg binary including trailing slash.
     */
    private String bin;
    /**
     * FFMPEG input arguments.
     */
    private Map<String, String> inputArgs;
    /**
     * FFMPEG output arguments.
     */
    private Map<String, String> outputArgs;
    /**
     * Directory name formatter.
     */
    private DateTimeFormatter dirFormatter;
    /**
     * File name formatter.
     */
    private DateTimeFormatter fileFormatter;

    public FFmpegResultFuture getFuture() {
        return future;
    }

    public FfmpegOut setFuture(final FFmpegResultFuture future) {
        this.future = future;
        return this;
    }

    public String getDevice() {
        return device;
    }

    public FfmpegOut setDevice(final String device) {
        this.device = device;
        return this;
    }

    public String getPath() {
        return path;
    }

    public FfmpegOut setPath(final String path) {
        this.path = path;
        return this;
    }

    public String getContainer() {
        return container;
    }

    public FfmpegOut setContainer(final String container) {
        this.container = container;
        return this;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDirPattern() {
        return dirPattern;
    }

    public FfmpegOut setDirPattern(final String dirPattern) {
        this.dirPattern = dirPattern;
        dirFormatter = DateTimeFormatter.ofPattern(dirPattern).withZone(ZoneId.systemDefault());
        return this;
    }

    public String getFileSuffix() {
        return fileSuffix;
    }

    public FfmpegOut setFileSuffix(final String fileSuffix) {
        this.fileSuffix = fileSuffix;
        return this;
    }

    public String getFilePattern() {
        return filePattern;
    }

    public FfmpegOut setFilePattern(final String filePattern) {
        this.filePattern = filePattern;
        fileFormatter = DateTimeFormatter.ofPattern(filePattern).withZone(ZoneId.systemDefault());
        return this;
    }

    public String getBin() {
        return bin;
    }

    public FfmpegOut setBin(final String bin) {
        this.bin = bin;
        return this;
    }

    public Map<String, String> getInputArgs() {
        return inputArgs;
    }

    public FfmpegOut setInputArgs(final Map<String, String> inputArgs) {
        this.inputArgs = inputArgs;
        return this;
    }

    public Map<String, String> getOutputArgs() {
        return outputArgs;
    }

    public FfmpegOut setOutputArgs(final Map<String, String> outputArgs) {
        this.outputArgs = outputArgs;
        return this;
    }

    @Override
    public void onProgress(FFmpegProgress progress) {
        //logger.info(String.format("FPS %.1f", progress.getFps()));
    }

    /**
     * Start recording. Future is used to run in the background.
     *
     * @param timestamp Timestamp to use in file name.
     */
    @Override
    public void start(final Instant timestamp) {
        // Make sure previous recording is done
        if (future == null || future.isDone()) {
            // Construct directory name
            final var dirName = String.format("%s%s%s", path, File.separator, dirFormatter.format(timestamp));
            // Create dir
            try {
                Files.createDirectories(Paths.get(dirName));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // Construct file name 
            fileName = String.
                    format("%s%s%s-%s.%s", dirName, File.separator, fileFormatter.format(timestamp), fileSuffix, container);
            log.info(String.format("Recording %s starting", fileName));
            final var input = UrlInput.fromUrl(device);
            // Set input args
            if (inputArgs != null) {
                inputArgs.entrySet().forEach(entry -> {
                    if (entry.getValue() != null) {
                        input.addArguments(entry.getKey(), entry.getValue());
                    } else {
                        input.addArgument(entry.getKey());
                    }
                });
            }
            final var output = UrlOutput.toPath(Paths.get(fileName));
            // Set output args
            if (outputArgs != null) {
                outputArgs.entrySet().forEach(entry -> {
                    if (entry.getValue() != null) {
                        output.addArguments(entry.getKey(), entry.getValue());
                    } else {
                        output.addArgument(entry.getKey());
                    }
                });
            }
            FFmpeg ffmpeg = FFmpeg.atPath(Paths.get(bin)).addInput(input).addOutput(output).setProgressListener(this);
            // Execute in background
            future = ffmpeg.executeAsync();
        } else {
            throw new RuntimeException(String.format("Cannot start recording until previous recording %s finished", fileName));
        }
    }

    /**
     * Stop recording gracefully.
     *
     * @param timestamp Timestamp of stop.
     */
    @Override
    public void stop(final Instant timestamp) {
        if (future != null && !future.isDone()) {
            future.graceStop();
            log.info(String.format("Recording %s stopping", fileName));
        } else {
            log.warn(String.format("%s not recording", fileName));
        }
    }
}
