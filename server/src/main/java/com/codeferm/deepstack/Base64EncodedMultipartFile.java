/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.deepstack;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

/**
 * Simple BASE64 multipart encoder.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
// Replaces the manual constructor: public Base64EncodedMultipartFile(final byte[] image, final String fileName)
@AllArgsConstructor
public class Base64EncodedMultipartFile implements MultipartFile {

    /**
     * Image as byte array.
     */
    // @Getter will generate getImage()
    @Getter
    private final byte[] image;
    
    /**
     * File name to return.
     */
    // Lombok's @Getter is not used here because the interface mandates
    // getName() and getOriginalFilename(), and fileName is used for both.
    private final String fileName;

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
    // This is required by the MultipartFile interface
    public long getSize() {
        return image.length;
    }

    @Override
    // This is required by the MultipartFile interface
    public byte[] getBytes() throws IOException {
        return image;
    }

    @Override
    // This is required by the MultipartFile interface
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(image);
    }

    @Override
    // This is required by the MultipartFile interface
    public void transferTo(File dest) throws IOException, IllegalStateException {
        new FileOutputStream(dest).write(image);
    }
}
