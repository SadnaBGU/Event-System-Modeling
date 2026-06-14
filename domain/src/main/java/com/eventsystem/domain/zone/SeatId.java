package com.eventsystem.domain.zone;

import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Embeddable;

@Embeddable
public record SeatId(String value) {

    public SeatId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static SeatId random() {
        return new SeatId(UUID.randomUUID().toString());
    }
}
