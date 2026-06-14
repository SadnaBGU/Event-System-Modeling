package com.eventsystem.domain.venue;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class VenueId implements Serializable {

    @Column(name = "id")
    private UUID value;

    protected VenueId() {}

    public VenueId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("VenueId value cannot be null");
        }
        this.value = value;
    }

    public static VenueId generate() {
        return new VenueId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VenueId venueId = (VenueId) o;
        return Objects.equals(value, venueId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}