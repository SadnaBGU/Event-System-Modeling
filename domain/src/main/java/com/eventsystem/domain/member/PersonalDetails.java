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
        String firstName,
        String lastName,
        String email,
        LocalDate dateOfBirth) {

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
