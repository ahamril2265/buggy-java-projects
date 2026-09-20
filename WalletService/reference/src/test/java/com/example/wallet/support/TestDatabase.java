package com.example.wallet.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;

/**
 * By default the tests run on in-memory H2. Run them against a real PostgreSQL instead with
 * {@code ./mvnw test -Dtest.db=postgres} (an embedded PostgreSQL is started; no Docker needed).
 */
public final class TestDatabase {

    private static EmbeddedPostgres postgres;

    private TestDatabase() {
    }

    public static boolean usePostgres() {
        return "postgres".equalsIgnoreCase(System.getProperty("test.db"));
    }

    public static synchronized void configure(DynamicPropertyRegistry registry) {
        if (!usePostgres()) {
            return;
        }
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new IllegalStateException("Could not start embedded PostgreSQL", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // best effort on shutdown
                }
            }));
        }
        EmbeddedPostgres db = postgres;
        registry.add("spring.datasource.url", () -> db.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }
}
