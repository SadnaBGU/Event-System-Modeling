package com.eventsystem.application.event;

import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.application.company.ICompanyPermissionServicePort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Objects;
import java.util.Set;



@Service
public class EventPurchaseService implements IEventQueryPort{

    private final IEventRepository eventRepository;
    private final IZoneRepository zoneRepository;
    private final ICompanyPermissionServicePort companyServicePort;
    private static final Logger logger = LoggerFactory.getLogger(EventPurchaseService.class);


    public EventPurchaseService( IEventRepository eventRepository, IZoneRepository zoneRepository, ICompanyPermissionServicePort companyservicePort) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository must not be null");
        this.zoneRepository = Objects.requireNonNull(zoneRepository, "zoneServicePort must not be null" );
        this.companyServicePort = Objects.requireNonNull( companyservicePort, "companyservicePort must not be null");
    }

    public boolean isPurchasable(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        logger.debug("Checking if event is purchasable. eventId={}", eventId.value());
        Event event = loadEvent(eventId);

        return event.isPurchasable();
    }

    public void requirePurchasable(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Event event = loadEvent(eventId);

        event.requirePurchasable();
    }

    public boolean areAllZonesFull(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        logger.debug("Checking whether all zones are full. eventId={}", eventId.value());
        List<Zone> zones = zoneRepository.findByEventId(eventId);

        return !zones.isEmpty()
                && zones.stream().allMatch(zone -> zone.getAvailableCount() == 0);
    }

    public void markSoldOutIfAllZonesFull(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Event event = loadEvent(eventId);

        if (!event.isPublished()) {
            logger.warn("Skipping sold-out update because event is not published. eventId={}", eventId.value());
            return;
            
        }

        List<Zone> zones = zoneRepository.findByEventId(eventId);

        boolean allZonesFull = !zones.isEmpty()
                && zones.stream().allMatch(zone -> zone.getAvailableCount() == 0);

        if (allZonesFull) {
            event.markSoldOut();
            eventRepository.save(event);
            logger.info("Event marked as sold out. eventId={}", eventId.value());
        }
    }

    public Set<ZoneId> getZonesOfEvent(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Event event = loadEvent(eventId);
        return event.zoneIds();
    }


    public Event getEventForSnapshot(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        return loadEvent(eventId);
    }

    public void requireZoneBelongsToEvent(EventId eventId, ZoneId zoneId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");

        Event event = loadEvent(eventId);
        if (!event.isZoneInEvent(zoneId))
        {
            logger.warn("Zone does not belong to event. eventId={}, zoneId={}",eventId.value(), zoneId.value());
        }
        event.requireZoneBelongsToEvent(zoneId);
    }

    public PolicyValidationResult validateContextAgainstEvent(EventId eventId, PurchaseContext context) {
        Event event = loadEvent(eventId);

        if (!event.isPurchasable()) {
            return PolicyValidationResult.failure("Event tickets are not purchasable");
        }

        for (ZoneId zoneId : context.zonesOfEachEventTicket()) {
            if (!event.isZoneInEvent(zoneId)) {
                return PolicyValidationResult.failure("Tickets for an invalid zone for event in ordered items");
            }
        }

        return PolicyValidationResult.success();
    }

    @Override
    public EventSnapshot getEventSnapshot(String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Event event = loadEvent(new EventId(eventId));
        return new EventSnapshot( event.id().value(), event.details().name(), companyServicePort.getCompanyName(event.companyId()),
                            event.details().dates().get(0).toLocalDate(), event.details().location());
    }

    private Event loadEvent(EventId eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    logger.warn("Event not found. eventId={}", eventId.value());
                    return new IllegalArgumentException("event not found: " + eventId.value());
                });
    }

    @Override
    public CompanyId companyOfEvent(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        return loadEvent(eventId).companyId();
    }


}