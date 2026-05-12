package com.eventsystem.application.event;

import com.eventsystem.domain.event.*;
import com.eventsystem.domain.zone.ZoneId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository must not be null");
    }

    public EventId createDraft(String companyId, EventDetails details, VenueMap venueMap) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(details, "details must not be null");
        Objects.requireNonNull(venueMap, "venueMap must not be null");

        Event event = Event.createDraft(companyId, details, venueMap);
        eventRepository.save(event);

        return event.id();
    }

    public void updateDetails(EventId eventId, EventDetails newDetails) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(newDetails, "newDetails must not be null");

        Event event = loadEvent(eventId);
        event.updateDetails(newDetails);
        eventRepository.save(event);
    }

    public void updateVenueMap(EventId eventId, VenueMap newVenueMap) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(newVenueMap, "newVenueMap must not be null");

        Event event = loadEvent(eventId);
        event.updateVenueMap(newVenueMap);
        eventRepository.save(event);
    }

    public void addZone(EventId eventId, ZoneId zoneId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");

        Event event = loadEvent(eventId);
        event.addZone(zoneId);
        eventRepository.save(event);
    }

    public void removeZone(EventId eventId, ZoneId zoneId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");

        Event event = loadEvent(eventId);
        event.removeZone(zoneId);
        eventRepository.save(event);
    }

    public void publish(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Event event = loadEvent(eventId);
        event.publish();
        eventRepository.save(event);
    }

    public void cancel(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Event event = loadEvent(eventId);
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
}