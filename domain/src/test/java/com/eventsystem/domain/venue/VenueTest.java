package com.eventsystem.domain.venue;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.zone.Seat;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.ZoneType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class VenueTest {

    private Venue venue;
    private CompanyId companyId;

    @BeforeEach
    void setUp() {
        companyId = CompanyId.random();
        venue = new Venue(VenueId.generate(), companyId, "Main Venue");
    }

    @Test
    void addZone_successfully_adds_zone_to_venue() {
        // Given
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);

        // When
        venue.addZone(zone);

        // Then
        assertThat(venue.getZones()).hasSize(1);
        assertThat(venue.getZones().get(0).getZoneName()).isEqualTo("Zone A");
    }

    @Test
    void addZone_throws_exception_for_duplicate_zone_name() {
        // Given
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone1 = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);
        VenueZone zone2 = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 50);

        venue.addZone(zone1);

        // When & Then
        assertThatThrownBy(() -> venue.addZone(zone2))
                .isInstanceOf(VenueException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void removeZone_successfully_removes_zone_from_venue() {
        // Given
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);
        venue.addZone(zone);

        // When
        venue.removeZone(zone.getZoneId());

        // Then
        assertThat(venue.getZones()).isEmpty();
    }

    @Test
    void getZone_returns_correct_zone() {
        // Given
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);
        venue.addZone(zone);

        // When
        VenueZone retrieved = venue.getZone(zone.getZoneId());

        // Then
        assertThat(retrieved.getZoneName()).isEqualTo("Zone A");
        assertThat(retrieved.getTotalCapacity()).isEqualTo(100);
    }

    @Test
    void getTotalCapacity_sums_all_zone_capacities() {
        // Given
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone1 = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);
        VenueZone zone2 = new VenueZone(ZoneId.random(), "Zone B", ZoneType.STANDING, price, 50);
        venue.addZone(zone1);
        venue.addZone(zone2);

        // When
        int totalCapacity = venue.getTotalCapacity();

        // Then
        assertThat(totalCapacity).isEqualTo(150);
    }

    @Test
    void getTotalAvailableSeats_counts_available_seats_in_seated_zone() {
        // Given
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);
        venue.addZone(zone);

        // When
        int availableSeats = venue.getTotalAvailableSeats();

        // Then
        assertThat(availableSeats).isEqualTo(100);
    }

    @Test
    void reserveSeat_updates_seat_status() {
        // Given
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);
        venue.addZone(zone);
        Seat seat = zone.getSeats().get(0);
        SeatId seatId = seat.seatId();

        // When
        zone.reserveSeat(seatId);

        // Then
        assertThat(zone.getReservedCount()).isEqualTo(1);
        assertThat(zone.getAvailableCount()).isEqualTo(99);
    }

    @Test
    void addZone_case_insensitive_duplicate_check() {
        // Given
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone1 = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);
        VenueZone zone2 = new VenueZone(ZoneId.random(), "zone a", ZoneType.SEATED, price, 50);

        venue.addZone(zone1);

        // When & Then
        assertThatThrownBy(() -> venue.addZone(zone2))
                .isInstanceOf(VenueException.class)
                .hasMessageContaining("already exists");
    }
}
