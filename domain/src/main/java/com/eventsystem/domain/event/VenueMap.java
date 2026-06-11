package com.eventsystem.domain.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.eventsystem.domain.domainexceptions.EventDomainException;

public record VenueMap(List<MapElement> mapElements) {

    public VenueMap {
        Objects.requireNonNull(mapElements, "map elements must not be null");

        for (MapElement element : mapElements) {
            Objects.requireNonNull(element, "map element must not be null");
        }
        
        mapElements = List.copyOf(mapElements);
    }

    public static VenueMap empty() {
        return new VenueMap(List.of());
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