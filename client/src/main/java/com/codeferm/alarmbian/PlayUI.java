/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import com.codeferm.alarmbian.entity.Event;
import com.codeferm.alarmbian.image.MatToBufImg;
import com.formdev.flatlaf.util.StringUtils;
import de.milchreis.uibooster.UiBooster;
import de.milchreis.uibooster.model.Form;
import de.milchreis.uibooster.model.FormElement;
import de.milchreis.uibooster.model.FormElementChangeListener;
import de.milchreis.uibooster.model.UiBoosterOptions;
import de.milchreis.uibooster.model.formelements.TextFormElement;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

/**
 * High-performance, consolidated multi-camera Alarmbian desktop event viewer console interface. Implements direct ProcessBuilder
 * process tracking execution contexts to execute local native multi-channel hardware accelerated media playback and dynamic clip
 * extraction pipelines via external configurations.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Slf4j
@Command(name = "playUI")
public class PlayUI implements Callable<Integer>, FormElementChangeListener {

    /**
     * Play logic instance orchestration service handling timeline collation and storage querying.
     */
    @Autowired
    private Play play;

    /**
     * Spring environment resource context used to perform dynamic runtime property expansions.
     */
    @Autowired
    private Environment env;

    /**
     * Space-delimited template line defining the exact executable binary string layout and placeholder tokens.
     */
    @Value("${ffplay.command.template}")
    private String ffplayCommandTemplate;

    /**
     * Space-delimited template line defining the native ffmpeg archive subsegment extraction pipeline layout.
     */
    @Value("${ffmpeg.command.template}")
    private String ffmpegCommandTemplate;

    /**
     * UI Booster layout frame controller managing graphical window generation tasks.
     */
    private final UiBooster booster;

    /**
     * Image timeline list index tracking reference identifier.
     */
    private int index = 0;

    /**
     * Hierarchical list grouping motion start, target preview thumbnail, and motion termination event records.
     */
    private List<List<Event>> images;

    /**
     * Map tracking reference baseline index elements matching raw event keys back to database metrics.
     */
    private Map<String, Event> buffers;

    /**
     * Map indexing unique, localized display strings directly into their index locations inside the timeline.
     */
    private Map<String, Integer> timestamps;

    /**
     * Chronological collection of select-box friendly date-time text arrays.
     */
    private List<String> elements;

    /**
     * Ordered list holding the names of all active configured single board or IP camera profiles.
     */
    @Value("#{'${devices.list}'.split(',')}")
    private List<String> devicesList;

    /**
     * Collection of volume modification adjustment settings options strings.
     */
    private final List<String> volumeOptionsList;

    /**
     * Currently active target volume modification adjustment token value.
     */
    private String selectedVolumeAdjustment;

    /**
     * Active device workspace configuration block identifier name key.
     */
    private String currentDeviceName;

    /**
     * Database origin directory location prefix path matching mask.
     */
    private String remoteFromPath;

    /**
     * Target mounting directory translation absolute coordinate map path on the local viewing node.
     */
    private String remoteToPath;

    /**
     * Local saving and target segmentation scratch space workspace directory.
     */
    @Value("${localPath}")
    private String localPath;

    /**
     * Playback look-ahead timeline offset pre-padding multiplier metric tracked in seconds.
     */
    @Value("${playBefore}")
    private Integer playBefore;

    /**
     * Playback trailing timeline offset padding boundary multiplier metric tracked in seconds.
     */
    @Value("${playAfter}")
    private Integer playAfter;

    /**
     * Preview image dimension layout boundary aspect maximum width constraint coordinate.
     */
    @Value("${xMax}")
    private Integer xMax;

    /**
     * Preview image dimension layout boundary aspect maximum height constraint coordinate.
     */
    @Value("${yMax}")
    private Integer yMax;

    /**
     * Native translation helper tool utilizing direct buffers to remap OpenCV Mat matrices into standard BufferedImages.
     */
    private final MatToBufImg matToBufImg;

    /**
     * Primary source frame asset tracking reference point for openCV matrix operations.
     */
    private Mat source;

    /**
     * Internal scaling destination structural placeholder context matrix for geometric spatial updates.
     */
    private final Mat dest;

    /**
     * Currently isolated database classification criteria tracking token parameter.
     */
    private String currentEventType;

    /**
     * Instantiates the PlayUI component bean, setting uniform Japanese rendering font definitions, initialization buffers, and
     * default audio normalization array structures.
     */
    public PlayUI() {
        final var customFontResource = new FontUIResource(new Font("MS Mincho", Font.PLAIN, 20));
        final var defaultKeys = UIManager.getDefaults().keys();
        while (defaultKeys.hasMoreElements()) {
            final var activeKey = defaultKeys.nextElement();
            final var activeValue = UIManager.get(activeKey);
            if (activeValue instanceof javax.swing.plaf.FontUIResource) {
                UIManager.put(activeKey, customFontResource);
            }
        }
        this.booster = new UiBooster(UiBoosterOptions.Theme.SWING);
        this.matToBufImg = new MatToBufImg();
        this.matToBufImg.init();
        this.source = null;
        this.dest = new Mat();

        this.volumeOptionsList = List.of("0dB (Default)", "5dB", "10dB", "15dB", "20dB", "25dB", "30dB");
        this.selectedVolumeAdjustment = "0dB";
    }

    /**
     * Binds and configures path translation arrays mapping back to the targeted hardware camera engine node.
     *
     * @param targetCamera Absolute alphanumeric tracking key defining the requested workspace profile target.
     */
    private void activateCameraWorkspace(final String targetCamera) {
        this.currentDeviceName = targetCamera;
        this.remoteFromPath = env.getProperty(targetCamera + ".remoteFromPath");
        this.remoteToPath = env.getProperty(targetCamera + ".remoteToPath");

        log.info("Workspace activated: device={}, paths={} -> {}", currentDeviceName, remoteFromPath, remoteToPath);
        play.setDeviceName(targetCamera);
    }

    /**
     * Sanitizes duplicate edge sequence forward slashes and remaps network logging strings back to true local disk paths.
     *
     * @param rawPath Unformatted log data tracking key specifying the file's remote index position.
     * @return Cleaned, cross-platform local system file path pointer location.
     */
    private String translatePath(final String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "";
        }
        var normalizedString = rawPath.replaceAll("/{2,}", "/");

        if (remoteFromPath != null && !remoteFromPath.isBlank() && remoteToPath != null) {
            normalizedString = normalizedString.replace(remoteFromPath, remoteToPath);
        }

        return normalizedString.replaceAll("/{2,}", "/");
    }

    /**
     * Flushes localized UI state vectors and pulls updated event blocks out of the relational repository layer.
     */
    public void refresh() {
        this.buffers = play.loadMotionBuffers();

        if (currentEventType == null && play.getSmtpUiTypes() != null && !play.getSmtpUiTypes().isEmpty()) {
            this.currentEventType = play.getSmtpUiTypes().get(0);
        }

        if ("Motion".equalsIgnoreCase(currentEventType)) {
            this.images = play.loadMotionEvents();
        } else {
            this.images = play.loadSmtpMotionEvents(currentEventType);
        }

        this.elements = new ArrayList<>();
        this.timestamps = new HashMap<>();

        var counter = 0;
        for (final var dynamicEventGroup : images) {
            timestamps.put(play.formatTimestamp(dynamicEventGroup.get(0).getEventTime()), counter++);
        }
        for (var lookupIdx = images.size(); lookupIdx-- > 0;) {
            elements.add(play.formatTimestamp(images.get(lookupIdx).get(0).getEventTime()));
        }

        this.index = images.isEmpty() ? 0 : images.size() - 1;
    }

    /**
     * Builds, constructs, and pops the interactive swing interface layout environment into life.
     *
     * @return Process termination execution status tracking flag code.
     * @throws Exception Channel synchronization and system rendering boundary faults.
     */
    @Override
    public Integer call() throws Exception {
        if (devicesList == null || devicesList.isEmpty()) {
            throw new IllegalStateException("Critical initialization parameter missing: devices.list cannot be blank.");
        }

        activateCameraWorkspace(devicesList.get(0));

        final var progressionDialog = booster.showWaitingDialog("Operation", currentDeviceName);
        progressionDialog.addToLargeMessage("Refresh data");
        refresh();
        progressionDialog.close();

        final var baseStartupIndex = images.isEmpty() ? 0 : images.size() - 1;
        final var startupThumbnailFile = images.isEmpty() ? "" : translatePath(images.get(baseStartupIndex).get(1).getEventData());

        booster.createForm("Alarmbian Multi-Cam Console").
                addCustomElement(new IconFormElement(getImageIcon(null, startupThumbnailFile))).
                setID("image").
                startRow().
                addSelection("Active Camera", devicesList).setID("activeCamera").
                addSelection("Events", elements).setID("events").
                addText("Duration", images.isEmpty() ? "00:00:00" : play.formatDuration(images.get(baseStartupIndex).get(0).getEventTime(), images.get(baseStartupIndex).get(2).getEventTime()), true).setID("duration").
                addText("Before", String.valueOf(playBefore)).setID("before").
                addText("After", String.valueOf(playAfter)).setID("after").
                addSelection("Event Type", play.getSmtpUiTypes()).setID("eventType").
                addSelection("Volume Boost", volumeOptionsList).setID("volumeBoost").
                endRow().
                startRow().
                addButton("Play", () -> {
                }).setID("play").
                addButton("Save", () -> {
                }).setID("save").
                addButton("Refresh", () -> {
                }).setID("refresh").
                endRow().
                andWindow().setSize(xMax + 40, yMax + 310).save().
                setChangeListener(this).show();

        matToBufImg.done();
        return 0;
    }

    /**
     * Loads, normalizes, and rescales targeted raw file frame graphics into a renderable Swing ImageIcon asset.
     *
     * @param structuralForm Root display panel wrapper form context.
     * @param diskTargetFileName Complete absolute path referencing localized JPEG media assets.
     * @return Fully structured Swing ImageIcon wrapped object container.
     */
    public ImageIcon getImageIcon(final Form structuralForm, final String diskTargetFileName) {
        if (diskTargetFileName == null || diskTargetFileName.isBlank() || !(new File(diskTargetFileName).exists())) {
            log.warn("Target display thumbnail path unavailable on disk layout structures: {}", diskTargetFileName);
            return new ImageIcon(new BufferedImage(xMax, yMax, BufferedImage.TYPE_3BYTE_BGR));
        }

        var completedDisplayIcon = (ImageIcon) null;
        this.source = Imgcodecs.imread(diskTargetFileName);

        var targetingColorSpacePlane = BufferedImage.TYPE_BYTE_GRAY;
        if (this.source.channels() > 1) {
            targetingColorSpacePlane = BufferedImage.TYPE_3BYTE_BGR;
        }

        if (this.source.cols() > xMax) {
            Imgproc.resize(this.source, this.dest, new Size(xMax, yMax), 0, 0, Imgproc.INTER_LINEAR);
            this.source.release();
            completedDisplayIcon = new ImageIcon(matToBufImg.execute(this.dest));
            this.dest.release();
        } else {
            completedDisplayIcon = new ImageIcon(matToBufImg.execute(this.source));
            this.source.release();
        }
        return completedDisplayIcon;
    }

    /**
     * Performs clean text component data updates tracking current frame tracking spans.
     *
     * @param containerForm Active operational view workspace tracking element layout.
     */
    public void update(final Form containerForm) {
        if (!images.isEmpty() && index < images.size()) {
            final var numericalDurationField = (TextFormElement) containerForm.getById("duration");
            numericalDurationField.setValue(play.formatDuration(images.get(index).get(0).getEventTime(), images.get(index).get(2).getEventTime()));
        }
    }

    /**
     * Parses the external space-delimited configuration template, interpolates localized timing offsets and destination paths, and
     * forks an externalized FFmpeg task to archive independent event chunks.
     *
     * @param sourceVideoFile High-resolution target stream source video sequence location path.
     * @param offsetStart Baseline second offset index detailing slice start.
     * @param segmentLength Span width duration metric tracking clipping end windows.
     * @param systemExportFile Absolute target destination file system path coordinates.
     */
    public void saveFile(final String sourceVideoFile, final long offsetStart, final long segmentLength, final String systemExportFile) {
        final var calculatedSeekTokenStr = String.valueOf(offsetStart);
        final var calculatedDurationTokenStr = String.valueOf(segmentLength);

        if (log.isDebugEnabled()) {
            log.debug("""
                     PlayUI Externalized Save Diagnostics Context:
                       Source File: {}
                       Offset Start: {}s
                       Duration: {}s
                       Export Target: {}""",
                    sourceVideoFile, calculatedSeekTokenStr, calculatedDurationTokenStr, systemExportFile);
        }

        // Regex split on sequential whitespace elements captures individual options array cleanly
        final var segmentedTemplateTokens = ffmpegCommandTemplate.split("\\s+");
        final var generationProcessList = new ArrayList<String>();

        for (final var rawTokenString : segmentedTemplateTokens) {
            if (rawTokenString == null || rawTokenString.isBlank()) {
                continue;
            }

            var transformedParameterToken = rawTokenString.trim();
            if (transformedParameterToken.contains("%SEEK%")) {
                transformedParameterToken = transformedParameterToken.replace("%SEEK%", calculatedSeekTokenStr);
            } else if (transformedParameterToken.contains("%FILE%")) {
                transformedParameterToken = transformedParameterToken.replace("%FILE%", sourceVideoFile);
            } else if (transformedParameterToken.contains("%DURATION%")) {
                transformedParameterToken = transformedParameterToken.replace("%DURATION%", calculatedDurationTokenStr);
            } else if (transformedParameterToken.contains("%EXPORT%")) {
                transformedParameterToken = transformedParameterToken.replace("%EXPORT%", systemExportFile);
            }

            generationProcessList.add(transformedParameterToken);
        }

        final var processingBuilderContext = new ProcessBuilder(generationProcessList);
        processingBuilderContext.redirectErrorStream(true);

        try {
            log.info("Spawning externalized ffmpeg archive segment extraction sequence...");
            final var localSystemProcess = processingBuilderContext.start();

            // Fully consume standard output/error stream allocation buffers to maintain OS kernel stability
            try (final var underlyingStreamChannel = localSystemProcess.getInputStream(); final var terminalOutputReader = new BufferedReader(new InputStreamReader(underlyingStreamChannel))) {
                while (terminalOutputReader.readLine() != null) {
                    // Drain buffer
                }
            }
            try {
                final var pipelineExitResponseCode = localSystemProcess.waitFor();
                log.debug("Externalized archive slice completed. Native process response code: {}", pipelineExitResponseCode);
                localSystemProcess.destroy();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Active archive extraction processing context thread bounds interrupted", e);
                throw new RuntimeException("Media pipeline file extraction operation interrupted defensively", e);
            }
        } catch (final IOException e) {
            log.error("System structural tracking failure executing file extraction loops", e);
            throw new RuntimeException("System IO crash encountered trying to fork runtime extraction processes", e);
        }
    }

    /**
     * Formats raw second integers into standard absolute time masks required by media options.
     *
     * @param totalSeconds Chronological duration interval length tracking parameter.
     * @return Formatted character sequence matching time masks.
     */
    private String formatAbsoluteTime(final long totalSeconds) {
        final var absoluteTimeAnchor = Math.max(0, totalSeconds);
        final var evaluatedHours = absoluteTimeAnchor / 3600;
        final var evaluatedMinutes = (absoluteTimeAnchor % 3600) / 60;
        final var evaluatedSeconds = absoluteTimeAnchor % 60;
        return String.format("%02d:%02d:%02d", evaluatedHours, evaluatedMinutes, evaluatedSeconds);
    }

    /**
     * Parses the external space-delimited configuration template, interpolates active timing, path, and selected dynamic volume
     * levels, then executes the hardware accelerated playback.
     *
     * @param mediaStreamFile Absolute local path location referencing target archive records.
     * @param executionSeekStart Number of seconds to fast-forward into the target file prior to opening output pipelines.
     * @param segmentPlayDuration Total track viewing scope width specified in total running seconds.
     */
    public void playFile(final String mediaStreamFile, final long executionSeekStart, final long segmentPlayDuration) {
        final var calculatedSeekTokenStr = formatAbsoluteTime(executionSeekStart);
        final var calculatedDurationTokenStr = formatAbsoluteTime(segmentPlayDuration);

        if (log.isDebugEnabled()) {
            log.debug("""
                     PlayUI Externalized Diagnostics Context:
                       Target File: {}
                       Raw Seconds: Start={}, Duration={}
                       Seek String: {}
                       Clip Length: {}
                       Selected Volume: {}""",
                    mediaStreamFile, executionSeekStart, segmentPlayDuration, calculatedSeekTokenStr, calculatedDurationTokenStr, selectedVolumeAdjustment);
        }

        final var segmentedTemplateTokens = ffplayCommandTemplate.split("\\s+");
        final var generationProcessList = new ArrayList<String>();

        for (final var rawTokenString : segmentedTemplateTokens) {
            if (rawTokenString == null || rawTokenString.isBlank()) {
                continue;
            }

            var transformedParameterToken = rawTokenString.trim();
            if (transformedParameterToken.contains("%SEEK%")) {
                transformedParameterToken = transformedParameterToken.replace("%SEEK%", calculatedSeekTokenStr);
            } else if (transformedParameterToken.contains("%FILE%")) {
                transformedParameterToken = transformedParameterToken.replace("%FILE%", mediaStreamFile);
            } else if (transformedParameterToken.contains("%DURATION%")) {
                transformedParameterToken = transformedParameterToken.replace("%DURATION%", calculatedDurationTokenStr);
            } else if (transformedParameterToken.contains("%VOLUME%")) {
                transformedParameterToken = transformedParameterToken.replace("%VOLUME%", selectedVolumeAdjustment);
            }

            generationProcessList.add(transformedParameterToken);
        }

        final var executableProcessBuilder = new ProcessBuilder(generationProcessList);
        executableProcessBuilder.inheritIO();

        try {
            log.info("Spawning externalized hardware-accelerated ffplay process sequence...");
            final var activeRunningProcessInstance = executableProcessBuilder.start();
            try {
                final var pipelineExitResponseCode = activeRunningProcessInstance.waitFor();
                log.debug("Externalized media window destroyed. Native pipeline exit code mapping: {}", pipelineExitResponseCode);
                activeRunningProcessInstance.destroy();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Active playback pipeline processing context thread bounds interrupted", e);
                throw new RuntimeException("Media rendering subtask thread instance aborted execution states", e);
            }
        } catch (final IOException e) {
            log.error("System structural tracking failure executing platform process execution loops", e);
            throw new RuntimeException("Native task launch processing fail during ffplay invocation steps", e);
        }
    }

    /**
     * Checks if a provided string variable parses into valid integer formats.
     *
     * @param validationInputString Raw characters under tracking review.
     * @return True if formatting matches valid numerical properties.
     */
    public boolean isInteger(final String validationInputString) {
        try {
            Integer.valueOf(validationInputString);
            return true;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    /**
     * Listens to graphical field mutations and updates component maps and display canvases.
     *
     * @param originatingElement Reference pointer matching mutated elements.
     * @param transformationValue Payload tracking new settings selections.
     * @param structuralActiveForm Global UI booster structural window element frame reference.
     */
    @Override
    public void onChange(final FormElement originatingElement, final Object transformationValue, final Form structuralActiveForm) {
        final var graphicalDisplayCanvasLabel = (JLabel) structuralActiveForm.getById("image").getValue();
        switch (originatingElement.getId()) {
            case "activeCamera" -> {
                final var parsedCameraKey = (String) transformationValue;
                if (parsedCameraKey != null && !parsedCameraKey.isBlank()) {
                    activateCameraWorkspace(parsedCameraKey.trim());
                    refresh();

                    final var dynamicDropdownSelection = structuralActiveForm.getById("events").toSelection();
                    dynamicDropdownSelection.setPossibilities(elements);

                    if (!images.isEmpty()) {
                        index = images.size() - 1;
                        graphicalDisplayCanvasLabel.setIcon(getImageIcon(structuralActiveForm, translatePath(images.get(index).get(1).getEventData())));
                    } else {
                        graphicalDisplayCanvasLabel.setIcon(getImageIcon(structuralActiveForm, null));
                        final var executionDurationBox = (TextFormElement) structuralActiveForm.getById("duration");
                        executionDurationBox.setValue("00:00:00");
                    }
                }
            }
            case "play" -> {
                if (images.isEmpty() || index >= images.size()) {
                    break;
                }
                final var timelineBufferHeadTime = buffers.get(images.get(index).get(0).getEventData()).getEventTime().toInstant();
                final var actualMotionStartTimeMarker = images.get(index).get(0).getEventTime().toInstant();
                final var actualMotionEndTimeMarker = images.get(index).get(2).getEventTime().toInstant();

                var completeStartOffsetInSeconds = Duration.between(timelineBufferHeadTime, actualMotionStartTimeMarker).minusSeconds(playBefore).getSeconds();
                if (completeStartOffsetInSeconds < 0) {
                    completeStartOffsetInSeconds = 0;
                }

                final var completeRunningDurationSpan = Duration.between(actualMotionStartTimeMarker, actualMotionEndTimeMarker).plusSeconds(playAfter);
                final var absoluteMediaDiskLocation = translatePath(images.get(index).get(0).getEventData());

                playFile(absoluteMediaDiskLocation, completeStartOffsetInSeconds, completeRunningDurationSpan.getSeconds());
            }
            case "save" -> {
                if (images.isEmpty() || index >= images.size()) {
                    break;
                }
                final var timelineBufferHeadTime = buffers.get(images.get(index).get(0).getEventData()).getEventTime().toInstant();
                final var actualMotionStartTimeMarker = images.get(index).get(0).getEventTime().toInstant();
                final var actualMotionEndTimeMarker = images.get(index).get(2).getEventTime().toInstant();

                var completeStartOffsetInSeconds = Duration.between(timelineBufferHeadTime, actualMotionStartTimeMarker).minusSeconds(playBefore).getSeconds();
                if (completeStartOffsetInSeconds < 0) {
                    completeStartOffsetInSeconds = 0;
                }

                final var completeRunningDurationSpan = Duration.between(actualMotionStartTimeMarker, actualMotionEndTimeMarker).plusSeconds(playAfter);
                final var absoluteMediaDiskLocation = translatePath(images.get(index).get(0).getEventData());
                final var localFileObjectTarget = new File(images.get(index).get(1).getEventData().replace("jpg", "mkv"));
                final var cleanOutputStringFilename = localFileObjectTarget.getName();

                saveFile(absoluteMediaDiskLocation, completeStartOffsetInSeconds, completeRunningDurationSpan.getSeconds(),
                        String.format("%s%s%s", localPath, File.separator, cleanOutputStringFilename));
            }
            case "events" -> {
                final var activeTargetTimestampSelection = (String) structuralActiveForm.getById("events").getValue();
                if (!StringUtils.isEmpty(activeTargetTimestampSelection) && timestamps.containsKey(activeTargetTimestampSelection)) {
                    final var retrievedTimelineCoordinateIndex = timestamps.get(activeTargetTimestampSelection);
                    if (retrievedTimelineCoordinateIndex < images.size()) {
                        this.index = retrievedTimelineCoordinateIndex;
                        graphicalDisplayCanvasLabel.setIcon(getImageIcon(structuralActiveForm, translatePath(images.get(index).get(1).getEventData())));
                    }
                }
            }
            case "eventType" -> {
                final var updatedTypeFilterToken = (String) transformationValue;
                if (updatedTypeFilterToken != null && !updatedTypeFilterToken.isBlank()) {
                    this.currentEventType = updatedTypeFilterToken.trim();
                    refresh();
                    final var targetedDropdownComponent = structuralActiveForm.getById("events").toSelection();
                    targetedDropdownComponent.setPossibilities(elements);

                    if (!images.isEmpty()) {
                        this.index = images.size() - 1;
                        graphicalDisplayCanvasLabel.setIcon(getImageIcon(structuralActiveForm, translatePath(images.get(index).get(1).getEventData())));
                    } else {
                        graphicalDisplayCanvasLabel.setIcon(getImageIcon(structuralActiveForm, null));
                    }
                }
            }
            case "volumeBoost" -> {
                final var rawSelectedVolume = (String) transformationValue;
                if (rawSelectedVolume != null && !rawSelectedVolume.isBlank()) {
                    this.selectedVolumeAdjustment = rawSelectedVolume.split("\\s+")[0].trim();
                    log.debug("Dynamic volume tracking buffer set to: {}", selectedVolumeAdjustment);
                }
            }
            case "refresh" -> {
                refresh();
                final var selection = structuralActiveForm.getById("events").toSelection();
                selection.setPossibilities(elements);
            }
            case "duration" -> {
            }
            case "before" -> {
                final var rawInputTextCharacters = structuralActiveForm.getById("before").asString();
                if (rawInputTextCharacters != null && !rawInputTextCharacters.isEmpty()) {
                    if (isInteger(rawInputTextCharacters)) {
                        this.playBefore = Integer.valueOf(rawInputTextCharacters);
                    } else {
                        new UiBooster().showErrorDialog("Only integer values allowed.", "ERROR");
                    }
                }
            }
            case "after" -> {
                final var rawInputTextCharacters = structuralActiveForm.getById("after").asString();
                if (rawInputTextCharacters != null && !rawInputTextCharacters.isEmpty()) {
                    if (isInteger(rawInputTextCharacters)) {
                        this.playAfter = Integer.valueOf(rawInputTextCharacters);
                    } else {
                        new UiBooster().showErrorDialog("Only integer values allowed.", "ERROR");
                    }
                }
            }
            default ->
                booster.showErrorDialog(String.format("Event element id target mapping %s change not explicitly handled", originatingElement.getId()), "Error");
        }
        update(structuralActiveForm);
    }
}
