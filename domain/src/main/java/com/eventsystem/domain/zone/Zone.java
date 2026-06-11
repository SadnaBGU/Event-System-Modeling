package com.eventsystem.domain.zone;

import com.eventsystem.domain.domainexceptions.ZoneDomainException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.shared.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.persistence.*;

@Entity
@Table(name = "zones")
public class Zone {

    @EmbeddedId
    private ZoneId zoneId;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "event_id", nullable = false))
    })
    private EventId eventId;

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

    @Version
    @Column(name = "version")
    private long version;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "zone_id", nullable = false)
    private List<Seat> seats = new ArrayList<>();      // populated only for SEATED zones

    @Transient
    private List<Row> rows = new ArrayList<>();       // populated only for SEATED zones

    @Column(name = "total_capacity")
    private int totalCapacity;

    @Column(name = "available_count")
    private int availableCount;

    protected Zone() {
        this.zoneId = null;
        this.eventId = null;
        this.zoneName = "";
        this.zoneType = null;
        this.pricePerTicket = null;
        this.rows = Collections.emptyList();
        this.totalCapacity = 0;
        this.availableCount = 0;
    }

    private Zone(ZoneId zoneId, EventId eventId, String zoneName, ZoneType zoneType,
                 Money pricePerTicket, List<Row> rows, int totalCapacity) {
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        if (zoneName == null || zoneName.isBlank()) {
            throw new IllegalArgumentException("zoneName must not be blank");
        }
        this.zoneName = zoneName;
        this.zoneType = Objects.requireNonNull(zoneType, "zoneType must not be null");
        this.pricePerTicket = Objects.requireNonNull(pricePerTicket, "pricePerTicket must not be null");
        this.rows = rows != null ? rows : new ArrayList<>();
        this.totalCapacity = totalCapacity;
        this.availableCount = totalCapacity;
        this.version = 0L;

        if (this.rows != null) {
            // For SEATED zones, populate the seats list from the rows
            for (Row row : this.rows) {
                this.seats.addAll(row.seats());
            }
        }
    }

    @PostLoad
    private void reconstructRowsAfterLoad() {
        if (this.zoneType == ZoneType.SEATED && this.seats != null) {
            Map<String, List<Seat>> seatsByRow = this.seats.stream()
                .collect(Collectors.groupingBy(Seat::rowLabel));

            this.rows = new ArrayList<>();
            for (Map.Entry<String, List<Seat>> entry : seatsByRow.entrySet()) {
                this.rows.add(new Row(entry.getKey(), entry.getValue()));
            }
        } else {
            this.rows = new ArrayList<>();
        }
    }

    public static Zone createSeated(ZoneId zoneId, EventId eventId, String zoneName,
                                    Money pricePerTicket, List<Row> rows) {
        Objects.requireNonNull(rows, "rows must not be null");
        if (rows.isEmpty()) {
            throw new ZoneDomainException("seated zone must have at least one row");
        }
        int capacity = rows.stream().mapToInt(r -> r.seats().size()).sum();
        return new Zone(zoneId, eventId, zoneName, ZoneType.SEATED,
                pricePerTicket, new ArrayList<>(rows), capacity);
    }

    public static Zone createStanding(ZoneId zoneId, EventId eventId, String zoneName,
                                      Money pricePerTicket, int capacity) {
        if (capacity < 1) {
            throw new ZoneDomainException("standing zone capacity must be at least 1");
        }
        return new Zone(zoneId, eventId, zoneName, ZoneType.STANDING,
                pricePerTicket, Collections.emptyList(), capacity);
    }

    // ── Seated operations ────────────────────────────────────────────────────

    public void reserveSeat(SeatId seatId) {
        requireSeated();
        SeatedZoneHelper.reserveSeat(this::findSeat, seatId);
        availableCount--;
    }

    public void releaseSeat(SeatId seatId) {
        requireSeated();
        SeatedZoneHelper.releaseSeat(this::findSeat, seatId);
        availableCount++;
    }

    public void markSold(SeatId seatId) {
        requireSeated();
        SeatedZoneHelper.markSeatSold(this::findSeat, seatId);
    }

    // ── Standing operations ──────────────────────────────────────────────────

    public void reserveStanding(int quantity) {
        requireStanding();
        if (quantity < 1) {
            throw new ZoneDomainException("quantity must be at least 1");
        }
        if (quantity > availableCount) {
            throw new ZoneDomainException(
                    "not enough capacity: requested " + quantity + ", available " + availableCount);
        }
        availableCount -= quantity;
    }

    public void releaseStanding(int quantity) {
        requireStanding();
        if (quantity < 1) {
            throw new ZoneDomainException("quantity must be at least 1");
        }
        if (availableCount + quantity > totalCapacity) {
            throw new ZoneDomainException(
                    "cannot release more spots than totalCapacity (would exceed by "
                    + (availableCount + quantity - totalCapacity) + ")");
        }
        availableCount += quantity;
    }

    // availableCount was already decremented on reserve; this records the completed sale
    public void markSoldStanding(int quantity) {
        requireStanding();
        if (quantity < 1) {
            throw new ZoneDomainException("quantity must be at least 1");
        }
    }

    // ── Zone-level updates ───────────────────────────────────────────────────

    public void updateName(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("zoneName must not be blank");
        }
        this.zoneName = newName;
    }

    public void updatePrice(Money newPrice) {
        this.pricePerTicket = Objects.requireNonNull(newPrice, "pricePerTicket must not be null");
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public ZoneId zoneId()          { return zoneId; }
    public EventId eventId()        { return eventId; }
    public String zoneName()        { return zoneName; }
    public ZoneType zoneType()      { return zoneType; }
    public Money pricePerTicket()   { return pricePerTicket; }
    public long version()           { return version; }
    public List<Row> rows()         { return Collections.unmodifiableList(rows); }
    public int totalCapacity()      { return totalCapacity; }
    public int getAvailableCount()  { return availableCount; }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void requireSeated() {
        if (zoneType != ZoneType.SEATED) {
            throw new ZoneDomainException("operation is only valid for SEATED zones");
        }
    }

    private void requireStanding() {
        if (zoneType != ZoneType.STANDING) {
            throw new ZoneDomainException("operation is only valid for STANDING zones");
        }
    }

    private Seat findSeat(SeatId seatId) {
        return SeatedZoneHelper.findSeatInList(this.seats, seatId);
    }
}
