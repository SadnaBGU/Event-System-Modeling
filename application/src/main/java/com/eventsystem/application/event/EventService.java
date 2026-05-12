package com.eventsystem.application.event;

import com.eventsystem.domain.event.*;
import com.eventsystem.domain.zone.ZoneId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventPermissionChecker permissionChecker;

    public EventService(EventRepository eventRepository, EventPermissionChecker permissionChecker ) {
        this.eventRepository = Objects.requireNonNull( eventRepository, "eventRepository must not be null");
        this.permissionChecker = Objects.requireNonNull(permissionChecker,"permissionChecker must not be null");
    }

    public EventId createDraft(String actorId, String companyId, EventDetails details, VenueMap venueMap) {
        Objects.requireNonNull(details, "details must not be null");
        Objects.requireNonNull(venueMap, "venueMap must not be null");

        requireManageEventsPermission(actorId, companyId);

        Event event = Event.createDraft(companyId, details, venueMap);
        eventRepository.save(event);

        return event.id();
    }

    public void updateDetails( String actorId, EventId eventId, EventDetails newDetails ) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(newDetails, "newDetails must not be null");
        requireValidActor(actorId);
        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.updateDetails(newDetails);
        eventRepository.save(event);
    }

    public void updateVenueMap( String actorId, EventId eventId, VenueMap newVenueMap ) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(newVenueMap, "newVenueMap must not be null");
        requireValidActor(actorId);
        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.updateVenueMap(newVenueMap);
        eventRepository.save(event);
    }

    public void addZone(String actorId, EventId eventId, ZoneId zoneId ) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        requireValidActor(actorId);
        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.addZone(zoneId);
        eventRepository.save(event);
    }

    public void removeZone(String actorId, EventId eventId, ZoneId zoneId ) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        requireValidActor(actorId);
        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.removeZone(zoneId);
        eventRepository.save(event);
    }

    /*TODO - Add when Policy and SalesMethod Support are added:
    public void setPurchasePolicy(String actorId, EventId eventId, PurchasePolicy policy) {
        
    }

    public void setDiscountPolicy(String actorId, EventId eventId, DiscountPolicy policy) {
        
    }

    public void setSalesMethod(String actorId, EventId eventId, SalesMethod method) {
        
    }
    */
   
    public void publish(String actorId, EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        requireValidActor(actorId);
        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.publish();
        eventRepository.save(event);
    }

    public void eventOver(String actorId, EventId eventId) { //TODO - ensure OVER is required
        Objects.requireNonNull(eventId, "eventId must not be null");
        requireValidActor(actorId);
        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.over();
        eventRepository.save(event);
    }

    public void cancel(String actorId, EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        requireValidActor(actorId);
        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.cancel();
        eventRepository.save(event);
    }

    public Event findById(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        return loadEvent(eventId);
    }

    public List<Event> findByCompany(String companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");

        return eventRepository.findByCompany(companyId);
    }

    public List<Event> findPublishedByCompany(String companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");

        return eventRepository.findByCompany(companyId)
                .stream()
                .filter(event -> event.status() == EventStatus.PUBLISHED)
                .toList();
    }

    private Event loadEvent(EventId eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found: " + eventId.value()));
    }

    private void requireValidActor(String actorId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        if (actorId.isBlank()) 
        {
            throw new IllegalArgumentException("actorId must not be blank or null");
        }
    }

    private void requireManageEventsPermission(String actorId, String companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (!permissionChecker.canManageEvents(actorId, companyId)) {
            throw new SecurityException("actor is not allowed to manage events for company: " + companyId);
        }
    }
}