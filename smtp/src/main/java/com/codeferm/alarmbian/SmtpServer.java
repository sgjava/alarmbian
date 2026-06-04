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
import java.util.HashMap;
import java.util.Map;
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
 * Local SMTP server to ingest smart hardware camera event notifications.
 * <p>
 * Intercepts incoming hardware email notifications, isolates video/image attachments, and routes binaries dynamically using strict,
 * explicit per-camera storage maps.
 * </p>
 *
 * @author Steven P. Goldsmith
 * @version 1.2.5
 * @since 1.0.0
 */
@Component
@Slf4j
public class SmtpServer {

    @Value("${smtp.bind}")
    private String bind;

    @Value("${smtp.port}")
    private Integer port;

    @Value("${smtp.start.timeout}")
    private Long startTimeout;

    @Value("${wait}")
    private Long wait;

    @Value("${dir.pattern}")
    private String dirPattern;

    @Value("${smtp.user}")
    private String user;

    @Value("${smtp.password}")
    private String password;

    @Value("${smtp.email}")
    private String email;

    @Value("${smtp.threads}")
    private Integer threads;

    @Value("${file.pattern}")
    private String filePattern;

    @Value("${log.pattern}")
    private String logPattern;

    /**
     * Array configuration mappings populated directly from properties. Format: cameraName:targetBaseDirectory
     */
    @Value("${output.camera.paths:}")
    private String[] cameraPathMappings;

    @Value("${device.regex}")
    private String[] deviceRegex;

    @Value("${smtp.classifier.mappings}")
    private String[] classifierMappings;

    private DateTimeFormatter dirFormatter;
    private DateTimeFormatter fileFormatter;
    private DateTimeFormatter logFormatter;

    private final AtomicBoolean shutDown = new AtomicBoolean(false);
    private ExecutorService executorService;

    @Autowired
    private EventService eventService;

    private final AtomicLong threadCounter = new AtomicLong(0);
    private GreenMail greenMail;

    /**
     * Core map cache containing hard literal base paths matched to specific device names.
     */
    private final Map<String, String> cameraPathMap = new HashMap<>();

    /**
     * Starts and provisions the localized SMTP listener framework context.
     */
    @PostConstruct
    public void start() {
        executorService = Executors.newFixedThreadPool(threads, r -> {
            final var t = new Thread(r);
            t.setName("SMTP-Worker-" + threadCounter.incrementAndGet());
            t.setUncaughtExceptionHandler((thread, e) -> {
                log.error("FATAL: Unhandled exception in SMTP worker thread: {}", thread.getName(), e);
            });
            return t;
        });

        logFormatter = DateTimeFormatter.ofPattern(logPattern).withZone(ZoneId.systemDefault());
        dirFormatter = DateTimeFormatter.ofPattern(dirPattern).withZone(ZoneId.systemDefault());
        fileFormatter = DateTimeFormatter.ofPattern(filePattern).withZone(ZoneId.systemDefault());

        // Parse literal destination parameters directly into runtime lookups
        if (cameraPathMappings != null) {
            for (final var mapping : cameraPathMappings) {
                if (mapping == null || mapping.isBlank()) {
                    continue;
                }
                final var parts = mapping.split(":", 2);
                if (parts.length == 2) {
                    final var camKey = parts[0].trim().toLowerCase();
                    final var rootPath = parts[1].trim();
                    cameraPathMap.put(camKey, rootPath);
                    log.info(String.format("Registered strict camera route: %s -> %s", camKey, rootPath));
                }
            }
        }

        final var setup = new ServerSetup(port, bind, ServerSetup.PROTOCOL_SMTP);
        setup.setServerStartupTimeout(startTimeout);
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
     * Shuts down processing pools and detaches network server listeners gracefully.
     */
    @PreDestroy
    public void stop() {
        if (executorService != null) {
            executorService.shutdown();
        }
        if (greenMail != null) {
            greenMail.stop();
        }
        log.info("SMTP server stopped");
    }

    public String findFirstMatch(final String input, final String regex) {
        if (input == null || regex == null) {
            return null;
        }
        final var pattern = Pattern.compile(regex);
        final var matcher = pattern.matcher(input);
        if (matcher.find()) {
            try {
                return matcher.group("match");
            } catch (IllegalArgumentException e) {
                return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group(0);
            }
        }
        return null;
    }

    public String parseDeviceName(final String input) {
        var deviceName = (String) null;
        var regexIndex = 0;
        while (regexIndex < deviceRegex.length && deviceName == null) {
            deviceName = findFirstMatch(input, deviceRegex[regexIndex++]);
        }
        return deviceName;
    }

    public EventType parseEventType(final String input) {
        if (input == null || classifierMappings == null) {
            return EventType.SMTP_MOTION;
        }

        final var lowercaseInput = input.toLowerCase();

        for (final var mapping : classifierMappings) {
            final var parts = mapping.split(":");
            if (parts.length == 2) {
                final var token = parts[0].trim().toLowerCase();
                final var enumName = parts[1].trim();

                if (lowercaseInput.matches(".*\\b" + Pattern.quote(token) + "\\b.*")) {
                    try {
                        return EventType.valueOf(enumName);
                    } catch (IllegalArgumentException e) {
                        log.error("Configuration error: Unknown EventType enum target mapped to token '{}': {}", token, enumName);
                    }
                }
            }
        }

        return EventType.SMTP_MOTION;
    }

    public void processMessage(final MimeMessage message) {
        executorService.submit(() -> {
            try {
                final var receivedInstant = message.getReceivedDate().toInstant();
                log.info("Received date: {}", logFormatter.format(receivedInstant));

                final var subject = message.getSubject();
                log.debug("Subject: {}", subject);

                var deviceName = parseDeviceName(subject);
                var eventType = parseEventType(subject);

                final var content = message.getContent();
                if (content instanceof String textBody) {
                    log.debug("Body: {}", textBody);
                    if (deviceName == null) {
                        deviceName = parseDeviceName(textBody);
                    }
                    if (eventType == EventType.SMTP_MOTION) {
                        eventType = parseEventType(textBody);
                    }
                } else if (content instanceof Multipart multipart) {
                    processMultipart(message, multipart, deviceName, eventType);
                } else {
                    log.warn("Message content type not handled: {}", content.getClass().getName());
                }
            } catch (MessagingException | IOException e) {
                throw new RuntimeException("Error processing MimeMessage", e);
            }
        });
    }

    public void processMultipart(final MimeMessage message, final Multipart multipart, final String deviceName, final EventType eventType) throws
            MessagingException, IOException {
        var devName = deviceName;
        var evType = eventType;
        for (int i = 0; i < multipart.getCount(); i++) {
            final var part = multipart.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null) {
                saveAttachment(message, part, i, devName, evType);
            } else if (part.isMimeType("text/plain")) {
                final var content = part.getContent().toString();
                log.debug("Body: {}", content);
                if (devName == null) {
                    devName = parseDeviceName(content);
                }
                if (evType == EventType.SMTP_MOTION) {
                    evType = parseEventType(content);
                }
            } else if (part.isMimeType("multipart/*") && part.getContent() instanceof MimeMultipart innerMultipart) {
                processMultipart(message, innerMultipart, devName, evType);
            }
        }
    }

    /**
     * Commits incoming attachments directly to literal paths declared inside setup configurations. Rejects executions immediately
     * if an incoming camera identifier lacks an explicit path rule mapping.
     */
    public void saveAttachment(final MimeMessage message, final Part part, final int index, final String deviceName, final EventType eventType) {
        if (deviceName == null) {
            log.error("Device name is null, cannot save attachment.");
            return;
        }

        final var targetCameraKey = deviceName.trim().toLowerCase();
        final var resolvedRootPath = cameraPathMap.get(targetCameraKey);

        // Reject tracking completely if the camera lacks a literal property constraint definition
        if (resolvedRootPath == null) {
            log.warn(String.format("Rejecting attachment save: No literal path mapping configured for camera '%s'.", deviceName));
            return;
        }

        try {
            final var receivedDate = message.getReceivedDate();
            final var instant = (receivedDate != null) ? receivedDate.toInstant() : Instant.now();

            // Assembles direct explicit targets: [literalPath]/[deviceName]/[MMddyyyy]
            final var devicePath = Path.of(resolvedRootPath, deviceName, dirFormatter.format(instant));

            Files.createDirectories(devicePath);

            var extension = "bin";
            final var originalName = part.getFileName();
            if (originalName != null) {
                final var lastDot = originalName.lastIndexOf('.');
                if (lastDot >= 0 && lastDot < originalName.length() - 1) {
                    extension = originalName.substring(lastDot + 1);
                }
            }

            final var fileName = String.format("%s-smtp.%s", fileFormatter.format(instant), extension);
            final var filePath = devicePath.resolve(fileName);
            try (InputStream inputStream = part.getInputStream()) {
                Files.copy(inputStream, filePath);
            }

            final var eventTimestamp = Timestamp.from(instant);
            eventService.create(new Event(deviceName, eventType.name(), filePath.toString(), eventTimestamp));
            log.info("Saved attachment: {} ({}) with type: {}", filePath.toAbsolutePath(), part.getContentType(), eventType.name());
        } catch (MessagingException | IOException e) {
            throw new RuntimeException("Error saving attachment for device: " + deviceName, e);
        }
    }

    public void run() {
        log.info("Waiting for incoming email...");
        while (!shutDown.get()) {
            if (greenMail.waitForIncomingEmail(wait, 1)) {
                final var messages = greenMail.getReceivedMessages();
                for (final var message : messages) {
                    processMessage(message);
                }
                try {
                    greenMail.purgeEmailFromAllMailboxes();
                } catch (FolderException e) {
                    log.error("Error purging mailboxes: {}", e.getMessage(), e);
                }
            }
        }
    }
}
