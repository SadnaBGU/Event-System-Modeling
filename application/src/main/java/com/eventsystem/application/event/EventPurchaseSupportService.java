package com.eventsystem.application.event;

import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.EventRepository;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.ZoneRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;



@Service
public class EventPurchaseSupportService implements EventQueryPort{

    private final EventRepository eventRepository;
    private final ZoneRepository zoneRepository;
    private static final Logger logger = LoggerFactory.getLogger(EventPurchaseSupportService.class);


    public EventPurchaseSupportService( EventRepository eventRepository, ZoneRepository zoneRepository) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository must not be null");
        this.zoneRepository = Objects.requireNonNull(zoneRepository, "zoneRepository must not be null" );
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

    @Override
    public boolean validatePurchasePolicy(String eventId, BuyerReference buyer, List<OrderItem> items) { //TODO - palceholder until purchase policy is implemented
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(buyer, "buyer must not be null");
        Objects.requireNonNull(items, "items must not be null");

        if (items.isEmpty()) {
            return false;
        }

        Event event = loadEvent(new EventId(eventId));

        if (!event.isPurchasable()) {
            return false;
        }

        for (OrderItem item : items) {
            if (item == null) {
                return false;
            }

            if (item.getQuantity() <= 0) {
                return false;
            }

            if (item.getUnitPrice() == null) {
                return false;
            }

            ZoneId zoneId = new ZoneId(item.getZoneId());

            if (!event.isZoneInEvent(zoneId)) {
                return false;
            }
        }


        return true;
    }

    @Override
    public DiscountSnapshot applyDiscount(String eventId, String discountCode, Money baseTotal) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(baseTotal, "baseTotal must not be null");

        Event event = loadEvent(new EventId(eventId));
        if (event.isValidDiscountCode(discountCode))
        {
            DiscountSnapshot snapshot = event.getDiscountSnapshot(discountCode, baseTotal);
            logger.info("Discount applied. eventId={}, discountCode={}, discountAmount={}",
                eventId, discountCode, snapshot.discountAmount());
            return snapshot;
        }
        logger.info("NO Discount applied. eventId={}, discountCode={}", eventId, discountCode);
        return new DiscountSnapshot("NO_DISCOUNT", Money.of(BigDecimal.ZERO, baseTotal.currency())
        );
    }

    @Override
    public EventSnapshot getEventSnapshot(String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Event event = loadEvent(new EventId(eventId));

        return new EventSnapshot( event.id().value(), event.details().name(), event.companyId(),
                            event.details().dates().get(0).toLocalDate(), event.details().location());
    }

    private Event loadEvent(EventId eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    logger.warn("Event not found. eventId={}", eventId.value());
                    return new IllegalArgumentException("event not found: " + eventId.value());
                });
    }
}