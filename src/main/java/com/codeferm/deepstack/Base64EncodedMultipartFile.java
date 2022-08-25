/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.deepstack;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

/**
 * Simple BASE64 multipart encoder.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
public class Base64EncodedMultipartFile implements MultipartFile {

    /**
     * Image as byte array.
     */
    private final byte[] image;
    /**
     * File name to return.
     */
    private final String fileName;

    public Base64EncodedMultipartFile(final byte[] image, final String fileName) {
        this.image = image;
        this.fileName = fileName;
    }

    @Override
    public String getName() {
        return fileName;
    }

    @Override
    public String getOriginalFilename() {
        return fileName;
    }

    @Override
    public String getContentType() {
        return "text/plain";
    }

    @Override
    public boolean isEmpty() {
        return image == null || image.length == 0;
    }

    @Override
    public long getSize() {
        return image.length;
    }

    @Override
    public byte[] getBytes() throws IOException {
        return image;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(image);
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        new FileOutputStream(dest).write(image);
    }
}
