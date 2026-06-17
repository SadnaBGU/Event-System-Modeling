package com.eventsystem.domain.venue;

import com.eventsystem.domain.domainexceptions.ZoneDomainException;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.Seat;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.ZoneType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VenueZoneTest {

    private static final Money PRICE = new Money(new BigDecimal("25.00"), "USD");

    @Test
    void constructor_rejects_invalid_arguments() {
        assertThatThrownBy(() -> new VenueZone(null, "A", ZoneType.SEATED, PRICE, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VenueZone(ZoneId.random(), " ", ZoneType.SEATED, PRICE, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VenueZone(ZoneId.random(), "A", null, PRICE, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VenueZone(ZoneId.random(), "A", ZoneType.SEATED, null, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VenueZone(ZoneId.random(), "A", ZoneType.SEATED, PRICE, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listConstructor_rejects_invalid_arguments() {
        List<Seat> validSeats = List.of(new Seat(SeatId.random(), "A", 1));

        assertThatThrownBy(() -> new VenueZone(null, "A", ZoneType.SEATED, PRICE, validSeats))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VenueZone(ZoneId.random(), "", ZoneType.SEATED, PRICE, validSeats))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VenueZone(ZoneId.random(), "A", ZoneType.SEATED, PRICE, (List<Seat>) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getters_return_correct_values() {
        ZoneId id = ZoneId.random();
        VenueZone zone = new VenueZone(id, "VIP", ZoneType.SEATED, PRICE, 5);

        assertThat(zone.getZoneId()).isEqualTo(id);
        assertThat(zone.getZoneName()).isEqualTo("VIP");
        assertThat(zone.getZoneType()).isEqualTo(ZoneType.SEATED);
        assertThat(zone.getPricePerTicket()).isEqualTo(PRICE);
        assertThat(zone.getTotalCapacity()).isEqualTo(5);
    }

    @Test
    void seatedZone_initializesSeats_and_supports_full_seat_lifecycle() {
        VenueZone zone = new VenueZone(ZoneId.random(), "Seated", ZoneType.SEATED, PRICE, 3);
        SeatId seatId = zone.getSeats().get(0).seatId();

        assertThat(zone.getAvailableCount()).isEqualTo(3);
        assertThat(zone.getReservedCount()).isZero();
        assertThat(zone.getSoldCount()).isZero();

        zone.reserveSeat(seatId);
        assertThat(zone.getAvailableCount()).isEqualTo(2);
        assertThat(zone.getReservedCount()).isEqualTo(1);

        zone.releaseSeat(seatId);
        assertThat(zone.getAvailableCount()).isEqualTo(3);
        assertThat(zone.getReservedCount()).isZero();

        zone.reserveSeat(seatId);
        zone.markSeatSold(seatId);
        assertThat(zone.getSoldCount()).isEqualTo(1);
    }

    @Test
    void listConstructor_initializes_with_provided_seats() {
        Seat seat1 = new Seat(SeatId.random(), "A", 1);
        Seat seat2 = new Seat(SeatId.random(), "A", 2);
        List<Seat> seats = List.of(seat1, seat2);

        VenueZone zone = new VenueZone(ZoneId.random(), "Custom", ZoneType.SEATED, PRICE, seats);

        assertThat(zone.getTotalCapacity()).isEqualTo(2);
        assertThat(zone.getSeats()).hasSize(2);
    }

    @Test
    void seatedZone_seatOperations_reject_unknownSeat() {
        VenueZone zone = new VenueZone(ZoneId.random(), "Seated", ZoneType.SEATED, PRICE, 2);

        assertThatThrownBy(() -> zone.reserveSeat(SeatId.random()))
                .isInstanceOf(ZoneDomainException.class)
                .hasMessageContaining("seat not found");
        assertThatThrownBy(() -> zone.releaseSeat(SeatId.random()))
                .isInstanceOf(ZoneDomainException.class)
                .hasMessageContaining("seat not found");
        assertThatThrownBy(() -> zone.markSeatSold(SeatId.random()))
                .isInstanceOf(ZoneDomainException.class)
                .hasMessageContaining("seat not found");
    }

    @Test
    void standingZone_returns_zero_for_seat_counts_and_empty_list() {
        ZoneId id = ZoneId.random();
        // Test both constructors for standing zones
        VenueZone zone1 = new VenueZone(id, "Standing 1", ZoneType.STANDING, PRICE, 50);
        VenueZone zone2 = new VenueZone(id, "Standing 2", ZoneType.STANDING, PRICE, Collections.emptyList());

        assertThat(zone1.getSeats()).isEmpty();
        assertThat(zone1.getReservedCount()).isZero();
        assertThat(zone1.getSoldCount()).isZero();
        
        assertThat(zone2.getSeats()).isEmpty();
        assertThat(zone2.getReservedCount()).isZero();
        assertThat(zone2.getSoldCount()).isZero();
    }

    @Test
    void equals_and_hashCode_and_toString() {
        ZoneId id = ZoneId.random();
        VenueZone z1 = new VenueZone(id, "A", ZoneType.SEATED, PRICE, 10);
        VenueZone z2 = new VenueZone(id, "B", ZoneType.STANDING, PRICE, 20);
        VenueZone z3 = new VenueZone(ZoneId.random(), "C", ZoneType.SEATED, PRICE, 10);

        assertThat(z1).isEqualTo(z2);
        assertThat(z1.hashCode()).isEqualTo(z2.hashCode());
        assertThat(z1).isNotEqualTo(z3);
        assertThat(z1).isNotEqualTo(null);
        assertThat(z1).isNotEqualTo(new Object());
        
        assertThat(z1.toString()).contains("A").contains(id.toString()).contains("25.00");
    }
}