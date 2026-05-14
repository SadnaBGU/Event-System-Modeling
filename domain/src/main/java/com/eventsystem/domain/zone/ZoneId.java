package com.eventsystem.domain.zone;

import java.util.Objects;
import java.util.UUID;

public record ZoneId(String value) {


    public ZoneId {
        Objects.requireNonNull(value, "value must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
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
