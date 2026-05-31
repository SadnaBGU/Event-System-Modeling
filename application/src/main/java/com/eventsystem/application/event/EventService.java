package com.eventsystem.application.event;

import com.eventsystem.application.company.ICompanyPermissionServicePort;

import com.eventsystem.domain.event.*;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.order.OrderItem;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EventService implements IEventManagementPort {

    private static final Logger logger = LoggerFactory.getLogger(EventService.class);
    private final IEventRepository eventRepository;
    private final ICompanyPermissionServicePort permissionChecker;

    public EventService(IEventRepository eventRepository, ICompanyPermissionServicePort permissionChecker) {
        this.eventRepository = Objects.requireNonNull( eventRepository, "eventRepository must not be null");
        this.permissionChecker = Objects.requireNonNull(permissionChecker,"permissionChecker must not be null");
    }

    public EventId createDraft(MemberId actorId, CompanyId companyId, EventDetails details, VenueMap venueMap) {
        Objects.requireNonNull(details, "details must not be null");
        Objects.requireNonNull(venueMap, "venueMap must not be null");

        requireManageEventPermission(actorId, companyId);

        Event event = Event.createDraft(companyId, details, venueMap);
        eventRepository.save(event);
        logger.info("Draft event created. eventId={}, companyId={}, actorId={}",
            event.id().value(), companyId, actorId);
        return event.id();
    }

    public EventId createDraft(MemberId actorId, CompanyId companyId, EventDetails details) {
        Objects.requireNonNull(details, "details must not be null");

        requireManageEventPermission(actorId, companyId);

        Event event = Event.createDraft(companyId, details, VenueMap.empty());
        eventRepository.save(event);
        logger.info("Draft event created. eventId={}, companyId={}, actorId={}",
            event.id().value(), companyId, actorId);
        return event.id();
    }

    public void updateDetails( MemberId actorId, EventId eventId, EventDetails newDetails ) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(newDetails, "newDetails must not be null");
        Event event = loadEvent(eventId);
        requireManageEventPermission(actorId, event.companyId());

        event.updateDetails(newDetails);
        eventRepository.save(event);
        logger.info("Event details updated. eventId={}, companyId={}, actorId={}",
            eventId.value(), event.companyId(), actorId);
    }

    public void updateVenueMap( MemberId actorId, EventId eventId, VenueMap newVenueMap ) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(newVenueMap, "newVenueMap must not be null");
        Event event = loadEvent(eventId);
        requireManageEventPermission(actorId, event.companyId());

        event.updateVenueMap(newVenueMap);
        eventRepository.save(event);
        logger.info("Event Venue Map updated. eventId={}, companyId={}, actorId={}",
            eventId.value(), event.companyId(), actorId);
    }

    public void addZone(MemberId actorId, EventId eventId, ZoneId zoneId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Event event = loadEvent(eventId);
        requireManageEventPermission(actorId, event.companyId());

        event.addZone(zoneId);
        eventRepository.save(event);
        logger.info("Zone added to event. eventId={}, zoneId={}, companyId={}, actorId={}",
            eventId.value(), zoneId.value(), event.companyId(), actorId);
    }

    public void removeZone(MemberId actorId, EventId eventId, ZoneId zoneId ) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Event event = loadEvent(eventId);
        requireManageEventPermission(actorId, event.companyId());

        event.removeZone(zoneId);
        eventRepository.save(event);
        logger.info("Zone removed from event. eventId={}, zoneId={}, companyId={}, actorId={}",
            eventId.value(), zoneId.value(), event.companyId(), actorId);
    }

    public void publish(MemberId actorId, EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Event event = loadEvent(eventId);
        requireManageEventPermission(actorId, event.companyId());

        event.publish();
        eventRepository.save(event);
        logger.info("Event published. eventId={}, companyId={}, actorId={}",
            eventId.value(), event.companyId(), actorId);
    }

    public void eventOver(MemberId actorId, EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Event event = loadEvent(eventId);
        requireManageEventPermission(actorId, event.companyId());

        event.over();
        eventRepository.save(event);
        logger.info("Event marked as over. eventId={}, companyId={}, actorId={}",
            eventId.value(), event.companyId(), actorId);
    }

    public void cancel(MemberId actorId, EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Event event = loadEvent(eventId);
        requireManageEventPermission(actorId, event.companyId());

        event.cancel();
        eventRepository.save(event);
        logger.info("Event cancelled. eventId={}, companyId={}, actorId={}",
            eventId.value(), event.companyId(), actorId);
    }

    public Event findById(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        return loadEvent(eventId);
    }

    public List<Event> findByCompany(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");

        return eventRepository.findByCompany(companyId);
    }

    public List<Event> findPublishedByCompany(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");

        return eventRepository.findByCompany(companyId)
                .stream()
                .filter(event -> event.status() == EventStatus.PUBLISHED)
                .toList();
    }

    @Override
    public void setSalesMethod(MemberId actorId, EventId eventId, SalesMethod salesMethod) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(salesMethod, "salesMethod must not be null");
        

        Event event = loadEvent(eventId);
        requireManageEventPermission(actorId, event.companyId());

        event.setSalesMethod(salesMethod);
        eventRepository.save(event);
        logger.info("Event sales method updated. eventId={}, salesMethod={}, companyId={}, actorId={}",
            eventId.value(), salesMethod, event.companyId(), actorId.value());
    }

    public void setMethodRegular(MemberId actorId, EventId eventId) {
        setSalesMethod(actorId, eventId, SalesMethod.REGULAR);
    }

    public void setMethodQueue(MemberId actorId, EventId eventId) {
        setSalesMethod(actorId, eventId, SalesMethod.VIRTUAL_QUEUE);
    }

    public void setMethodLottery(MemberId actorId, EventId eventId) {
        setSalesMethod(actorId, eventId, SalesMethod.LOTTERY);
    }

    private Event loadEvent(EventId eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    logger.warn("Event not found. eventId={}", eventId.value());
                    return new IllegalArgumentException("event not found: " + eventId.value());
                });
    }

    private void requireManageEventPermission(MemberId actorId, CompanyId companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");


        if (!permissionChecker.canManageEvents(actorId, companyId)) {
            logger.warn("Permission denied for event management. actorId={}, companyId={}, event={}",
                actorId, companyId);
            throw new SecurityException("actor is not allowed to manage events for company: " + companyId.value());
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

    @Override
    public boolean isEventByCompany(EventId eventId, CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Event event = loadEvent(eventId);
        return event.companyId().value().equals(companyId.value());
    }

    @Override
    public List<EventId> allEventsOfCompany(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return eventRepository.findByCompany(companyId).stream().map(event -> event.id()).toList();
    }

    public List<EventId> allPublishedEvents() {
        return eventRepository.findPublishedEvents().stream().map(event -> event.id()).toList();
    }

    @Override
    public CompanyId companyOfEvent(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        return loadEvent(eventId).companyId();
    }

    @Override
    public List<ZoneId> getZonesOfTicketsForEvent(EventId eventId, List<OrderItem> items) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(items, "items must not be null");

        Event event = loadEvent(eventId);
        List<ZoneId> zonesOfEachTickets = new ArrayList<>();
        for (OrderItem item : items) {
            ZoneId zoneOfItem = new ZoneId(item.getZoneId());
            if (!event.isZoneInEvent(zoneOfItem)) {
                throw new IllegalArgumentException("item zones cannot have zones not in event");
            }
            for (int i = 0; i < item.getQuantity(); i++) {
                zonesOfEachTickets.add(zoneOfItem);
            }
        }
        return zonesOfEachTickets;
    }

    public boolean isZoneInEvent(EventId eventId, ZoneId zoneId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Event event = loadEvent(eventId);
        return event.isZoneInEvent(zoneId);
    }  

}