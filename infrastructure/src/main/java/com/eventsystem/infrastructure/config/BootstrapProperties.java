package com.eventsystem.infrastructure.config;

import java.time.Duration;
import java.time.LocalDate;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Seed data read once at startup by {@link AdminBootstrap}.
 *
 * <ul>
 *   <li>{@link Admin} — credentials for the initial system administrator
 *       created when no {@link com.eventsystem.domain.platform.Platform}
 *       aggregate yet exists.</li>
 *   <li>{@code defaultReservationTimeout} / {@code queueLoadThreshold} —
 *       initial Platform tunables (admins can change them later via
 *       {@code AdminService}).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "eventsystem.bootstrap")
@Validated
public record BootstrapProperties(
        @Valid Admin admin,
        Duration defaultReservationTimeout,
        int queueLoadThreshold) {

    public record Admin(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String email,
            @NotNull LocalDate dateOfBirth) {}

        
}

