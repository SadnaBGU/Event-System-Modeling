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
    }
}
