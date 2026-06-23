package com.eventsystem.infrastructure.persistence.springrepostests;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class BasePostgresTest {

        @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // הנה השינוי - הפורט הוא 5434
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:5434/eventsdb");
        registry.add("spring.datasource.username", () -> "admin");
        registry.add("spring.datasource.password", () -> "admin123");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Keep the total connection count low: the test suite spins up many cached
        // Spring contexts (one per @DataJpaTest config). With Hikari's default
        // minimum-idle == maximum-pool-size, every cached context would hold 10
        // connections forever and exhaust PostgreSQL's max_connections (=100),
        // causing "too many clients already". A tiny pool that releases idle
        // connections keeps us well under the limit.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
        registry.add("spring.datasource.hikari.idle-timeout", () -> "10000");
    }
}
