package com.eventsystem.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Event System.
 *
 * Component scan is rooted at {@code com.eventsystem} so it picks up beans
 * from all three modules (domain, application, infrastructure).
 *
 * V1: no web layer (see application.yml: spring.main.web-application-type=none).
 */
@SpringBootApplication(scanBasePackages = "com.eventsystem")
public class EventSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventSystemApplication.class, args);
    }
}
