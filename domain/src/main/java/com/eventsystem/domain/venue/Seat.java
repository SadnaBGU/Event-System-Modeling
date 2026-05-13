package com.eventsystem.domain.venue;

import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.SeatStatus;
import java.util.Objects;

public class Seat {
    private final SeatId seatId;
    private final String rowLabel;
    private final int seatNumber;
    private SeatStatus status;

    public Seat(SeatId seatId, String rowLabel, int seatNumber, SeatStatus status) {
        if (seatId == null || rowLabel == null || status == null) {
            throw new IllegalArgumentException("SeatId, rowLabel, and status cannot be null");
        }
        if (rowLabel.isBlank()) {
            throw new IllegalArgumentException("Row label cannot be blank");
        }
        if (seatNumber <= 0) {
            throw new IllegalArgumentException("Seat number must be positive");
        }
        this.seatId = seatId;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.status = status;
    }

    public SeatId getSeatId() {
        return seatId;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public void markReserved() {
        if (status != SeatStatus.AVAILABLE) {
            throw new VenueException("Seat " + rowLabel + seatNumber + " is not available for reservation");
        }
        this.status = SeatStatus.RESERVED;
    }

    public void markSold() {
        if (status != SeatStatus.RESERVED && status != SeatStatus.AVAILABLE) {
            throw new VenueException("Seat " + rowLabel + seatNumber + " cannot be marked as sold");
        }
        this.status = SeatStatus.SOLD;
    }

    public void markAvailable() {
        this.status = SeatStatus.AVAILABLE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Seat seat = (Seat) o;
        return seatId.equals(seat.seatId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(seatId);
    }

    @Override
    public String toString() {
        return "Seat{" +
                "seatId=" + seatId +
                ", rowLabel='" + rowLabel + '\'' +
                ", seatNumber=" + seatNumber +
                ", status=" + status +
                '}';
    }
}
