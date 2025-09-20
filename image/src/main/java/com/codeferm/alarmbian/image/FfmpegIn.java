/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.image;

import com.codeferm.alarmbian.type.VideoSource;
import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.FFmpegResultFuture;
import com.github.kokorin.jaffree.ffmpeg.Frame;
import com.github.kokorin.jaffree.ffmpeg.FrameConsumer;
import com.github.kokorin.jaffree.ffmpeg.FrameOutput;
import com.github.kokorin.jaffree.ffmpeg.Stream;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Read frames from FFMPEG source. We are using a future here, but still support synchronous VideoSource.getFrame. I found that
 * FrameConsumer leaks memory in ffmpeg process. See https://github.com/sgjava/leaker for more information. Do not use this class
 * until it's fixed.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Deprecated(since = "1.0.0", forRemoval = true)
public class FfmpegIn extends VideoSource implements FrameConsumer {

    /**
     * Future to run ffmpeg in background.
     */
    private FFmpegResultFuture future;
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
     * Frame queue.
     */
    private BlockingQueue<BufferedImage> frameQueue;

    /**
     * Initialize queue.
     */
    public FfmpegIn() {
        frameQueue = new ArrayBlockingQueue<>(100);
    }

    public FFmpegResultFuture getFuture() {
        return future;
    }

    public FfmpegIn setFuture(final FFmpegResultFuture future) {
        this.future = future;
        return this;
    }

    public String getBin() {
        return bin;
    }

    public FfmpegIn setBin(final String bin) {
        this.bin = bin;
        return this;
    }

    public Map<String, String> getInputArgs() {
        return inputArgs;
    }

    public FfmpegIn setInputArgs(final Map<String, String> inputArgs) {
        this.inputArgs = inputArgs;
        return this;
    }

    public Map<String, String> getOutputArgs() {
        return outputArgs;
    }

    public FfmpegIn setOutputArgs(final Map<String, String> outputArgs) {
        this.outputArgs = outputArgs;
        return this;
    }

    public BlockingQueue<BufferedImage> getFrameQueue() {
        return frameQueue;
    }

    public FfmpegIn setFrameQueue(final BlockingQueue<BufferedImage> frameQueue) {
        this.frameQueue = frameQueue;
        return this;
    }

    /**
     * Future is used to run FFMPEG in the background. Disable all streams except video.
     *
     * @param device String representation of device.
     * @return True on success and false on failure.
     */
    @Override
    public boolean open(String device) {
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
        // Disable audio, subtitles and data from stream
        final var output = FrameOutput.withConsumer(this).disableStream(StreamType.AUDIO).disableStream(StreamType.SUBTITLE).
                disableStream(StreamType.DATA);
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
        future = FFmpeg.atPath(Paths.get(bin)).addInput(input).addOutput(output).executeAsync();
        BufferedImage frame;
        // Wait for first frame
        try {
            frame = frameQueue.poll​(getTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        setWidth(frame.getWidth()).setHeight(frame.getHeight());
        return !frameQueue.isEmpty();
    }

    @Override
    public void consumeStreams(List<Stream> streams) {
        // All stream types except video are disabled. just ignore
    }

    /**
     * Add frame to a queue that can be picked up by getFrame.
     *
     * @param frame Frame consumed.
     */
    @Override
    public void consume(final Frame frame) {
        // End of Stream?
        if (frame != null) {
            frameQueue.add(frame.getImage());
        } else {
            throw new RuntimeException("End of stream");
        }
    }

    /**
     * Return image as a BufferedImage.
     *
     * @return Image as a BufferedImage.
     */
    @Override
    public BufferedImage getFrame() {
        BufferedImage frame;
        try {
            frame = frameQueue.poll​(getTimeout(), TimeUnit.MILLISECONDS);
            if (!frameQueue.isEmpty()) {
                log.warn(String.format("Frame queue %d", frameQueue.size()));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return frame;
    }

    /**
     * Shut down ffmpeg.
     */
    @Override
    public void close() {
        future.graceStop();
    }
}
