package com.eventsystem.application.event;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.*;

import java.util.List;
import java.util.Objects;

/**
 * Application service for zone lifecycle, seat reservations, and standing inventory.
 *
 * <p>Zone-to-event linking (adding/removing ZoneId on the Event aggregate) is handled
 * by the Event team's EventService, which owns the Event aggregate boundary.
 */
public class ZoneService {

    private final ZoneRepository zoneRepository;

    public ZoneService(ZoneRepository zoneRepository) {
        this.zoneRepository = Objects.requireNonNull(zoneRepository, "zoneRepository must not be null");
    }

    // ── Zone creation ────────────────────────────────────────────────────────

    public ZoneId createSeatedZone(EventId eventId, String zoneName, Money price, List<Row> rows) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneName, "zoneName must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(rows, "rows must not be null");
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("seated zone must have at least one row");
        }

        ZoneId zoneId = ZoneId.random();
        Zone zone = Zone.createSeated(zoneId, eventId, zoneName, price, rows);
        zoneRepository.save(zone);
        return zoneId;
    }

    public ZoneId createStandingZone(EventId eventId, String zoneName, Money price, int capacity) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneName, "zoneName must not be null");
        Objects.requireNonNull(price, "price must not be null");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }

        ZoneId zoneId = ZoneId.random();
        Zone zone = Zone.createStanding(zoneId, eventId, zoneName, price, capacity);
        zoneRepository.save(zone);
        return zoneId;
    }

    // ── Zone updates ─────────────────────────────────────────────────────────

    public void updateZoneName(ZoneId zoneId, String newName) {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Zone zone = loadZone(zoneId);
        zone.updateName(newName);
        zoneRepository.save(zone);
    }

    public void updateZonePrice(ZoneId zoneId, Money newPrice) {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Zone zone = loadZone(zoneId);
        zone.updatePrice(newPrice);
        zoneRepository.save(zone);
    }

    // ── Seated reservation lifecycle ─────────────────────────────────────────

    public void reserveSeat(ZoneId zoneId, SeatId seatId) {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
        Zone zone = loadZone(zoneId);
        zone.reserveSeat(seatId);
        zoneRepository.save(zone);
    }

    public void releaseSeat(ZoneId zoneId, SeatId seatId) {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
        Zone zone = loadZone(zoneId);
        zone.releaseSeat(seatId);
        zoneRepository.save(zone);
    }

    public void markSeatSold(ZoneId zoneId, SeatId seatId) {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
        Zone zone = loadZone(zoneId);
        zone.markSold(seatId);
        zoneRepository.save(zone);
    }

    // ── Standing inventory lifecycle ─────────────────────────────────────────

    public void reserveStanding(ZoneId zoneId, int quantity) {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        Zone zone = loadZone(zoneId);
        zone.reserveStanding(quantity);
        zoneRepository.save(zone);
    }

    public void releaseStanding(ZoneId zoneId, int quantity) {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        Zone zone = loadZone(zoneId);
        zone.releaseStanding(quantity);
        zoneRepository.save(zone);
    }

    public void markStandingSold(ZoneId zoneId, int quantity) {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        Zone zone = loadZone(zoneId);
        zone.markSoldStanding(quantity);
        zoneRepository.save(zone);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public Zone findById(ZoneId zoneId) {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        return loadZone(zoneId);
    }

    public List<Zone> findByEvent(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        return zoneRepository.findByEventId(eventId);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private Zone loadZone(ZoneId zoneId) {
        return zoneRepository.findById(zoneId)
                .orElseThrow(() -> new IllegalArgumentException("zone not found: " + zoneId.value()));
    }
}
