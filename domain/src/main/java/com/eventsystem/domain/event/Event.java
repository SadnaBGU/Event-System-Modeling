package com.eventsystem.domain.event;

import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.EventDomainException;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;


import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class Event {
    
    private final EventId eventId;
    private final CompanyId companyId;
    private EventDetails details;
    private VenueMap venueMap;
    private EventStatus status;
    private final Set<ZoneId> zones;

    private SalesMethod salesMethod;


     public Event( EventId id, CompanyId companyId, EventDetails details, VenueMap venueMap) {
        this.eventId= Objects.requireNonNull(id, "event id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "company id must not be null");
        this.details = Objects.requireNonNull(details, "event details must not be null");
        this.venueMap = Objects.requireNonNull(venueMap, "venue map must not be null");
        this.status = EventStatus.DRAFT;
        this.zones = new LinkedHashSet<>();
        this.salesMethod = SalesMethod.REGULAR;
    }

    public Event( EventId id, String companyId, EventDetails details, VenueMap venueMap) {
        this.eventId= Objects.requireNonNull(id, "event id must not be null");
        this.companyId = Objects.requireNonNull(new CompanyId(companyId), "company id must not be null");
        this.details = Objects.requireNonNull(details, "event details must not be null");
        this.venueMap = Objects.requireNonNull(venueMap, "venue map must not be null");
        this.status = EventStatus.DRAFT;
        this.zones = new LinkedHashSet<>();
        this.salesMethod = SalesMethod.REGULAR;
    }

    public Event( String id, String companyId, EventDetails details, VenueMap venueMap) {
        this(new EventId(id), companyId, details, venueMap);
    }

    public static Event createDraft( CompanyId companyId, EventDetails details, VenueMap venueMap) {
        return new Event( EventId.random(), companyId, details, venueMap );
    }

    public EventId id() {
        return eventId;
    }

    public String eventIdString() {
        return eventId.toString();
    }

    public CompanyId companyId() {
        return companyId;
    }

    public synchronized EventDetails details() {
        return details;
    }

    public synchronized VenueMap venueMap() {
        return venueMap;
    }

    public synchronized EventStatus status() {
        return status;
    }

    public synchronized SalesMethod salesMethod() {
        return salesMethod;
    }

    public synchronized Set<ZoneId> zoneIds() {
        return Set.copyOf(zones);
    }

    public synchronized boolean isZoneInEvent(ZoneId zoneId) {
        Objects.requireNonNull(zoneId, "zone id must not be null");
        return zones.contains(zoneId);
    }

    public synchronized boolean isPurchasable() {
        return status == EventStatus.PUBLISHED;
    }

    public void requireZoneBelongsToEvent(ZoneId zoneId) {
        Objects.requireNonNull(zoneId, "zone id must not be null");
        if (!isZoneInEvent(zoneId)) {
            throw new EventDomainException("given zone does not belong to the event");
        }
    }

    public void requirePurchasable() {
        if (!isPurchasable()) {
            throw new EventDomainException("event is not purchasable");
        }
    }

    public synchronized void updateDetails(EventDetails newDetails) {
        requireDraft("Cannot update event details after publish");
        this.details = Objects.requireNonNull(newDetails, "event details must not be null");
    }

    public synchronized void updateVenueMap(VenueMap newVenueMap) {
        requireDraft("Cannot update venue map after publish");
        this.venueMap = Objects.requireNonNull(newVenueMap, "venue map must not be null");
    }

    public synchronized void addZone(ZoneId zoneId) {
        requireDraft("Cannot add zone after event is published");

        Objects.requireNonNull(zoneId, "zone id must not be null");

        boolean added = zones.add(zoneId);

        if (!added) {
            throw new EventDomainException("Zone already belongs to this event");
        }
    }

    public synchronized void removeZone(ZoneId zoneId) {
        requireDraft("Cannot remove zone after event is published");

        boolean removed = zones.remove(zoneId);

        if (!removed) {
            throw new EventDomainException("Zone does not belong to this event");
        }
    }

    public synchronized void publish() {
        if (status != EventStatus.DRAFT) {
            throw new EventDomainException("Only draft events can be published");
        }

        if (zones.isEmpty()) {
            throw new EventDomainException("Cannont publish event with no defined zones");
        }

        status = EventStatus.PUBLISHED;
    }

    public synchronized void cancel() {
        if (status == EventStatus.CANCELLED) {
            return;
        }

        if (status == EventStatus.OVER) { 
            throw new EventDomainException("Cannot cancel an event that ended");
        }

        status = EventStatus.CANCELLED;
    }

    public synchronized void over() {
        if (status == EventStatus.OVER) {
            return;
        }

        if (status == EventStatus.DRAFT) {
            throw new EventDomainException("Draft event cannot be marked as over");
        }

        if (status == EventStatus.CANCELLED) {
            throw new EventDomainException("Cancelled event cannot be marked as over");
        }

        status = EventStatus.OVER;
    }

    public synchronized void markSoldOut() {
        if (status != EventStatus.PUBLISHED) {
            throw new EventDomainException("Only published events can become sold out");
        }

        status = EventStatus.SOLD_OUT;
    }

    public synchronized void setSalesMethod(SalesMethod salesMethod) {
        this.salesMethod = Objects.requireNonNull(salesMethod, "salesMethod must not be null");
    }

    public void setMethodRegular()
    {
        setSalesMethod(SalesMethod.REGULAR);
    }

    public void setMethodQueue()
    {
        setSalesMethod(SalesMethod.VIRTUAL_QUEUE);
    }

    public void setMethodLottery()
    {
        setSalesMethod(SalesMethod.LOTTERY);
    }

    // public synchronized void markNotSoldOut() { //TODO - Check if transition: soldout-> published again is allowwed
    //     if (status != EventStatus.SOLD_OUT) {
    //         throw new EventDomainException("Only sold-out events can return to published state");
    //     }

    //     status = EventStatus.PUBLISHED;
    // }

    public boolean isMethodSale() {
        return salesMethod == SalesMethod.REGULAR;
    }

    public boolean isMethodQueue() {
        return salesMethod == SalesMethod.VIRTUAL_QUEUE;
    }

    public boolean isMethodLottery() {
        return salesMethod == SalesMethod.LOTTERY;
    }

    public synchronized boolean isDraft() {
        return status == EventStatus.DRAFT;
    }

    public synchronized boolean isPublished() {
        return status == EventStatus.PUBLISHED;
    }

    public synchronized boolean isSoldOut() {
    return status == EventStatus.SOLD_OUT;
    }

    public synchronized boolean isOver() {
        return status == EventStatus.OVER;
    }

    public synchronized boolean isCancelled() {
        return status == EventStatus.CANCELLED;
    }

    private void requireDraft(String message) {
        if (status != EventStatus.DRAFT) {
            throw new EventDomainException(message);
        }
    }

}

