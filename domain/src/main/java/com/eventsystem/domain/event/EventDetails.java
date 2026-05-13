package com.eventsystem.domain.event;

import java.util.List;
import java.util.Objects;
import java.time.LocalDateTime;


public record EventDetails( String name, List<LocalDateTime> dates, String category,
                            String location, String description ) {
    public EventDetails {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("event name must not be blank");
        }

        Objects.requireNonNull(dates, "event date must not be null");
        if (dates.isEmpty()) {
            throw new IllegalArgumentException("date must be chosen for the event");
        }

        for (LocalDateTime date : dates) {
            Objects.requireNonNull(date, "event date must not be null");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }

        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank"); //TODO- should enforce NON-EMPTY description?
        }

    }
}