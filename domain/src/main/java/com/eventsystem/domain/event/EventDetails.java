package com.eventsystem.domain.event;

import java.util.Objects;
import java.time.LocalDateTime;


public record EventDetails(
        String name,
        LocalDateTime dateTime
) {

    public EventDetails {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("event name must not be blank");
        }

        Objects.requireNonNull(dateTime, "event date/time must not be null");
    }
}