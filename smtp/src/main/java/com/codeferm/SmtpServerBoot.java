/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm;

import com.codeferm.alarmbian.SmtpServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * Smtp server app based on Spring Boot.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@SpringBootApplication
@Slf4j
public class SmtpServerBoot implements CommandLineRunner {
    @Autowired
    private ApplicationContext context;    

    /**
     * Mail server.
     */
    @Autowired
    private SmtpServer smtpServer;

    /**
     * Run app.
     *
     * @param args Arguments from main.
     */
    @Override
    public void run(final String... args) {
        // Mail loop
        smtpServer.run();
        // Clean shutdown.
        SpringApplication.exit(context);
    }    
    
    /**
     * Main program.
     *
     * @param args Arguments passed on command line.
     */
    public static void main(String[] args) {
        // Let Spring instantiate and inject dependencies
        SpringApplication.run(SmtpServerBoot.class, args);
    }
}
