package io.github.jdeeplearn.rag.io;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jdeeplearn.rag.data.Faq;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

/**
 * Input adapter: loads FAQs from a local JSON file and validates them.
 * Keeps the source (file, S3, etc.) isolated from orchestration.
 */
@Component
public class FaqFileReader {

    private static final Logger log = LogManager.getLogger(FaqFileReader.class);

    private final ObjectMapper mapper;
    private final Validator validator;
    private final String jsonPath;

    public FaqFileReader(ObjectMapper mapper,
                         Validator validator,
                         @Value("${uploader.faq-json}") String jsonPath) {
        this.mapper = mapper;
        this.validator = validator;
        this.jsonPath = jsonPath;
    }

    public List<Faq> readAll() throws Exception {
        File file = new File(jsonPath);
        if (!file.exists()) {
            throw new IllegalStateException("faq.json not found at: " + file.getAbsolutePath());
        }
        byte[] bytes = Files.readAllBytes(file.toPath());
        List<Faq> faqs = mapper.readValue(bytes, new TypeReference<>() {});
        // Bean Validation per item
        for (Faq f : faqs) {
            Set<ConstraintViolation<Faq>> violations = validator.validate(f);
            if (!violations.isEmpty()) {
                throw new IllegalArgumentException("Validation failed for question='" + f.question() + "': " + violations);
            }
        }
        log.info("Loaded {} FAQ items from {}", faqs.size(), file.getAbsolutePath());
        return faqs;
    }
}