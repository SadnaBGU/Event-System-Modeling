package com.eventsystem.domain.member;

import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Embeddable;

/**
 * Value Object — a member's personal/contact details.
 * Immutable; replaced wholesale via {@link Member#updateDetails(PersonalDetails)}.
 */

@Embeddable
public record PersonalDetails(
        LocalDate dateOfBirth,
        String email,
        String firstName,
        String lastName) {

    /**
     * Lenient email format check (UAT-38): requires a single "@" with non-empty
     * local and domain parts and no whitespace. Deliberately not stricter (e.g.
     * no required TLD dot) so existing identifiers like "admin@local" stay valid.
     */
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    public PersonalDetails {
        Objects.requireNonNull(firstName, "firstName must not be null");
        Objects.requireNonNull(lastName, "lastName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
        if (firstName.isBlank()) {
            throw new IllegalArgumentException("firstName must not be blank");
        }
        if (lastName.isBlank()) {
            throw new IllegalArgumentException("lastName must not be blank");
        }
        if (email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("email format is invalid");
        }
    }

    // ---------------- GETTERS ----------------

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }


}
