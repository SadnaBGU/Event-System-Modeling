package com.eventsystem.domain.venue;

import com.eventsystem.domain.domainexceptions.ZoneDomainException;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.ZoneType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
    void standingZone_hasNoSeats_and_toString_contains_identity() {
        ZoneId id = ZoneId.random();
        VenueZone zone = new VenueZone(id, "Standing", ZoneType.STANDING, PRICE, 50);

        assertThat(zone.getSeats()).isEmpty();
        assertThat(zone.getAvailableCount()).isZero();
        assertThat(zone.toString()).contains("Standing").contains(id.toString());
    }

    @Test
    void equals_and_hashCode_useZoneId() {
        ZoneId id = ZoneId.random();
        VenueZone z1 = new VenueZone(id, "A", ZoneType.SEATED, PRICE, 10);
        VenueZone z2 = new VenueZone(id, "B", ZoneType.STANDING, PRICE, 20);

        assertThat(z1).isEqualTo(z2);
        assertThat(z1.hashCode()).isEqualTo(z2.hashCode());
    }
}
