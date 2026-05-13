package com.eventsystem.domain.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.eventsystem.domain.domainexceptions.EventDomainException;

public final class VenueMap {

    private final List<MapElement> mapElements;

    public VenueMap(List<MapElement> mapElements) {
        Objects.requireNonNull(mapElements, "map elements must not be null");

        this.mapElements = List.copyOf(mapElements);

        for (MapElement element : this.mapElements) {
            Objects.requireNonNull(element, "map element must not be null");
        }
    }

    public static VenueMap empty() {
        return new VenueMap(List.of());
    }

    public List<MapElement> mapElements() {
        return mapElements;
    }

    public VenueMap addElement(MapElement element) {
        Objects.requireNonNull(element, "map element must not be null");

        List<MapElement> updatedElements = new ArrayList<>(this.mapElements);
        updatedElements.add(element);

        return new VenueMap(updatedElements);
    }

    public VenueMap removeElement(MapElement element) {
        Objects.requireNonNull(element, "map element must not be null");

        List<MapElement> updatedElements = new ArrayList<>(this.mapElements);
        boolean removed = updatedElements.remove(element);

        if (!removed) {
            throw new EventDomainException("map element does not exist in venue map");
        }

        return new VenueMap(updatedElements);
    }
}

// TODO - Verify if MapElement and VenueMap should be entities (as in the ClassDiagram) or Records (So immutable and just hold data)
