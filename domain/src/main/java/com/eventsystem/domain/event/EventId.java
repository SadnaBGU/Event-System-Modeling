package com.eventsystem.domain.event;

import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Embeddable;

@Embeddable
public record EventId(String value) {

    public EventId {
        Objects.requireNonNull(value, "value must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }


    }

    public static EventId random() {
        return new EventId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}