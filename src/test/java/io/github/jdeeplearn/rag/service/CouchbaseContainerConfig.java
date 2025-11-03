package io.github.jdeeplearn.rag.service;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.manager.collection.CollectionManager;
import com.couchbase.client.java.manager.collection.CollectionSpec;
import com.couchbase.client.java.manager.collection.ScopeSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;

import java.nio.file.Path;
import java.time.Duration;

/**
 * CouchbaseContainer configuration for integration tests.
 * - Starts Couchbase 8.0 in Docker
 * - Creates bucket, scope, and collection
 * - Waits for full readiness before tests run
 */
public abstract class CouchbaseContainerConfig {

    private static final Logger log = LogManager.getLogger(CouchbaseContainerConfig.class);

    public static final String BUCKET = "faq_bucket";
    public static final String SCOPE = "faq_scope";
    public static final String COLLECTION = "faqs";

    /**
     * Global test container shared across tests
     */
    protected static final CouchbaseContainer COUCHBASE =
            new CouchbaseContainer("couchbase/server:8.0.0")
                    .withBucket(new BucketDefinition(BUCKET).withPrimaryIndex(false))
                    .withStartupTimeout(Duration.ofMinutes(4))
                    .waitingFor(new HostPortWaitStrategy().withStartupTimeout(Duration.ofMinutes(3)));

    static {
        COUCHBASE.start();

        try (Cluster cluster = Cluster.connect(
                COUCHBASE.getConnectionString(),
                COUCHBASE.getUsername(),
                COUCHBASE.getPassword())) {

            cluster.waitUntilReady(Duration.ofSeconds(60));
            Bucket bucket = cluster.bucket(BUCKET);
            bucket.waitUntilReady(Duration.ofSeconds(30));

            CollectionManager collMgr = bucket.collections();


            // Create scope
            boolean scopeExists = collMgr.getAllScopes()
                    .stream()
                    .anyMatch(scope -> scope.name().equals(SCOPE));
            if (!scopeExists) {
                log.info("Creating scope '{}'", SCOPE);
                collMgr.createScope(SCOPE);
                Thread.sleep(1000);
            }

            // Create collection
            boolean collExists = collMgr.getAllScopes().stream()
                    .filter(s -> s.name().equals(SCOPE))
                    .flatMap(s -> s.collections().stream())
                    .anyMatch(c -> c.name().equals(COLLECTION));
            if (!collExists) {
                log.info("Creating collection '{}.{}'", SCOPE, COLLECTION);

                collMgr.createCollection(CollectionSpec.create(COLLECTION, SCOPE));
                Thread.sleep(2000);
            }


            // Wait for metadata to propagate
            cluster.waitUntilReady(Duration.ofSeconds(15));
            bucket.waitUntilReady(Duration.ofSeconds(15));

            // Confirm final structure
            for (ScopeSpec s : collMgr.getAllScopes()) {
                log.info("Scope {} -> {}", s.name(),
                        s.collections().stream().map(CollectionSpec::name).toList());
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Couchbase Testcontainer", e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.couchbase.connection-string", COUCHBASE::getConnectionString);
        registry.add("spring.couchbase.username", COUCHBASE::getUsername);
        registry.add("spring.couchbase.password", COUCHBASE::getPassword);
        registry.add("spring.data.couchbase.bucket-name", () -> BUCKET);
        registry.add("spring.data.couchbase.scope-name", () -> SCOPE);
        registry.add("uploader.collection", () -> COLLECTION);
        registry.add("embedding.fail-open", () -> true);

        String jsonPath = Path.of("src/test/resources/sample-faq.json").toAbsolutePath().toString();
        registry.add("uploader.faq-json", () -> jsonPath);
        registry.add("cb.tls.enabled", () -> false);
    }
}
