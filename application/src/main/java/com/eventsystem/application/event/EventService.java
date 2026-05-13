package com.eventsystem.application.event;

import com.eventsystem.domain.event.*;
import com.eventsystem.domain.zone.ZoneId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EventService {

    private static final Logger logger = LoggerFactory.getLogger(EventService.class);
    private final EventRepository eventRepository;
    private final EventPermissionChecker permissionChecker;

    public EventService(EventRepository eventRepository, EventPermissionChecker permissionChecker ) {
        this.eventRepository = Objects.requireNonNull( eventRepository, "eventRepository must not be null");
        this.permissionChecker = Objects.requireNonNull(permissionChecker,"permissionChecker must not be null");
    }

    public EventId createDraft(String actorId, String companyId, EventDetails details, VenueMap venueMap) {
        requireValidActor(actorId);
        Objects.requireNonNull(details, "details must not be null");
        Objects.requireNonNull(venueMap, "venueMap must not be null");

        requireManageEventsPermission(actorId, companyId);

        Event event = Event.createDraft(companyId, details, venueMap);
        eventRepository.save(event);
        logger.info("Draft event created. eventId={}, companyId={}, actorId={}",
            event.id().value(), companyId, actorId);
        return event.id();
    }

    public void updateDetails( String actorId, EventId eventId, EventDetails newDetails ) {
        requireValidActor(actorId);
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(newDetails, "newDetails must not be null");
        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.updateDetails(newDetails);
        eventRepository.save(event);
        logger.info("Event details updated. eventId={}, companyId={}, actorId={}",
            eventId.value(), event.companyId(), actorId);
    }

    public void updateVenueMap( String actorId, EventId eventId, VenueMap newVenueMap ) {
        requireValidActor(actorId);
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(newVenueMap, "newVenueMap must not be null");
        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.updateVenueMap(newVenueMap);
        eventRepository.save(event);
        logger.info("Event Venue Map updated. eventId={}, companyId={}, actorId={}",
            eventId.value(), event.companyId(), actorId);
    }

    public void addZone(String actorId, EventId eventId, ZoneId zoneId ) {
        requireValidActor(actorId);
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.addZone(zoneId);
        eventRepository.save(event);
        logger.info("Zone added to event. eventId={}, zoneId={}, companyId={}, actorId={}",
            eventId.value(), zoneId.value(), event.companyId(), actorId);
    }

    public void removeZone(String actorId, EventId eventId, ZoneId zoneId ) {
        requireValidActor(actorId);
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.removeZone(zoneId);
        eventRepository.save(event);
        logger.info("Zone removed from event. eventId={}, zoneId={}, companyId={}, actorId={}",
            eventId.value(), zoneId.value(), event.companyId(), actorId);
    }

    /*TODO - Add when Policy and SalesMethod Support are added:
    public void setPurchasePolicy(String actorId, EventId eventId, PurchasePolicy policy) {
        
    }

    public void setDiscountPolicy(String actorId, EventId eventId, DiscountPolicy policy) {
        
    }

    */

    public void publish(String actorId, EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        requireValidActor(actorId);
        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.publish();
        eventRepository.save(event);
        logger.info("Event published. eventId={}, companyId={}, actorId={}",
            eventId.value(), event.companyId(), actorId);
    }

    public void eventOver(String actorId, EventId eventId) { //TODO - ensure OVER is required
        requireValidActor(actorId);
        Objects.requireNonNull(eventId, "eventId must not be null");
        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.over();
        eventRepository.save(event);
        logger.info("Event marked as over. eventId={}, companyId={}, actorId={}",
            eventId.value(), event.companyId(), actorId);
    }

    public void cancel(String actorId, EventId eventId) {
        requireValidActor(actorId);
        Objects.requireNonNull(eventId, "eventId must not be null");
        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.cancel();
        eventRepository.save(event);
        logger.info("Event cancelled. eventId={}, companyId={}, actorId={}",
            eventId.value(), event.companyId(), actorId);
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

    public void setSalesMethod(String actorId, EventId eventId, SalesMethod salesMethod) {
        requireValidActor(actorId);
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(salesMethod, "salesMethod must not be null");

        Event event = loadEvent(eventId);
        requireManageEventsPermission(actorId, event.companyId());

        event.setSalesMethod(salesMethod);
        eventRepository.save(event);
        logger.info("Event sales method updated. eventId={}, salesMethod={}, companyId={}, actorId={}",
            eventId.value(), salesMethod, event.companyId(), actorId);
    }

    public void setMethodRegular(String actorId, EventId eventId) {
        setSalesMethod(actorId, eventId, SalesMethod.REGULAR);
    }

    public void setMethodQueue(String actorId, EventId eventId) {
        setSalesMethod(actorId, eventId, SalesMethod.VIRTUAL_QUEUE);
    }

    public void setMethodLottery(String actorId, EventId eventId) {
        setSalesMethod(actorId, eventId, SalesMethod.LOTTERY);
    }

    private Event loadEvent(EventId eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    logger.warn("Event not found. eventId={}", eventId.value());
                    return new IllegalArgumentException("event not found: " + eventId.value());
                });
    }

    private void requireValidActor(String actorId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        if (actorId.isBlank()) 
        {
            logger.warn("Invalid actor id: blank actorId");
            throw new IllegalArgumentException("actorId must not be blank or null");
        }
    }

    private void requireManageEventsPermission(String actorId, String companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (!permissionChecker.canManageEvents(actorId, companyId)) {
            logger.warn("Permission denied for event management. actorId={}, companyId={}",
                actorId, companyId);
            throw new SecurityException("actor is not allowed to manage events for company: " + companyId);
        }
        
    }

    public void requireZoneBelongsToEvent(EventId eventId, ZoneId zoneId) {
        Event event = loadEvent(eventId);
        if (!event.isZoneInEvent(zoneId)) {
        logger.warn("Zone does not belong to event. eventId={}, zoneId={}",
                eventId.value(), zoneId.value());
    }
        event.requireZoneBelongsToEvent(zoneId);
    }

    public Event getEvent(EventId eventId) {
        return loadEvent(eventId);
    }

}