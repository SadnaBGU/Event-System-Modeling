package com.eventsystem.application.member;

import java.time.LocalDate;

/** Input DTO for updating a member's personal details (II.3.4). */
public record UpdateMemberDetailsRequest(
        String firstName,
        String lastName,
        String email,
        LocalDate dateOfBirth) {
}
