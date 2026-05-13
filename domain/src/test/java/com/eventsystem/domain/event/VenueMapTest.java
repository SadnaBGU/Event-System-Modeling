package com.eventsystem.domain.event;

import com.eventsystem.domain.domainexceptions.EventDomainException;
import com.eventsystem.domain.zone.ZoneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VenueMapTest {

    @Test
    void emptyVenueMapHasNoMapElements() {
        VenueMap venueMap = VenueMap.empty();

        assertThat(venueMap.mapElements()).isEmpty();
    }

    @Test
    void addElementReturnsNewVenueMapWithElement() {
        VenueMap original = VenueMap.empty();

        MapElement stage = new MapElement(
                "STAGE",
                "Main stage",
                10,
                20,
                null
        );

        VenueMap updated = original.addElement(stage);

        assertThat(original.mapElements()).isEmpty();
        assertThat(updated.mapElements()).containsExactly(stage);
    }

    @Test
    void removeElementReturnsNewVenueMapWithoutElement() {
        MapElement stage = new MapElement(
                "STAGE",
                "Main stage",
                10,
                20,
                null
        );

        VenueMap original = VenueMap.empty().addElement(stage);

        VenueMap updated = original.removeElement(stage);

        assertThat(original.mapElements()).containsExactly(stage);
        assertThat(updated.mapElements()).isEmpty();
    }

    @Test
    void cannotRemoveElementThatDoesNotExist() {
        VenueMap venueMap = VenueMap.empty();

        MapElement stage = new MapElement(
                "STAGE",
                "Main stage",
                10,
                20,
                null
        );

        assertThatThrownBy(() -> venueMap.removeElement(stage))
                .isInstanceOf(EventDomainException.class);
    }

    @Test
    void cannotAddNullElement() {
        VenueMap venueMap = VenueMap.empty();

        assertThatThrownBy(() -> venueMap.addElement(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void mapElementCanBeLinkedToZone() {
        ZoneId zoneId = ZoneId.random();

        MapElement seats = new MapElement(
                "SEATS",
                "Front seats",
                5,
                8,
                zoneId
        );

        VenueMap venueMap = VenueMap.empty().addElement(seats);

        assertThat(venueMap.mapElements()).containsExactly(seats);
        assertThat(venueMap.mapElements().get(0).linkedZoneId()).isEqualTo(zoneId);
    }
}