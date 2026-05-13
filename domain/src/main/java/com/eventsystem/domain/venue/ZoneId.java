package com.eventsystem.domain.venue;

import java.util.UUID;

public record ZoneId(UUID value) {
    public ZoneId {
        if (value == null) {
            throw new IllegalArgumentException("ZoneId value cannot be null");
        }
    }

    public static ZoneId generate() {
        return new ZoneId(UUID.randomUUID());
    }
}
