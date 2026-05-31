package com.eventsystem.domain.event;

import com.eventsystem.domain.zone.ZoneId; 

public record MapElement( String elementType, String label, int positionX,
                            int positionY, ZoneId linkedZoneId)
{
    public MapElement {
        if (elementType == null || elementType.isBlank()) {
            throw new IllegalArgumentException("element type must not be blank");
        }

        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("element label must not be blank");
        }

        if (positionX < 0 || positionY < 0) {
            throw new IllegalArgumentException("element position must not be negative");
        }
    }

}
