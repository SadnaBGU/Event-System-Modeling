package com.eventsystem.domain.event;

import com.eventsystem.domain.zone.ZoneId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class Event {
    
    private final EventId eventId;
    private final String companyId;
    private EventDetails details;
    private VenueMap venueMap;
    private EventStatus status;
    private final Set<ZoneId> zones;

    // private PurchasePolicy purchasePolicy; //TODO - add later
    // private DiscountPolicy discountPolicy; //TODO - add later
    // private SalesMethod salesMethod;       //TODO - add later

     public Event( EventId id, String companyId, EventDetails details, VenueMap venueMap) {
        this.eventId= Objects.requireNonNull(id, "event id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "company id must not be null");
        this.details = Objects.requireNonNull(details, "event details must not be null");
        this.venueMap = Objects.requireNonNull(venueMap, "venue map must not be null");
        this.status = EventStatus.DRAFT;
        this.zones = new LinkedHashSet<>();
    }

    public Event( String id, String companyId, EventDetails details, VenueMap venueMap) {
        this(new EventId(id), companyId, details, venueMap);
    }

    public static Event createDraft( String companyId, EventDetails details, VenueMap venueMap) {
        return new Event( EventId.random(), companyId, details, venueMap );
    }

    public EventId id() {
        return eventId;
    }

    public String eventIdString() {
        return eventId.toString();
    }

    public String companyId() {
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

    public synchronized Set<ZoneId> zoneIds() {
        return Set.copyOf(zones);
    }

    public synchronized void updateDetails(EventDetails newDetails) {
        requireDraft("Cannot update event details after publish");
        this.details = Objects.requireNonNull(newDetails, "event details must not be null");
    }

    public synchronized void updateVenueMap(VenueMap newVenueMap) {
        requireDraft("Cannot update venue map after publish");
        this.venueMap = Objects.requireNonNull(newVenueMap, "venue map must not be null");
    }

    /* TODO - requires adding POLICIES and sales method
    public synchronized void setPurchasePolicy(PurchasePolicy policy){

    }

    public synchronized void setDiscountPolicy(DiscountPolicy policy){

    }

    public synchronized void setSalesMethod(SalesMethod method){

    }
    */

    public synchronized void addZone(ZoneId zoneId) {
        requireDraft("Cannot add zone after event is published");
        zones.add(Objects.requireNonNull(zoneId, "zone id must not be null"));
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
            throw new EventDomainException("Cannont publish event with no defined zones"); //TODO - verify if this check is required
        }

        status = EventStatus.PUBLISHED;
    }

    public synchronized void cancel() {
        if (status == EventStatus.CANCELLED) {
            return;
        }

        if (status == EventStatus.OVER) { //TODO - Check if EventStatus.OVER is needed
            throw new EventDomainException("Cannot cancel an event that ended");
        }

        status = EventStatus.CANCELLED;
    }

    public synchronized void over() { //TODO - Check if EventStatus.OVER is needed
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

    public synchronized void markNotSoldOut() { //TODO - Check if transition: soldout-> published again is allowwed
        if (status != EventStatus.SOLD_OUT) {
            throw new EventDomainException("Only sold-out events can return to published state");
        }

        status = EventStatus.PUBLISHED;
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

    public synchronized boolean isOver() { //TODO - Check if EventStatus.OVER is needed
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

