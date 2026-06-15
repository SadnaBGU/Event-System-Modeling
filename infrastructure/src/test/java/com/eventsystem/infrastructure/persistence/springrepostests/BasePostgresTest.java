package com.eventsystem.infrastructure.persistence.springrepostests;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

public abstract class BasePostgresTest {

    // הקונטיינר הסטטי המשותף לכולם
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("myuser")
            .withPassword("mypassword");

    // מפעיל את הקונטיינר פעם אחת בלבד לפני תחילת כל הטסטים
    static {
        // String skipDocker = System.getenv("SKIP_DOCKER");
        // if (skipDocker == null || !skipDocker.equals("true")) {
            postgres.start();
        // }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }
}
