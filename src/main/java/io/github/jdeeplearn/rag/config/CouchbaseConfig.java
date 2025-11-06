package io.github.jdeeplearn.rag.config;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Infrastructure configuration for Couchbase.
 *
 * Responsibilities:
 *  - Connect to Couchbase Cluster
 *  - Expose a Collection bean for faq_bucket/faq_scope/faqs
 */
@Configuration
public class CouchbaseConfig {

    private static final Logger log = LogManager.getLogger(CouchbaseConfig.class);

    @Value("${spring.couchbase.connection-string}")
    private String connectionString;

    @Value("${spring.couchbase.username}")
    private String username;

    @Value("${spring.couchbase.password}")
    private String password;

    @Value("${spring.data.couchbase.bucket-name}")
    private String bucketName;

    @Value("${spring.data.couchbase.scope-name}")
    private String scopeName;

    @Value("${uploader.collection:faqs}")
    private String collectionName;

    @Value("${spring.couchbase.ready-timeout-seconds:10}")
    private int readyTimeoutSeconds;

    @Bean(destroyMethod = "disconnect")
    public Cluster couchbaseCluster() {
        log.info("Connecting to Couchbase at {}", connectionString);
        Cluster cluster = Cluster.connect(connectionString, username, password);
        cluster.waitUntilReady(Duration.ofSeconds(readyTimeoutSeconds));
        return cluster;
    }

    @Bean
    public Collection faqCollection(Cluster cluster) {
        Bucket bucket = cluster.bucket(bucketName);
        bucket.waitUntilReady(Duration.ofSeconds(readyTimeoutSeconds));
        Scope scope = bucket.scope(scopeName);
        Collection collection = scope.collection(collectionName);
        log.info("Using Couchbase collection {}/{}/{}", bucketName, scopeName, collectionName);
        return collection;
    }
}