package com.eventsystem.domain.zone;

import com.eventsystem.domain.domainexceptions.ZoneDomainException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatedZoneHelperTest {

    @Test
    void instantiatesClassForCoverage() {
        // Just for JaCoCo 100% on utility classes
        new SeatedZoneHelper(); 
    }

    @Test
    void delegatesLifecycleCorrectly() {
        Seat seat = new Seat(SeatId.random(), "A", 1);
        Function<SeatId, Seat> finder = id -> seat;

        SeatedZoneHelper.reserveSeat(finder, seat.seatId());
        assertThat(seat.status()).isEqualTo(SeatStatus.RESERVED);

        SeatedZoneHelper.markSeatSold(finder, seat.seatId());
        assertThat(seat.status()).isEqualTo(SeatStatus.SOLD);

        // Reset and test release
        Seat seat2 = new Seat(SeatId.random(), "A", 2);
        Function<SeatId, Seat> finder2 = id -> seat2;
        SeatedZoneHelper.reserveSeat(finder2, seat2.seatId());
        SeatedZoneHelper.releaseSeat(finder2, seat2.seatId());
        assertThat(seat2.status()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void findSeatInList_findsOrThrows() {
        SeatId id = SeatId.random();
        Seat seat = new Seat(id, "A", 1);
        List<Seat> seats = List.of(seat);

        assertThat(SeatedZoneHelper.findSeatInList(seats, id)).isEqualTo(seat);
        
        assertThatThrownBy(() -> SeatedZoneHelper.findSeatInList(seats, SeatId.random()))
                .isInstanceOf(ZoneDomainException.class)
                .hasMessageContaining("seat not found");
    }

    @Test
    void findSeatInRows_findsOrThrows() {
        SeatId id = SeatId.random();
        Seat seat = new Seat(id, "A", 1);
        Row row = new Row("A", List.of(seat));
        List<Row> rows = List.of(row);

        assertThat(SeatedZoneHelper.findSeatInRows(rows, id)).isEqualTo(seat);
        
        assertThatThrownBy(() -> SeatedZoneHelper.findSeatInRows(rows, SeatId.random()))
                .isInstanceOf(ZoneDomainException.class)
                .hasMessageContaining("seat not found");
    }
}