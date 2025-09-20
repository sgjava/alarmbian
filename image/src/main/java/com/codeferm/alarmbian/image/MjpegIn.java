/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.image;

import com.codeferm.alarmbian.type.VideoSource;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;

/**
 * Pure Java MJPEG implementation. Handles authorization if user and password passed in URL.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class MjpegIn extends VideoSource {

    /**
     * A URLConnection with support for HTTP-specific features.
     */
    private HttpURLConnection connection = null;
    /**
     * Buffered HTTP stream.
     */
    private BufferedInputStream bufferedInputStream = null;
    /**
     * EOL character (if OS supports more than one char only the last char is used).
     */
    private final byte eol;
    /**
     * Number of lines to skip after Content-Length
     */
    private int skipLines = -1;
    /**
     * Input stream buffer size.
     */
    public static final int BUFFER_SIZE = 4096;

    /**
     * Set EOL character based on OS.
     */
    public MjpegIn() {
        eol = (byte) System.getProperty("line.separator").charAt(System.getProperty("line.separator").length() - 1);
    }

    /**
     * Create HttpURLConnection from String URL. Handles authorization if user set.
     *
     * @param device String representation of device.
     * @param user User if authorization required.
     * @param password Password if authorization required.
     * @param timeout Connection timeout in milliseconds.
     * @return True on success and false on failure.
     */
    public boolean open(final String device, final String user, final String password, final int timeout) {
        var isOpen = false;
        URL deviceUrl = null;
        try {
            log.debug(String.format("Opening %s", device));
            deviceUrl = new URI(device).toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new RuntimeException(e);
        }
        log.debug(String.format("Connect and read timeout %d ms", timeout));
        try {
            connection = (HttpURLConnection) deviceUrl.openConnection();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        // Use Authenticator if user set
        if (user != null) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, password.toCharArray()
                    );
                }
            });
        }
        try {
            connection.connect();
            bufferedInputStream = new BufferedInputStream(connection.getInputStream(), BUFFER_SIZE);
            // First time through skipLines = -1 to skip readLine call until set below
            getFrameLength();
            String line = null;
            // Number of lines to skip after Content-Length and before JPEG image data
            do {
                line = readLine();
                skipLines++;
                // Check for JPEG header
            } while (!line.contains("\uffff\uffd8\uffff"));
            // Get JPEG encoded frame
            final var frame = getFrame();
            setWidth(frame.getWidth()).setHeight(frame.getHeight());
            log.debug(String.format("Resolution %dw x %dh", getWidth(), getHeight()));
            isOpen = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return isOpen;
    }

    /**
     * Create HttpURLConnection from String URL. Handles Authorization if passed in URL.
     *
     * @param device String representation of device.
     * @return True on success and false on failure.
     */
    @Override
    public boolean open(final String device) {
        var isOpen = false;
        URL deviceUrl = null;
        try {
            deviceUrl = new URI(device).toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new RuntimeException(e);
        }
        final var userInfo = deviceUrl.getUserInfo();
        if (userInfo != null) {
            final var auth = userInfo.split(":");
            isOpen = open(device.replace(userInfo, "").replace("@", ""), auth[0], auth[1], getTimeout());
        } else {
            isOpen = open(device, null, null, getTimeout());
        }
        return isOpen;
    }

    /**
     * Read until the native OS EOL is reached and return line. In the context
     * of a M-JPEG stream it is to get the non-JPEG data.
     *
     * @return String line of characters.
     */
    public String readLine() {
        final var line = new StringBuilder();
        final var in = new byte[1];
        int length = 0;
        try {
            length = bufferedInputStream.read(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        while (length > 0 && in[0] != eol) {
            line.append((char) in[0]);
            try {
                length = bufferedInputStream.read(in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return line.toString().trim();
    }

    /**
     * Get frame length by parsing Content-Length.
     *
     * @return Frame length in bytes.
     */
    public int getFrameLength() {
        String contentLength = null;
        do {
            final var line = readLine();
            // See if we have "content-length" line
            if (line.toLowerCase(Locale.ROOT).contains("content-length")) {
                final var parts = line.split(":");
                // Get length
                contentLength = parts[1].trim();
            }
        } while (contentLength == null);
        // Skip lines before image data.
        var i = skipLines;
        while (i > 0) {
            readLine();
            i--;
        }
        return Integer.parseInt(contentLength);
    }

    /**
     * Return JPEG frame as byte array.
     *
     * @param frameLength How many bytes to read.
     * @return JPEG as byte array.
     */
    public byte[] getFrameRaw(final int frameLength) {
        final var frameArray = new byte[frameLength];
        var i = 0;
        while (i < frameLength) {
            try {
                frameArray[i++] = (byte) bufferedInputStream.read();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return frameArray;
    }

    /**
     * Return raw JPEG data as BufferedImage.
     *
     * @return JPEG as byte array.
     */
    @Override
    public BufferedImage getFrame() {
        BufferedImage destImage = null;
        try (final var inputStream = new ByteArrayInputStream(getFrameRaw(getFrameLength()))) {
            destImage = ImageIO.read(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return destImage;
    }

    /**
     * Close stream.
     */
    @Override
    public void close() {
        try {
            bufferedInputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        connection.disconnect();
    }
}
