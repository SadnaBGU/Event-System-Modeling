package com.eventsystem.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's scheduled-task support so background jobs annotated with
 * {@code @Scheduled} (e.g. the abandoned-order {@code RecoverySweeper}) actually fire.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
