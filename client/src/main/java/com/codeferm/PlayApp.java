package com.codeferm;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;

/**
 *
 * @author servadmin
 */
@SpringBootApplication
@Slf4j
public class PlayApp implements CommandLineRunner, ExitCodeGenerator {
    
    @Autowired
    private PlayUI playUI;    

    private int exitCode;

    @Override
    public void run(String... args) throws Exception {
        exitCode = new CommandLine(playUI).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        // let Spring instantiate and inject dependencies
        System.exit(SpringApplication.exit(SpringApplication.run(PlayApp.class, args)));
    }
}
