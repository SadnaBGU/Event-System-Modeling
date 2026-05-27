package com.eventsystem.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root: builds every adapter and service, then runs the bootstrap
 * that seeds the singleton Platform aggregate and an initial admin Member.
 */
@SpringBootApplication
public class EventSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventSystemApplication.class, args);
    }
}


