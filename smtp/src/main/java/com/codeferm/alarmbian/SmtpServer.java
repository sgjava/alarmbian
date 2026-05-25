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
 * Local SMTP server to ingest smart hardware camera event notifications.
 * <p>
 * This broker intercepts camera email triggers, parses device identifiers and
 * externalized target classifications, isolates binary payloads, and persists
 * records natively.
 * </p>
 *
 * @author Steven P. Goldsmith
 * @version 1.1.0
 * @since 1.0.0
 */
@Component
@Slf4j
public class SmtpServer {

    /**
     * Network interface address to bind the SMTP receptor.
     */
    @Value("${smtp.bind}")
    private String bind;

    /**
     * Active network port for incoming mail listening.
     */
    @Value("${smtp.port}")
    private Integer port;

    /**
     * Thread sleep threshold in milliseconds when awaiting message sweeps.
     */
    @Value("${wait}")
    private Long wait;

    /**
     * Pattern layout for generating dynamic output sub-directories.
     */
    @Value("${dir.pattern}")
    private String dirPattern;

    /**
     * Account security username for internal user authentication profiles.
     */
    @Value("${smtp.user}")
    private String user;

    /**
     * Account security password credential for internal user authentication.
     */
    @Value("${smtp.password}")
    private String password;

    /**
     * Root routing electronic mail address rule target.
     */
    @Value("${smtp.email}")
    private String email;

    /**
     * Maximum capacity bounds for the processing fixed worker pool threads.
     */
    @Value("${smtp.threads}")
    private Integer threads;

    /**
     * File naming pattern syntax applied to binary file outputs.
     */
    @Value("${file.pattern}")
    private String filePattern;

    /**
     * Log time visual string pattern schema mapping.
     */
    @Value("${log.pattern}")
    private String logPattern;

    /**
     * Root filesystem branch destination where media payloads write.
     */
    @Value("${output.path}")
    private String outputPath;

    /**
     * Array of regular expression strings designed to pull device tags from
     * text blocks.
     */
    @Value("${device.regex}")
    private String[] deviceRegex;

    /**
     * Injected token-to-event key-value configurations from application
     * properties. Example: people:SMTP_PEOPLE, vehicle:SMTP_VEHICLE
     */
    @Value("${smtp.classifier.mappings}")
    private String[] classifierMappings;

    /**
     * Temporal folder dynamic destination structure mask formatter.
     */
    private DateTimeFormatter dirFormatter;

    /**
     * Media file storage descriptor string mask formatter.
     */
    private DateTimeFormatter fileFormatter;

    /**
     * Internal terminal text log date-time visualization mask formatter.
     */
    private DateTimeFormatter logFormatter;

    /**
     * Control gate tracking runtime termination requests.
     */
    private final AtomicBoolean shutDown = new AtomicBoolean(false);

    /**
     * Background concurrent work thread manager boundary pool execution runner.
     */
    private ExecutorService executorService;

    /**
     * Persistence execution utility providing native relational state
     * management database interactions.
     */
    @Autowired
    private EventService eventService;

    /**
     * Safe rolling reference index provider feeding dedicated thread
     * identification identities.
     */
    private final AtomicLong threadCounter = new AtomicLong(0);

    /**
     * Isolated micro-server process pipeline anchor interface instance.
     */
    private GreenMail greenMail;

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
        final var setup = new ServerSetup(port, bind, ServerSetup.PROTOCOL_SMTP);
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
     * Shuts down processing pools and detaches network server listeners
     * gracefully.
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

    /**
     * Locates the first regex match inside text data, pulling standard group
     * contents cleanly.
     *
     * @param input Raw character context targeted for evaluation loops.
     * @param regex Search pattern syntax configuration framework.
     * @return Matched string value result, or null if boundaries match empty
     * arrays.
     */
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

    /**
     * Evaluates text fields to find custom registered hardware camera system
     * IDs.
     *
     * @param input Context line segment extracted from headers or document
     * parts.
     * @return Discovered machine name string, or null if tracking profiles
     * mismatch.
     */
    public String parseDeviceName(final String input) {
        var deviceName = (String) null;
        var regexIndex = 0;
        while (regexIndex < deviceRegex.length && deviceName == null) {
            deviceName = findFirstMatch(input, deviceRegex[regexIndex++]);
        }
        return deviceName;
    }

    /**
     * Scans textual fields to resolve hardware classifications using
     * configuration rules.
     * <p>
     * Iterates through the properties matrix tokens to match sub-strings,
     * defaulting immediately to a standard fallback motion event if unmatched.
     * </p>
     *
     * @param input Message subject line or body text segments.
     * @return Resolved EventType tag classification.
     */
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

                if (lowercaseInput.contains(token)) {
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

    /**
     * Isolates mail elements asynchronously to parse headers and branch
     * multipart objects.
     *
     * @param message Mail item context targeted for structural decomposition.
     */
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

    /**
     * Loops across internal attachment payloads to extract nested document
     * trees.
     *
     * @param message Mail item entity context.
     * @param multipart Element collection layout boundary components.
     * @param deviceName Identity token of the originating equipment node.
     * @param eventType Determined target object classification status.
     * @throws MessagingException Error passing elements over mail protocols.
     * @throws IOException Error handling internal frame stream pipelines.
     */
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
     * Commits incoming files to disk branches and registers an immutable
     * classification database record.
     *
     * @param message Mail item data container.
     * @param part Extracted component element containing raw binary blocks.
     * @param index Position counter index within tracking multipart layers.
     * @param deviceName Machine label associated with active alerts.
     * @param eventType Determined semantic event category context tag.
     */
    public void saveAttachment(final MimeMessage message, final Part part, final int index, final String deviceName, final EventType eventType) {
        if (deviceName == null) {
            log.error("Device name is null, cannot save attachment.");
            return;
        }
        try {
            final var receivedDate = message.getReceivedDate();
            final var instant = (receivedDate != null) ? receivedDate.toInstant() : Instant.now();
            final var devicePath = Path.of(outputPath, deviceName, dirFormatter.format(instant));

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

    /**
     * Loops continuous verification scans watching for incoming greenMail
     * transactions.
     */
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
