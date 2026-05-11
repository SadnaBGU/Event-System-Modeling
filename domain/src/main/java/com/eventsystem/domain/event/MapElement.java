package com.eventsystem.domain.event;

public record MapElement(
        String name,
        String description
) {

    public MapElement {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("map element name must not be blank");
        }

        if (description == null) {
            description = "";
        }
    }
}

// TODO - Verify if MapElement and VenueMap should be entities (as in the ClassDiagram) or Records (So immutable and just hold data)