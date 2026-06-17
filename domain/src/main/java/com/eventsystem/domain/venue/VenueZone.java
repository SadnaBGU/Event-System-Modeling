package com.eventsystem.domain.venue;

import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.Seat;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.SeatStatus;
import com.eventsystem.domain.zone.ZoneType;
import com.eventsystem.domain.zone.SeatedZoneHelper;
import com.eventsystem.domain.shared.Money;
import jakarta.persistence.*;

import java.lang.reflect.Field;
import java.util.*;

@Entity
@Table(name = "venue_zone")
public class VenueZone {

    @EmbeddedId
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "zone_id"))
    })
    private ZoneId zoneId;

    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false)
    private ZoneType zoneType;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "price_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "price_currency"))
    })
    private Money pricePerTicket;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "venue_zone_id")
    private List<Seat> seats;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    protected VenueZone() {}

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

        if (zoneType == ZoneType.SEATED) {
            for (int i = 1; i <= totalCapacity; i++) {
                Seat newSeat = new Seat(SeatId.random(), "GEN", i);
                this.seats.add(newSeat);
            }
        }
    }

    public VenueZone(ZoneId zoneId, String zoneName, ZoneType zoneType, Money pricePerTicket, List<Seat> seats) {
        if (zoneId == null || zoneName == null || zoneType == null || pricePerTicket == null) {
            throw new IllegalArgumentException("ZoneId, zoneName, zoneType, and pricePerTicket cannot be null");
        }
        if (zoneName.isBlank()) {
            throw new IllegalArgumentException("Zone name cannot be blank");
        }
        if (seats == null) {
            throw new IllegalArgumentException("Seats cannot be null");
        }

        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.zoneType = zoneType;
        this.pricePerTicket = pricePerTicket;
        this.totalCapacity = seats.size();
        this.seats = new ArrayList<>();
        
        if (zoneType == ZoneType.SEATED) {
            this.seats.addAll(seats);
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
        if (this.zoneType == ZoneType.STANDING) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(new ArrayList<>(seats));
    }

    public int getAvailableCount() {
        if (zoneType == ZoneType.STANDING) {
            return totalCapacity;
        }
        
        return (int) seats.stream()
                .filter(seat -> seat.status() == SeatStatus.AVAILABLE)
                .count();
    }

    public int getReservedCount() {
        if (zoneType == ZoneType.STANDING) {
            return 0;
        }

        return (int) seats.stream()
                .filter(seat -> seat.status() == SeatStatus.RESERVED)
                .count();
    }

    public int getSoldCount() {
        if (zoneType == ZoneType.STANDING) {
            return 0;
        }

        return (int) seats.stream()
                .filter(seat -> seat.status() == SeatStatus.SOLD)
                .count();
    }

    public void reserveSeat(SeatId seatId) {
        SeatedZoneHelper.reserveSeat(this::findSeat, seatId);
    }

    public void releaseSeat(SeatId seatId) {
        SeatedZoneHelper.releaseSeat(this::findSeat, seatId);
    }

    public void markSeatSold(SeatId seatId) {
        SeatedZoneHelper.markSeatSold(this::findSeat, seatId);
    }

    private Seat findSeat(SeatId seatId) {
        return SeatedZoneHelper.findSeatInList(seats, seatId);
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
