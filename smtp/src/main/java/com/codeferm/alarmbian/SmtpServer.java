/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.entity.Event;
import com.codeferm.alarmbian.service.EventService;
import com.codeferm.alarmbian.type.EventType;
import com.icegreen.greenmail.store.FolderException;
import com.icegreen.greenmail.user.UserException;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Local SMTP server to get camera events.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.1
 * @since 1.0.0
 */
@Component
@Slf4j
public class SmtpServer {

    /**
     * Bind address.
     */
    @Value("${smtp.bind}")
    private String bind;
    /**
     * Port.
     */
    @Value("${smtp.port}")
    private Integer port;
    /**
     * Wait MS checking for messages.
     */
    @Value("${wait}")
    private Long wait;
    /**
     * Directory pattern.
     */
    @Value("${dir.pattern}")
    private String dirPattern;
    /**
     * SMTP user.
     */
    @Value("${smtp.user}")
    private String user;
    /**
     * AMTP password.
     */
    @Value("${smtp.password}")
    private String password;
    /**
     * SMTP email.
     */
    @Value("${smtp.email}")
    private String email;
    /**
     * SMTP threads.
     */
    @Value("${smtp.threads}")
    private Integer threads;
    /**
     * File pattern.
     */
    @Value("${file.pattern}")
    private String filePattern;
    /**
     * Log pattern.
     */
    @Value("${log.pattern}")    
    private String logPattern;
    /**
     * File output path.
     */
    @Value("${output.path}")
    private String outputPath;
    /**
     * Array of regex to find device name in subject or body.
     */
    @Value("${device.regex}")
    private String[] deviceRegex;
    /**
     * Directory name formatter.
     */
    private DateTimeFormatter dirFormatter;
    /**
     * File name formatter.
     */
    private DateTimeFormatter fileFormatter;
    /**
     * Log date formatter for message received date.
     */
    private DateTimeFormatter logFormatter;
    /**
     * Default to not shutting down.
     */
    private final AtomicBoolean shutDown = new AtomicBoolean(false);
    /**
     * Executor service.
     */
    private ExecutorService executorService;
    /**
     * Used to presist event.
     */
    @Autowired
    private EventService eventService;
    /**
     * Counter for naming worker threads.
     */
    private final AtomicLong threadCounter = new AtomicLong(0);
    /**
     * Server instance.
     */
    private GreenMail greenMail;

    /**
     * Start SMTP server.
     */
    @PostConstruct
    public void start() {
        // Use a custom ThreadFactory to assign an UncaughtExceptionHandler
        executorService = Executors.newFixedThreadPool(threads, r -> {
            var t = new Thread(r);
            // Use AtomicLong for unique ID
            t.setName("SMTP-Worker-" + threadCounter.incrementAndGet());
            // Set the handler that will catch and log any unchecked exception
            t.setUncaughtExceptionHandler((thread, e) -> {
                // This logs the full stack trace to your configured logger (Slf4j)
                log.error("FATAL: Unhandled exception in SMTP worker thread: {}", thread.getName(), e);
            });
            return t;
        });
        logFormatter = DateTimeFormatter.ofPattern(logPattern).withZone(ZoneId.systemDefault());
        dirFormatter = DateTimeFormatter.ofPattern(dirPattern).withZone(ZoneId.systemDefault());
        fileFormatter = DateTimeFormatter.ofPattern(filePattern).withZone(ZoneId.systemDefault());
        var setup = new ServerSetup(port, bind, ServerSetup.PROTOCOL_SMTP);
        greenMail = new GreenMail(setup);
        try {
            greenMail.getManagers().getUserManager().createUser(email, user, password);
        } catch (UserException e) {
           throw new RuntimeException(e);
        }
        greenMail.getManagers().getUserManager().setAuthRequired(true);
        greenMail.start();
        log.info("SMTP server running on {}:{}", bind, port);
    }

    /**
     * Stop SMTP server.
     */
    @PreDestroy
    public void stop() {
        // Stop the thread pool gracefully
        if (executorService != null) {
            executorService.shutdown();
        }
        if (greenMail != null) {
            greenMail.stop();
        }
        log.info("SMTP server stopped");
    }

    /**
     * Returns the first match for the given regex in the input string. If a named group "match" exists, that group's value is
     * returned. Otherwise, the first capturing group is returned, or the whole match if no groups.
     *
     * @param input The string to search.
     * @param regex The regex pattern to use.
     * @return The matched substring, or null if no match found.
     */
    public String findFirstMatch(final String input, final String regex) {
        if (input == null || regex == null) {
            return null;
        }
        // Pattern.compile is resource-intensive; consider caching common patterns if performance is critical
        var pattern = Pattern.compile(regex);
        var matcher = pattern.matcher(input);
        if (matcher.find()) {
            // Prefer named group "match" if present
            try {
                return matcher.group("match");
            } catch (IllegalArgumentException e) {
                // No named group, fallback to first capturing group or entire match
                return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group(0);
            }
        }
        return null;
    }

    /**
     * Parse device name.
     *
     * @param input Subject or body.
     * @return Device name or null.
     */
    public String parseDeviceName(final String input) {
        String deviceName = null;
        var regexIndex = 0;
        // Go through regexs
        while (regexIndex < deviceRegex.length && deviceName == null) {
            deviceName = findFirstMatch(input, deviceRegex[regexIndex++]);
        }
        // If null then no match
        return deviceName;
    }

    /**
     * Parse out device name and handle multi part message.
     *
     * @param message MimeMessage.
     */
    public void processMessage(final MimeMessage message) {
        // Run the heavy lifting in a background thread
        executorService.submit(() -> {
            try {
                // Use the thread-safe DateTimeFormatter for logging
                var receivedInstant = message.getReceivedDate().toInstant();
                log.info("Received date: {}", logFormatter.format(receivedInstant));
                var subject = message.getSubject();
                log.debug("Subject: {}", subject);
                // Set device name based on subject
                var deviceName = parseDeviceName(subject);
                var content = message.getContent();
                // Uses Pattern Matching for instanceof (Java 16+)
                if (content instanceof String textBody) {
                    log.debug("Body: {}", textBody);
                    // If device name not found in subject, try body
                    if (deviceName == null) {
                        deviceName = parseDeviceName(textBody);
                    }
                } else if (content instanceof Multipart multipart) {
                    processMultipart(message, multipart, deviceName);
                } else {
                    log.warn("Message content type not handled: {}", content.getClass().getName());
                }
            } catch (MessagingException | IOException e) {
                // We intentionally wrap checked exceptions here. The UncaughtExceptionHandler in start() will log this.
                throw new RuntimeException("Error processing MimeMessage", e);
            }
        });
    }

    /**
     * Process multi part message.
     *
     * @param message MimeMessage.
     * @param multipart Multipart.
     * @param deviceName Device name.
     * @throws MessagingException possible exception.
     * @throws IOException possible exception.
     */
    public void processMultipart(final MimeMessage message, final Multipart multipart, final String deviceName) throws
            MessagingException, IOException {
        var devName = deviceName;
        for (int i = 0; i < multipart.getCount(); i++) {
            var part = multipart.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null) {
                saveAttachment(message, part, i, devName);
            } else if (part.isMimeType("text/plain")) {
                var content = part.getContent().toString();
                log.debug("Body: {}", content);
                // If device name not found in subject try body
                if (devName == null) {
                    devName = parseDeviceName(content);
                }
            } else if (part.isMimeType("multipart/*") && part.getContent() instanceof MimeMultipart innerMultipart) {
                processMultipart(message, innerMultipart, devName);
            }
        }
    }

    /**
     * Save attachment using received timestamp for unique filename.
     *
     * @param message MimeMessage.
     * @param part Part.
     * @param index Index of part.
     * @param deviceName Device name.
     */
    public void saveAttachment(final MimeMessage message, final Part part, final int index, final String deviceName) {
        if (deviceName == null) {
            log.error("Device name is null, cannot save attachment.");
            return;
        }
        try {
            // Use received date if available, otherwise current time
            final var receivedDate = message.getReceivedDate();
            final var instant = (receivedDate != null) ? receivedDate.toInstant() : Instant.now();

            // 1. IMPROVEMENT: Construct path using Path API (safer and cleaner than String concatenation)
            final Path devicePath = Path.of(outputPath, deviceName, dirFormatter.format(instant));

            // Create dir
            Files.createDirectories(devicePath);

            // Determine file extension (default bin)
            var extension = "bin";
            final var originalName = part.getFileName();
            if (originalName != null) {
                var lastDot = originalName.lastIndexOf('.');
                if (lastDot >= 0 && lastDot < originalName.length() - 1) {
                    extension = originalName.substring(lastDot + 1);
                }
            }
            // Construct file name and full path
            final var fileName = String.format("%s-smtp.%s", fileFormatter.format(instant), extension);
            final Path filePath = devicePath.resolve(fileName);
            // Use Files.copy() for efficient stream copying (Java 7+)
            try (InputStream inputStream = part.getInputStream()) {
                Files.copy(inputStream, filePath);
            }
            // Use Instant.ofEpochMilli(message.getReceivedDate().getTime()) or similar if Timestamp conversion is required
            var eventTimestamp = Timestamp.from(instant);
            eventService.create(new Event(deviceName, EventType.SMTP_MOTION.name(), fileName, eventTimestamp));
            log.info("Saved attachment: {} ({})", filePath.toAbsolutePath(), part.getContentType());
        } catch (MessagingException | IOException e) {
            throw new RuntimeException("Error saving attachment for device: " + deviceName, e);
        }
    }

    /**
     * SMTP server message processing.
     */
    public void run() {
        log.info("Waiting for incoming email...");
        while (!shutDown.get()) {
            // Keep the wait loop as is, though a blocking receive might be cleaner if GreenMail supported it better
            // waitForIncomingEmail returns true if at least one message was received.
            if (greenMail.waitForIncomingEmail(wait, 1)) {
                var messages = greenMail.getReceivedMessages();
                // Process all messages
                for (var message : messages) {
                    processMessage(message);
                }
                try {
                    // Clear so we only see new messages next loop
                    greenMail.purgeEmailFromAllMailboxes();
                } catch (FolderException e) {
                    log.error("Error purging mailboxes: {}", e.getMessage(), e);
                }
            }
        }
    }
}
