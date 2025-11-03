package io.github.jdeeplearn.rag.config;

import com.couchbase.client.core.retry.BestEffortRetryStrategy;
import com.couchbase.client.core.retry.FailFastRetryStrategy;
import com.couchbase.client.core.retry.RetryStrategy;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.env.ClusterEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Couchbase environment configuration using the 3.9+ Consumer-based builder API.
 * TLS is optional and disabled by default for local use.
 */
@Configuration
public class CouchbaseConfig {

    private static final Logger log = LogManager.getLogger(CouchbaseConfig.class);

    @Bean
    public ClusterEnvironment clusterEnvironment(
            @Value("${spring.couchbase.env.timeouts.connect:20s}") Duration connectTimeout,
            @Value("${spring.couchbase.env.timeouts.key-value:5s}") Duration kvTimeout,
            @Value("${spring.couchbase.env.timeouts.query:30s}") Duration queryTimeout,
            @Value("${cb.tls.enabled:false}") boolean tlsEnabled,
            @Value("${cb.tls.trust-cert-path:}") String trustCertPath,
            @Value("${cb.retry.best-effort:true}") boolean bestEffortRetry,
            @Value("${cb.compression.enabled:true}") boolean compressionEnabled
    ) {

        // ---- Choose retry strategy (Best-Effort vs Fail-Fast) ----
        final RetryStrategy retry = bestEffortRetry
                ? BestEffortRetryStrategy.INSTANCE
                : FailFastRetryStrategy.INSTANCE;

        // ---- Build environment with Consumer-based sub-builders (recommended in SDK 3.9) ----
        ClusterEnvironment env = ClusterEnvironment.builder()
                // TLS / Security (Consumer overload, not deprecated)
                .securityConfig(sc -> {
                    sc.enableTls(tlsEnabled);
                    if (tlsEnabled) {
                        if (trustCertPath != null && !trustCertPath.isBlank()) {
                            Path caPath = Path.of(trustCertPath);
                            if (!Files.exists(caPath)) {
                                throw new IllegalStateException("TLS trust certificate not found: " + trustCertPath);
                            }
                            sc.trustCertificate(caPath);
                            log.info("TLS enabled with trust certificate: {}", trustCertPath);
                        } else {
                            log.info("TLS enabled using default JVM trust store.");
                        }
                    } else {
                        log.info("TLS explicitly disabled for local development (using couchbase:// scheme).");
                    }
                })
                // Compression
                .compressionConfig(cc -> cc.enable(compressionEnabled))
                // Timeouts
                .timeoutConfig(tc -> tc
                        .connectTimeout(connectTimeout)
                        .kvTimeout(kvTimeout)
                        .queryTimeout(queryTimeout))
                // I/O tuning
                .ioConfig(io -> io.numKvConnections(2))
                // Retry strategy
                .retryStrategy(retry)
                .build();

        log.info("ClusterEnvironment initialized (TLS={}, Retry={}, Compression={}, ConnectTimeout={})",
                tlsEnabled, retry.getClass().getSimpleName(), compressionEnabled, connectTimeout);
        return env;
    }

    @Bean
    public Bucket faqBucket(Cluster cluster,
                            @Value("${spring.data.couchbase.bucket-name}") String bucketName) {
        Bucket bucket = cluster.bucket(bucketName);
        bucket.waitUntilReady(java.time.Duration.ofSeconds(20));
        return bucket;
    }
}
