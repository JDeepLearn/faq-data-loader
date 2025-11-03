package io.github.jdeeplearn.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Enterprise entrypoint.
 * Bootstraps Spring context and ensures structured Log4j2 logging.
 */
@SpringBootApplication
public class AppMain {
    private static final Logger log = LogManager.getLogger(AppMain.class);

    public static void main(String[] args) {
        log.info("Starting Couchbase FAQ Uploader application...");
        SpringApplication.run(AppMain.class, args);
    }
}