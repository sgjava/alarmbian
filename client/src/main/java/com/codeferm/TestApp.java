package com.codeferm;

import com.codeferm.alarmbian.entity.Event;
import de.milchreis.uibooster.UiBooster;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/**
 *
 * @author servadmin
 */
@SpringBootApplication
@Slf4j
public class TestApp implements CommandLineRunner {

    @Autowired
    private ApplicationContext context;
    /**
     * Spring environment.
     */
    @Autowired
    private Environment env;    
    /**
     * Play logic.
     */
    @Autowired
    private Play play;    

    /**
     * Run app.
     *
     * @param args Arguments from main.
     */
    @Override
    public void run(final String... args) {
        new UiBooster().showInfoDialog(play.getDeviceName());
        var events = play.findMotionEvents();
        for (Event event : events) {
            log.debug(event.toString());
        }
        // Clean shutdown.
        SpringApplication.exit(context);
    }

    /**
     * Main.
     *
     * @param args Arguments from from command line.
     */
    public static void main(final String[] args) {
        // Load the OpenCV system library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        SpringApplication.run(TestApp.class, args);
    }
}
