package com.eventsystem.application.event;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Application service for zone lifecycle, seat reservations, and standing
 * inventory.
 *
 * <p>
 * Zone-to-event linking (adding/removing ZoneId on the Event aggregate) is
 * handled
 * by the Event team's EventService, which owns the Event aggregate boundary.
 */
@Transactional
public class ZoneService implements IZoneServicePort {

    private static final Logger log = LoggerFactory.getLogger(ZoneService.class);

    private final IZoneRepository zoneRepository;
    private final ICompanyPermissionServicePort permissionChecker;
    private final IEventManagementPort eventOwnershipChecker;

    public ZoneService(IZoneRepository zoneRepository, ICompanyPermissionServicePort permissionChecker,
            IEventManagementPort eventOwnershipChecker) {
        this.zoneRepository = Objects.requireNonNull(zoneRepository, "zoneRepository must not be null");
        this.permissionChecker = Objects.requireNonNull(permissionChecker, "permissionChecker must not be null");
        this.eventOwnershipChecker = Objects.requireNonNull(eventOwnershipChecker,
                "eventOwnershipChecker must not be null");
    }

    // ── Zone creation ────────────────────────────────────────────────────────

    public ZoneId createSeatedZone(MemberId actorId, EventId eventId, String zoneName, Money price, List<Row> rows) {

        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneName, "zoneName must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(rows, "rows must not be null");
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("seated zone must have at least one row");
        }

        log.info("createSeatedZone: eventId={}, zoneName={}, rows={}", eventId, zoneName,
                rows == null ? null : rows.size());

        requireZoneEditingPermissions(actorId, eventOwnershipChecker.companyOfEvent(eventId));

        ZoneId zoneId = ZoneId.random();
        Zone zone = Zone.createSeated(zoneId, eventId, zoneName, price, rows);
        zoneRepository.save(zone);
        return zoneId;
    }

    public ZoneId createStandingZone(MemberId actorId, EventId eventId, String zoneName, Money price, int capacity) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneName, "zoneName must not be null");
        Objects.requireNonNull(price, "price must not be null");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        log.info("createStandingZone: eventId={}, zoneName={}, capacity={}", eventId, zoneName, capacity);
        requireZoneEditingPermissions(actorId, eventOwnershipChecker.companyOfEvent(eventId));

        ZoneId zoneId = ZoneId.random();
        Zone zone = Zone.createStanding(zoneId, eventId, zoneName, price, capacity);
        zoneRepository.save(zone);
        return zoneId;
    }

    // ── Zone updates ─────────────────────────────────────────────────────────

    public void updateZoneName(MemberId actorId, ZoneId zoneId, String newName) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(newName, "newName must not be null");

        log.info("updateZoneName: actorId={}, zoneId={}, newName={}", actorId, zoneId, newName);

        zoneRepository.withLock(zoneId, () -> {
            Zone zone = loadZone(zoneId);
            CompanyId companyId = eventOwnershipChecker.companyOfEvent(zone.eventId());
            requireZoneEditingPermissions(actorId, companyId);

            zone.updateName(newName);
            zoneRepository.save(zone);
        });
    }

    public void updateZonePrice(MemberId actorId, ZoneId zoneId, Money newPrice) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(newPrice, "newPrice must not be null");

        log.info("updateZonePrice: actorId={}, zoneId={}, newPrice={}", actorId, zoneId, newPrice);

        zoneRepository.withLock(zoneId, () -> {
            Zone zone = loadZone(zoneId);
            CompanyId companyId = eventOwnershipChecker.companyOfEvent(zone.eventId());
            requireZoneEditingPermissions(actorId, companyId);

            zone.updatePrice(newPrice);
            zoneRepository.save(zone);
        });
    }
    // ── Seated reservation lifecycle ─────────────────────────────────────────

    @Override
    public OrderItem reserveSeat(ZoneId zoneId, SeatId seatId) {
        log.info("reserveSeat: zoneId={}, seatId={}", zoneId, seatId);
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
        final Zone[] zoneHolder = new Zone[1];
        zoneRepository.withLock(zoneId, () -> {
            zoneHolder[0] = loadZone(zoneId);
            zoneHolder[0].reserveSeat(seatId);
            zoneRepository.save(zoneHolder[0]);
        });
        return new OrderItem(zoneId.value(), seatId.value(), 1, zoneHolder[0].pricePerTicket());
    }

    @Override
    public void releaseSeat(ZoneId zoneId, SeatId seatId) {
        log.info("releaseSeat: zoneId={}, seatId={}", zoneId, seatId);
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
        zoneRepository.withLock(zoneId, () -> {
            Zone zone = loadZone(zoneId);
            zone.releaseSeat(seatId);
            zoneRepository.save(zone);
        });
    }

    public void markSeatSold(ZoneId zoneId, SeatId seatId) {
        log.info("markSeatSold: zoneId={}, seatId={}", zoneId, seatId);
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(seatId, "seatId must not be null");
        zoneRepository.withLock(zoneId, () -> {
            Zone zone = loadZone(zoneId);
            zone.markSold(seatId);
            zoneRepository.save(zone);
        });
    }

    // ── Standing inventory lifecycle ─────────────────────────────────────────

    public void reserveStanding(ZoneId zoneId, int quantity) {
        log.info("reserveStanding: zoneId={}, quantity={}", zoneId, quantity);
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        zoneRepository.withLock(zoneId, () -> {
            Zone zone = loadZone(zoneId);
            zone.reserveStanding(quantity);
            zoneRepository.save(zone);
        });
    }

    public void releaseStanding(ZoneId zoneId, int quantity) {
        log.info("releaseStanding: zoneId={}, quantity={}", zoneId, quantity);
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        zoneRepository.withLock(zoneId, () -> {
            Zone zone = loadZone(zoneId);
            zone.releaseStanding(quantity);
            zoneRepository.save(zone);
        });
    }

    public void markStandingSold(ZoneId zoneId, int quantity) {
        log.info("markStandingSold: zoneId={}, quantity={}", zoneId, quantity);
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        zoneRepository.withLock(zoneId, () -> {
            Zone zone = loadZone(zoneId);
            zone.markSoldStanding(quantity);
            zoneRepository.save(zone);
        });
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public Zone findById(ZoneId zoneId) {
        log.info("findById: zoneId={}", zoneId);
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        return loadZone(zoneId);
    }

    public List<Zone> findByEvent(EventId eventId) {
        log.info("findByEvent: eventId={}", eventId);
        Objects.requireNonNull(eventId, "eventId must not be null");
        return zoneRepository.findByEventId(eventId);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private Zone loadZone(ZoneId zoneId) {
        return zoneRepository.findById(zoneId)
                .orElseThrow(() -> new NoSuchElementException("Zone not found for zoneId: " + zoneId));
    }

    // helpers:

    private void requireZoneEditingPermissions(MemberId actorId, CompanyId companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (!permissionChecker.canConfigureVenue(actorId, companyId)
                && !permissionChecker.canManageEvents(actorId, companyId)) {
            throw new SecurityException(
                    "actor is not allowed to manage zones for company: " + companyId);
        }
    }

}
