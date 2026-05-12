package com.eventsystem.domain.zone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Row {

    private final String rowLabel;
    private final List<Seat> seats;

    public Row(String rowLabel, List<Seat> seats) {
        if (rowLabel == null || rowLabel.isBlank()) {
            throw new IllegalArgumentException("rowLabel must not be blank");
        }
        this.rowLabel = rowLabel;
        Objects.requireNonNull(seats, "seats must not be null");
        if (seats.isEmpty()) {
            throw new IllegalArgumentException("row must have at least one seat");
        }
        this.seats = new ArrayList<>(seats);
    }

    public String rowLabel() {
        return rowLabel;
    }

    public List<Seat> seats() {
        return Collections.unmodifiableList(seats);
    }

    // Package-private: Zone traverses rows to locate seats
    Optional<Seat> findSeat(SeatId seatId) {
        return seats.stream()
                .filter(s -> s.seatId().equals(seatId))
                .findFirst();
    }
}
