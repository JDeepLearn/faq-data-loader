package io.github.jdeeplearn.rag;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Main entry point for the FAQ Data Loader.
 *
 * This application:
 *  - Reads FAQs from a JSON file
 *  - Generates embeddings
 *  - Writes them into Couchbase
 *  - Ensures a vector search index is available
 */
@SpringBootApplication
public class DataLoaderApplication {

    private static final Logger log = LogManager.getLogger(DataLoaderApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(DataLoaderApplication.class, args);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down FAQ Data Loader...");
            ctx.close();
        }));
    }
}
