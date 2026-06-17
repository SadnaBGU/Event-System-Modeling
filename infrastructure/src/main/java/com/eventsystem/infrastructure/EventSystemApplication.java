package com.eventsystem.infrastructure;

import com.eventsystem.infrastructure.config.WsepProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * Composition root: builds every adapter and service, then runs the bootstrap
 * that seeds the singleton Platform aggregate and an initial admin Member.
 */
@SpringBootApplication
@EntityScan(basePackages = {"com.eventsystem.domain"})
@EnableConfigurationProperties(WsepProperties.class)
public class EventSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventSystemApplication.class, args);
    }
}


