package com.eventsystem.domain.company;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public record CompanyDetails(String name, String description, double rating) {

    public CompanyDetails {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (rating < 0.0 || rating > 5.0) {
            throw new IllegalArgumentException("rating must be in range [0, 5]");
        }
    }
}
