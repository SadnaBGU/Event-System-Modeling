package com.eventsystem.domain.venue;

import java.util.UUID;

public record SeatId(UUID value) {
    public SeatId {
        if (value == null) {
            throw new IllegalArgumentException("SeatId value cannot be null");
        }
    }

    public static SeatId generate() {
        return new SeatId(UUID.randomUUID());
    }
}
