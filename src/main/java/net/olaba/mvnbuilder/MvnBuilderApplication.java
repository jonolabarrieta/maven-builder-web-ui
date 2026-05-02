package net.olaba.mvnbuilder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main entry point for the MvnBuilder application.
 */
@SpringBootApplication
@EnableAsync
public class MvnBuilderApplication {

    /**
     * Main method to start the Spring Boot application.
     * 
     * @param args Command line arguments.
     */
    public static void main(final String[] args) {
        SpringApplication.run(MvnBuilderApplication.class, args);
    }

}
