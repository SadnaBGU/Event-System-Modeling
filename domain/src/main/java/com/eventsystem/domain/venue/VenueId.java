package com.eventsystem.domain.venue;

import java.util.UUID;

public record VenueId(UUID value) {
    public VenueId {
        if (value == null) {
            throw new IllegalArgumentException("VenueId value cannot be null");
        }
    }

    public static VenueId generate() {
        return new VenueId(UUID.randomUUID());
    }
}
