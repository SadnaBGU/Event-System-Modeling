package com.eventsystem.domain.event;

import java.util.List;
import java.util.Objects;

public record VenueMap(List<MapElement> elements) {

    public VenueMap {
        Objects.requireNonNull(elements, "elements must not be null");
        elements = List.copyOf(elements);
    }

    public static VenueMap empty() {
        return new VenueMap(List.of());
    }
}

// TODO - Verify if MapElement and VenueMap should be entities (as in the ClassDiagram) or Records (So immutable and just hold data)
