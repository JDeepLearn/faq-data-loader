package io.github.jdeeplearn.rag.service;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest extends CouchbaseContainerConfig {

    @BeforeAll
    static void banner() {
        System.out.println(">>> Couchbase Testcontainer ready at: " + COUCHBASE.getConnectionString());
    }
}
