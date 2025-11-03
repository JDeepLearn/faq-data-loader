package io.github.jdeeplearn.rag.data;

import com.couchbase.client.java.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Component
public class FaqDocumentMapper {

    private static final Logger log = LogManager.getLogger(FaqDocumentMapper.class);

    public String generateId(Faq faq) {
        try {
            var sha1 = MessageDigest.getInstance("SHA-1").digest(faq.question().getBytes());
            return "faq-" + HexFormat.of().formatHex(sha1, 0, 4); // 8 hex chars
        } catch (Exception e) {
            log.fatal("Failed to generate deterministic ID", e);
            throw new IllegalStateException("ID generation failed", e);
        }
    }

    public JsonObject toDocument(String id, Faq faq, List<Double> answerVectorOrNull) {
        if (faq.image() != null) validateUrl(faq.image(), "image");
        if (faq.link()  != null) validateUrl(faq.link(),  "link");

        JsonObject doc = JsonObject.create()
                .put("id", id)
                .put("type", "faq")
                .put("question", faq.question())
                .put("answer", faq.answer());

        if (faq.image() != null) doc.put("image", faq.image());
        if (faq.link()  != null) doc.put("link",  faq.link());

        // Add vector if provided
        if (answerVectorOrNull != null && !answerVectorOrNull.isEmpty()) {
            doc.put("question_vector", answerVectorOrNull);
        }

        return doc;
    }

    private void validateUrl(String url, String field) {
        try {
            new URI(url).toURL();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid URL for field '" + field + "': " + url);
        }
    }
}
