package io.github.jdeeplearn.rag.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jdeeplearn.rag.service.FaqUploaderService;
import io.github.jdeeplearn.rag.service.FaqUploaderService.FaqInput;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * CLI entry point that loads faq.json and triggers FaqUploaderService.
 */
@Component
public class FaqLoaderCommand implements CommandLineRunner {

    private static final Logger log = LogManager.getLogger(FaqLoaderCommand.class);

    private final FaqUploaderService uploaderService;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${uploader.input-file:classpath:faq.json}")
    private Resource inputFile;

    @Value("${uploader.auto-run:true}")
    private boolean autoRun;

    public FaqLoaderCommand(FaqUploaderService uploaderService) {
        this.uploaderService = uploaderService;
    }

    @Override
    public void run(String... args) {
        if (!autoRun) {
            log.info("Auto-run disabled. Exiting.");
            return;
        }

        try (InputStream is = inputFile.getInputStream()) {
            List<FaqInput> faqs = mapper.readValue(is, new TypeReference<>() {});
            log.info("Loaded {} FAQ entries from {}", faqs.size(), inputFile.getFilename());
            uploaderService.uploadFaqs(faqs);
        } catch (Exception e) {
            log.error("Failed to process FAQ input file '{}': {}", inputFile.getFilename(), e.getMessage(), e);
        }
    }
}
