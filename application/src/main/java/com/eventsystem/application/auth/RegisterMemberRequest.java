package com.eventsystem.application.auth;

import java.time.LocalDate;

/**
 * Input DTO for member self-registration (use case II.1).
 */
public record RegisterMemberRequest(
        String username,
        String plaintextPassword,
        String firstName,
        String lastName,
        String email,
        LocalDate dateOfBirth) {
}
