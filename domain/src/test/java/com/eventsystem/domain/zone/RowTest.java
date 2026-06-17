package com.eventsystem.domain.zone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RowTest {

    @Test
    void constructor_rejectsInvalidArguments() {
        Seat seat = new Seat(SeatId.random(), "A", 1);
        
        assertThatThrownBy(() -> new Row(null, List.of(seat))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Row("  ", List.of(seat))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Row("A", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Row("A", List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getters_returnCorrectValues() {
        Seat seat = new Seat(SeatId.random(), "A", 1);
        Row row = new Row("A", List.of(seat));
        
        assertThat(row.rowLabel()).isEqualTo("A");
        assertThat(row.seats()).containsExactly(seat);
        
        // Ensure immutability
        assertThatThrownBy(() -> row.seats().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findSeat_returnsOptionalCorrectly() {
        SeatId id1 = SeatId.random();
        SeatId id2 = SeatId.random();
        Seat seat1 = new Seat(id1, "A", 1);
        Seat seat2 = new Seat(id2, "A", 2);
        
        Row row = new Row("A", List.of(seat1, seat2));
        
        assertThat(row.findSeat(id1)).isPresent().contains(seat1);
        assertThat(row.findSeat(SeatId.random())).isEmpty();
    }
}