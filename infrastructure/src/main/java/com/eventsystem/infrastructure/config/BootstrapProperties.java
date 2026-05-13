package com.eventsystem.infrastructure.config;

import java.time.Duration;
import java.time.LocalDate;

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
public record BootstrapProperties(
        Admin admin,
        Duration defaultReservationTimeout,
        int queueLoadThreshold) {

    public record Admin(
            String username,
            String password,
            String firstName,
            String lastName,
            String email,
            LocalDate dateOfBirth) {}
}

