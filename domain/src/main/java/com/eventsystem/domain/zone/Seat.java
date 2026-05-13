package com.eventsystem.domain.zone;

import java.util.Objects;

import com.eventsystem.domain.domainexceptions.ZoneDomainException;

public class Seat {

    private final SeatId seatId;
    private final String rowLabel;
    private final int seatNumber;
    private SeatStatus status;

    public Seat(SeatId seatId, String rowLabel, int seatNumber) {
        this.seatId = Objects.requireNonNull(seatId, "seatId must not be null");
        if (rowLabel == null || rowLabel.isBlank()) {
            throw new IllegalArgumentException("rowLabel must not be blank");
        }
        this.rowLabel = rowLabel;
        if (seatNumber < 1) {
            throw new IllegalArgumentException("seatNumber must be at least 1");
        }
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }

    public SeatId seatId()    { return seatId; }
    public String rowLabel()  { return rowLabel; }
    public int seatNumber()   { return seatNumber; }
    public SeatStatus status(){ return status; }

    // Package-private: only Zone (same package) drives seat lifecycle
    public void reserve() {
        if (status != SeatStatus.AVAILABLE) {
            throw new ZoneDomainException(
                    "seat " + seatId.value() + " cannot be reserved: current status is " + status);
        }
        status = SeatStatus.RESERVED;
    }

    public void release() {
        if (status != SeatStatus.RESERVED || status == SeatStatus.SOLD) {
            throw new ZoneDomainException(
                    "seat " + seatId.value() + " cannot be released: current status is " + status);
        }
        status = SeatStatus.AVAILABLE;
    }

    public void markSold() {
        if (status != SeatStatus.RESERVED) {
            throw new ZoneDomainException(
                    "seat " + seatId.value() + " cannot be sold: current status is " + status);
        }
        status = SeatStatus.SOLD;
    }
}
