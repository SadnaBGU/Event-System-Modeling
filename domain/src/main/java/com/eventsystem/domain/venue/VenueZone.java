package com.eventsystem.domain.venue;

import java.util.*;
import java.util.stream.Collectors;

public class VenueZone {
    private final ZoneId zoneId;
    private final String zoneName;
    private final ZoneType zoneType;
    private final Money pricePerTicket;
    private final List<Seat> seats;
    private final int totalCapacity;

    public VenueZone(ZoneId zoneId, String zoneName, ZoneType zoneType, Money pricePerTicket, int totalCapacity) {
        if (zoneId == null || zoneName == null || zoneType == null || pricePerTicket == null) {
            throw new IllegalArgumentException("ZoneId, zoneName, zoneType, and pricePerTicket cannot be null");
        }
        if (zoneName.isBlank()) {
            throw new IllegalArgumentException("Zone name cannot be blank");
        }
        if (totalCapacity <= 0) {
            throw new IllegalArgumentException("Total capacity must be positive");
        }

        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.zoneType = zoneType;
        this.pricePerTicket = pricePerTicket;
        this.totalCapacity = totalCapacity;
        this.seats = new ArrayList<>();

        // Initialize seats for SEATED zones
        if (zoneType == ZoneType.SEATED) {
            initializeSeatedZone(totalCapacity);
        }
    }

    private void initializeSeatedZone(int capacity) {
        int seatsPerRow = 10;
        int currentSeat = 0;

        for (char row = 'A'; row < 'Z' && currentSeat < capacity; row++) {
            String rowLabel = String.valueOf(row);
            for (int seatNum = 1; seatNum <= seatsPerRow && currentSeat < capacity; seatNum++) {
                seats.add(new Seat(
                        SeatId.generate(),
                        rowLabel,
                        seatNum,
                        SeatStatus.AVAILABLE
                ));
                currentSeat++;
            }
        }
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public ZoneType getZoneType() {
        return zoneType;
    }

    public Money getPricePerTicket() {
        return pricePerTicket;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public List<Seat> getSeats() {
        return Collections.unmodifiableList(seats);
    }

    public int getAvailableCount() {
        return (int) seats.stream()
                .filter(seat -> seat.getStatus() == SeatStatus.AVAILABLE)
                .count();
    }

    public int getReservedCount() {
        return (int) seats.stream()
                .filter(seat -> seat.getStatus() == SeatStatus.RESERVED)
                .count();
    }

    public int getSoldCount() {
        return (int) seats.stream()
                .filter(seat -> seat.getStatus() == SeatStatus.SOLD)
                .count();
    }

    public void reserveSeat(SeatId seatId) {
        Seat seat = findSeat(seatId);
        seat.markReserved();
    }

    public void releaseSeat(SeatId seatId) {
        Seat seat = findSeat(seatId);
        seat.markAvailable();
    }

    public void markSeatSold(SeatId seatId) {
        Seat seat = findSeat(seatId);
        seat.markSold();
    }

    private Seat findSeat(SeatId seatId) {
        return seats.stream()
                .filter(s -> s.getSeatId().equals(seatId))
                .findFirst()
                .orElseThrow(() -> new VenueException("Seat not found: " + seatId));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VenueZone that = (VenueZone) o;
        return zoneId.equals(that.zoneId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(zoneId);
    }

    @Override
    public String toString() {
        return "VenueZone{" +
                "zoneId=" + zoneId +
                ", zoneName='" + zoneName + '\'' +
                ", zoneType=" + zoneType +
                ", pricePerTicket=" + pricePerTicket +
                ", totalCapacity=" + totalCapacity +
                ", availableSeats=" + getAvailableCount() +
                '}';
    }
}
