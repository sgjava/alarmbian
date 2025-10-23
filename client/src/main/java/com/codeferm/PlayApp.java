/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm;

import com.codeferm.alarmbian.PlayUI;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import picocli.CommandLine;

/**
 * Alarmbian player app based on Spring Boot. This uses Pico CLI to handle blocking in UI code. Otherwise Spring Boot app exits
 * right away.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@SpringBootApplication
@Slf4j
public class PlayApp implements CommandLineRunner, ExitCodeGenerator {

    /**
     * Player UI.
     */
    @Autowired
    private PlayUI playUI;
    /**
     * App exit code.
     */
    private int exitCode;

    /**
     * Run Pico CLI command line.
     *
     * @param args Arguments passed on command line.
     * @throws Exception Possible exception.
     */
    @Override
    public void run(String... args) throws Exception {
        exitCode = new CommandLine(playUI).execute(args);
    }

    /**
     * Get exit code.
     *
     * @return Exit code.
     */
    @Override
    public int getExitCode() {
        return exitCode;
    }

    /**
     * Main program.
     *
     * @param args Arguments passed on command line.
     */
    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        // Let Spring instantiate and inject dependencies
        System.exit(SpringApplication.exit(new SpringApplicationBuilder(PlayApp.class).headless(false).run(args)));
    }
}
