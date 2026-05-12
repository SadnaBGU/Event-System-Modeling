package com.eventsystem.domain.zone;

import java.util.Objects;
import java.util.UUID;

public record ZoneId(String value) {

    private static final String PREFIX = "zon_"; //TODO- check if necessary, for testing and better organisation

    public ZoneId {
        Objects.requireNonNull(value, "value must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }

        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("event id must start with " + PREFIX);
        }
    }

    public static ZoneId random() {
        return new ZoneId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
