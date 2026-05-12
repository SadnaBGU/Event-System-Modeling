package com.eventsystem.domain.event;

import java.util.Objects;
import java.util.UUID;

public record EventId(String value) {

    private static final String PREFIX = "evt_"; //TODO- check if necessary

    public EventId {
        Objects.requireNonNull(value, "value must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }

        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("event id must start with " + PREFIX);
        }
    }

    public static EventId random() {
        return new EventId(PREFIX + UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value;
    }
}