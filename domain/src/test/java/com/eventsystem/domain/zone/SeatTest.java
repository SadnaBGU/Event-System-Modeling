package com.eventsystem.domain.zone;

import com.eventsystem.domain.domainexceptions.ZoneDomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatTest {

    @Test
    void constructor_rejectsInvalidArguments() {
        SeatId validId = SeatId.random();
        
        assertThatThrownBy(() -> new Seat(null, "A", 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Seat(validId, null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Seat(validId, "  ", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Seat(validId, "A", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jpaConstructor_createsEmptyInstance() throws Exception {
        java.lang.reflect.Constructor<Seat> c = Seat.class.getDeclaredConstructor();
        c.setAccessible(true);
        Seat seat = c.newInstance();
        
        assertThat(seat.seatId()).isNull();
        assertThat(seat.rowLabel()).isEmpty();
        assertThat(seat.seatNumber()).isZero();
        assertThat(seat.status()).isNull();
    }

    @Test
    void getters_returnCorrectValues() {
        SeatId id = SeatId.random();
        Seat seat = new Seat(id, "B", 5);
        
        assertThat(seat.seatId()).isEqualTo(id);
        assertThat(seat.rowLabel()).isEqualTo("B");
        assertThat(seat.seatNumber()).isEqualTo(5);
        assertThat(seat.status()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void validStateTransitions_workCorrectly() {
        Seat seat = new Seat(SeatId.random(), "A", 1);
        
        seat.reserve();
        assertThat(seat.status()).isEqualTo(SeatStatus.RESERVED);
        
        seat.release();
        assertThat(seat.status()).isEqualTo(SeatStatus.AVAILABLE);
        
        seat.reserve();
        seat.markSold();
        assertThat(seat.status()).isEqualTo(SeatStatus.SOLD);
    }

    @Test
    void invalidStateTransitions_throwZoneDomainException() {
        Seat seat = new Seat(SeatId.random(), "A", 1);
        
        // Cannot release an AVAILABLE seat
        assertThatThrownBy(seat::release).isInstanceOf(ZoneDomainException.class);
        // Cannot mark an AVAILABLE seat as sold
        assertThatThrownBy(seat::markSold).isInstanceOf(ZoneDomainException.class);
        
        seat.reserve();
        // Cannot reserve an already RESERVED seat
        assertThatThrownBy(seat::reserve).isInstanceOf(ZoneDomainException.class);
        
        seat.markSold();
        // Cannot reserve, release, or mark sold a SOLD seat
        assertThatThrownBy(seat::reserve).isInstanceOf(ZoneDomainException.class);
        assertThatThrownBy(seat::release).isInstanceOf(ZoneDomainException.class);
        assertThatThrownBy(seat::markSold).isInstanceOf(ZoneDomainException.class);
    }
}